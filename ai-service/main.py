"""
Nector Foods — AI Lead Scoring Service
FastAPI application powered by Google Gemini.
"""

import json
import os
import re
from typing import Optional

import google.generativeai as genai
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr, Field, field_validator

# ---------------------------------------------------------------------------
# Environment & Gemini setup
# ---------------------------------------------------------------------------

load_dotenv()

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    raise RuntimeError("GEMINI_API_KEY is not set. Please configure your .env file.")

genai.configure(api_key=GEMINI_API_KEY)
# ---------------------------------------------------------------------------
# Dynamic model resolution — avoids hard-coded model names that may be
# deprecated or renamed across API versions.
# ---------------------------------------------------------------------------

_PREFERRED_MODELS = [
    "gemini-2.0-flash",
    "gemini-1.5-flash-8b",
    "gemini-1.5-flash",
    "gemini-pro",
]

_gemini_model: Optional[genai.GenerativeModel] = None


def _resolve_gemini_model() -> genai.GenerativeModel:
    """Return a cached GenerativeModel, resolving the best available name.

    Strategy:
    1. List all models that support ``generateContent`` via the live API.
    2. Pick the first model whose base name matches our preference list.
    3. If the API listing fails for any reason, fall back to the preferred
       names in order, accepting the first one that constructs without error.
    """
    global _gemini_model
    if _gemini_model is not None:
        return _gemini_model

    # --- attempt live listing ---
    try:
        available = [
            m.name
            for m in genai.list_models()
            if "generateContent" in (m.supported_generation_methods or [])
        ]
        # m.name is like "models/gemini-2.0-flash" — normalise to bare name
        available_bare = {n.split("/")[-1]: n for n in available}

        for preferred in _PREFERRED_MODELS:
            if preferred in available_bare:
                full_name = available_bare[preferred]
                _gemini_model = genai.GenerativeModel(full_name)
                print(f"[Gemini] Using model from live listing: {full_name}")
                return _gemini_model

        # None of our preferred names matched — just take whatever is first
        if available:
            _gemini_model = genai.GenerativeModel(available[0])
            print(f"[Gemini] Preferred models not found; falling back to: {available[0]}")
            return _gemini_model
    except Exception as list_exc:  # noqa: BLE001
        print(f"[Gemini] list_models() failed ({list_exc}); trying static fallbacks.")

    # --- static fallback ---
    for name in _PREFERRED_MODELS:
        try:
            model = genai.GenerativeModel(name)
            _gemini_model = model
            print(f"[Gemini] Using static fallback model: {name}")
            return _gemini_model
        except Exception:  # noqa: BLE001
            continue

    raise RuntimeError(
        "Could not initialise any Gemini model. "
        f"Tried: {_PREFERRED_MODELS}. Check your GEMINI_API_KEY and network access."
    )

# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Nector Foods Lead Scoring API",
    description=(
        "AI-powered lead scoring service that uses Google Gemini to analyse "
        "inbound leads and classify them as HOT, WARM, or COLD."
    ),
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Pydantic schemas
# ---------------------------------------------------------------------------


class Lead(BaseModel):
    """Represents a single inbound lead."""

    name: str = Field(..., min_length=1, max_length=200, description="Full name of the lead")
    email: str = Field(..., description="Email address of the lead")
    phone: str = Field(..., min_length=5, max_length=30, description="Contact phone number")
    message: str = Field(..., min_length=1, description="Lead's enquiry or message")
    source: str = Field(
        ...,
        min_length=1,
        max_length=100,
        description="Channel the lead came from (e.g. website, referral, social)",
    )
    budget: Optional[float] = Field(
        default=None, ge=0, description="Indicative budget in USD (optional)"
    )
    company: Optional[str] = Field(
        default=None, max_length=200, description="Company name (optional)"
    )

    @field_validator("email")
    @classmethod
    def validate_email_format(cls, v: str) -> str:
        pattern = r"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$"
        if not re.match(pattern, v):
            raise ValueError("Invalid email format")
        return v.lower()


class LeadScoreResponse(BaseModel):
    """Scoring result returned for a single lead."""

    ai_score: int = Field(..., ge=0, le=100, description="AI-generated score from 0 to 100")
    category: str = Field(
        ..., description="Lead category: HOT (70-100), WARM (40-69), COLD (0-39)"
    )
    reason: str = Field(..., description="Human-readable explanation of the score")
    raw_response: str = Field(..., description="Verbatim response text from Gemini")


class BatchLeadRequest(BaseModel):
    """Payload for the batch scoring endpoint."""

    leads: list[Lead] = Field(
        ..., min_length=1, max_length=50, description="List of leads (max 50)"
    )


class BatchLeadScoreResponse(BaseModel):
    """Batch scoring result."""

    total: int
    results: list[dict]


