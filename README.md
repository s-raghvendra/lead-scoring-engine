\# Hybrid Lead Scoring \& Intelligence Engine



An enterprise-grade, asynchronous lead scoring system powered by a hybrid architecture combining deterministic heuristic rules with LLM-based sentiment and intent analysis (Google Gemini API).



\---



\## Architecture Overview



```text

&#x20;      +-----------------------+

&#x20;      |      HTTP Client      |

&#x20;      +-----------+-----------+

&#x20;                  |

&#x20;                  v

&#x20;      +-----------------------+

&#x20;      |   Spring Boot 3 App   | <--- Port 8080

&#x20;      |    (Core Engine)      |

&#x20;      +-----+-----------+-----+

&#x20;            |           |

&#x20;   HTTP/REST|           | JDBC / JPA

&#x20;            v           v

&#x20; +----------------+   +-------------------------------------+

&#x20; | FastAPI Micro- |   |               MySQL 8.0             |

&#x20; | service (LLM)  |   | (Master Leads, Status, Deduplication)|

&#x20; |  (Port 8000)   |   +-------------------------------------+

&#x20; +----------------+   |            PostgreSQL 15            |

&#x20;                      |    (JSONB Scoring Audit Trail)      |

&#x20;                      +-------------------------------------+

```



\* \*\*Backend Service (`backend-service`):\*\* Spring Boot 3 (Java 17), Spring Data JPA, Dual-Datasource configuration (MySQL + PostgreSQL), Flyway database migrations.

\* \*\*AI Intelligence Service (`ai-service`):\*\* FastAPI (Python 3.11), Google Gemini API integration for intent scoring, urgency analysis, and qualification reasoning.

\* \*\*Primary Store (MySQL 8.0):\*\* Manages relational lead records, lifecycle status (`PENDING\_SCORE`, `SCORED`, `DUPLICATE`), and contact metadata.

\* \*\*Audit Store (PostgreSQL 15):\*\* Persists append-only audit rows using native `JSONB` columns (`raw\_ai\_response`, `rule\_breakdown`) for auditability.



\---



\## Key Features



\* \*\*Hybrid Scoring Engine:\*\* Calculates a balanced score (0–100) using 60% LLM intent extraction and 40% deterministic business rules (budget tier, domain reputation, source channel).

\* \*\*Smart Deduplication:\*\* Identifies repeat leads by normalized email; updates lead touchpoint history without overwriting audit logs.

\* \*\*Fault-Tolerant Resilience:\*\* Gracefully falls back to heuristic rule scoring if the external AI service encounters network latency or rate-limiting.

\* \*\*Real-time Analytics:\*\* Aggregates total lead volumes, category distributions (`HOT`, `WARM`, `COLD`), and average conversion readiness per acquisition channel.



\---



\## Quickstart Guide



\### 1. Prerequisites

\* Docker \& Docker Compose

\* Java 17+ (Eclipse Adoptium OpenJDK recommended)

\* Python 3.10+

\* Google Gemini API Key



\### 2. Infrastructure Setup

Spin up MySQL and PostgreSQL containers:

```bash

docker-compose up -d

```



\### 3. AI Service Configuration \& Run

```bash

cd ai-service

cp .env.example .env

\# Add your GEMINI\_API\_KEY in .env



python -m venv .venv

\# Windows:

.venv\\Scripts\\activate

\# Linux/macOS:

source .venv/bin/activate



pip install -r requirements.txt

uvicorn main:app --host 0.0.0.0 --port 8000 --reload

```



\### 4. Backend Service Startup

Open a new terminal:

```bash

cd backend-service

./gradlew bootRun

```

The application will bootstrap schemas via Flyway and initialize Tomcat on `http://localhost:8080`.



\---



\## API Documentation



\### 1. Ingest \& Score a Lead

\* \*\*Endpoint:\*\* `POST /api/v1/leads`

\* \*\*Payload:\*\*

```json

{

&#x20; "name": "Vikram Malhotra",

&#x20; "email": "vikram@packagingdelight.com",

&#x20; "phone": "9811223344",

&#x20; "message": "Looking to purchase 1000 cartons of premium Alphonso mango pulp. Need immediate delivery.",

&#x20; "source": "website",

&#x20; "budget": 250000,

&#x20; "company": "Dessert Delights"

}

```

\* \*\*Sample Response:\*\*

```json

{

&#x20; "id": 1,

&#x20; "name": "Vikram Malhotra",

&#x20; "email": "vikram@packagingdelight.com",

&#x20; "category": "HOT",

&#x20; "latestScore": 100,

&#x20; "status": "SCORED",

&#x20; "isRepeatLead": false

}

```



\### 2. Fetch Lead Analytics

\* \*\*Endpoint:\*\* `GET /api/v1/leads/analytics`

\* \*\*Sample Response:\*\*

```json

{

&#x20; "totalLeads": 1,

&#x20; "categoryBreakdown": { "HOT": 1 },

&#x20; "avgScoreBySource": { "website": 100.0 },

&#x20; "top5HottestLeads": \[...]

}

```



\---



\## Database Audit Verification

Inspect native JSONB persistence in PostgreSQL:

```bash

docker exec -it nector\_postgres psql -U audit\_user -d audit\_db -c "SELECT id, lead\_id, final\_score, raw\_ai\_response FROM score\_audit LIMIT 5;"

```