# ---------------------------------------------------------------------------
# Gemini scoring helper
# ---------------------------------------------------------------------------

_SYSTEM_PROMPT = """
You are an expert sales analyst for Nector Foods, a premium food products company.
Analyse the provided lead information and return a JSON object (no markdown, no code fences)
with exactly these four keys:

{
  "ai_score": <integer 0-100>,
  "category": "<HOT|WARM|COLD>",
  "reason": "<concise 1-3 sentence explanation>",
  "raw_response": "<your full analysis in 2-4 sentences>"
}

Scoring guidelines:
- HOT  (70-100): Clear purchase intent, sizeable budget, decision-maker, urgent timeline.
- WARM (40-69):  General interest, exploring options, partial budget info, possible future buyer.
- COLD (0-39):   Vague enquiry, no budget signals, likely unqualified or irrelevant contact.

Consider: message sentiment, specificity of the request, budget mentioned, company size signals,
source channel quality, and professional language used.
"""


def _build_lead_prompt(lead: Lead) -> str:
    parts = [
        f"Name:    {lead.name}",
        f"Email:   {lead.email}",
        f"Phone:   {lead.phone}",
        f"Source:  {lead.source}",
        f"Message: {lead.message}",
    ]
    if lead.company:
        parts.append(f"Company: {lead.company}")
    if lead.budget is not None:
        parts.append(f"Budget:  ${lead.budget:,.2f}")
    return "\n".join(parts)


def _parse_gemini_response(text: str) -> dict:
    """Extract the JSON payload from Gemini's response text."""
    # Strip markdown fences if present
    cleaned = re.sub(r"```(?:json)?", "", text).strip()
    # Try direct parse first
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass
    # Fallback: find first JSON object in the text
    match = re.search(r"\{.*\}", cleaned, re.DOTALL)
    if match:
        return json.loads(match.group())
    raise ValueError(f"Could not parse JSON from Gemini response:\n{text}")


async def _score_single_lead(lead: Lead) -> dict:
    """Call Gemini and return the parsed scoring dict."""
    prompt = f"{_SYSTEM_PROMPT}\n\nLead details:\n{_build_lead_prompt(lead)}"
    try:
        response = _resolve_gemini_model().generate_content(prompt)
        raw_text = response.text
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Gemini API error: {exc}",
        )

    try:
        parsed = _parse_gemini_response(raw_text)
    except (ValueError, json.JSONDecodeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Failed to parse Gemini response: {exc}",
        )

    # Validate / normalise
    score = int(parsed.get("ai_score", 0))
    score = max(0, min(100, score))

    raw_category = str(parsed.get("category", "")).upper()
    if raw_category not in {"HOT", "WARM", "COLD"}:
        # Derive from score if Gemini returned something unexpected
        raw_category = "HOT" if score >= 70 else ("WARM" if score >= 40 else "COLD")

    return {
        "ai_score": score,
        "category": raw_category,
        "reason": str(parsed.get("reason", "")),
        "raw_response": str(parsed.get("raw_response", raw_text)),
    }


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@app.get("/health", tags=["Health"])
async def health_check():
    """Simple liveness check."""
    return {"status": "ok", "service": "nector-lead-scoring-ai", "version": "1.0.0"}


@app.post(
    "/api/v1/score-lead",
    response_model=LeadScoreResponse,
    status_code=status.HTTP_200_OK,
    tags=["Lead Scoring"],
    summary="Score a single lead",
)
async def score_lead(lead: Lead) -> LeadScoreResponse:
    """
    Accepts a single lead payload and returns an AI-generated score,
    category (HOT/WARM/COLD), and explanation.
    """
    result = await _score_single_lead(lead)
    return LeadScoreResponse(**result)


@app.post(
    "/api/v1/score-batch",
    response_model=BatchLeadScoreResponse,
    status_code=status.HTTP_200_OK,
    tags=["Lead Scoring"],
    summary="Score a batch of up to 50 leads",
)
async def score_batch(payload: BatchLeadRequest) -> BatchLeadScoreResponse:
    """
    Accepts a list of up to 50 leads and returns scoring results for each.
    Leads are processed sequentially to respect API rate limits.
    """
    results = []
    for idx, lead in enumerate(payload.leads):
        try:
            scored = await _score_single_lead(lead)
            results.append(
                {
                    "index": idx,
                    "email": lead.email,
                    "name": lead.name,
                    **scored,
                    "error": None,
                }
            )
        except HTTPException as exc:
            results.append(
                {
                    "index": idx,
                    "email": lead.email,
                    "name": lead.name,
                    "ai_score": None,
                    "category": None,
                    "reason": None,
                    "raw_response": None,
                    "error": exc.detail,
                }
            )

    return BatchLeadScoreResponse(total=len(results), results=results)
