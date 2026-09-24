-- ============================================================
-- V1__init_schema.sql  — Nector Lead Scoring master DB schema
-- Applied automatically via Flyway on startup.
-- ============================================================

-- ── scoring_config ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS scoring_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    factor_name VARCHAR(100) NOT NULL UNIQUE COMMENT 'Unique rule identifier (e.g. source_referral)',
    weight      INT          NOT NULL DEFAULT 0 COMMENT 'Bonus points added to AI score',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Default scoring weights seed data
INSERT INTO scoring_config (factor_name, weight, is_active) VALUES
    ('source_referral',         15, TRUE),
    ('source_partner',          10, TRUE),
    ('source_website',           5, TRUE),
    ('source_social',            3, TRUE),
    ('budget_gte_100000',       10, TRUE),
    ('budget_gte_50000',         7, TRUE),
    ('budget_gte_10000',         4, TRUE),
    ('company_present',          5, TRUE),
    ('repeat_lead_bonus',        8, TRUE),
    ('message_length_gte_200',   3, TRUE)
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

-- ── leads ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS leads (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    phone           VARCHAR(30)     NOT NULL,
    message         TEXT            NOT NULL,
    source          VARCHAR(100)    NOT NULL,
    budget          DECIMAL(15, 2)  NULL,
    company         VARCHAR(200)    NULL,
    category        ENUM('HOT','WARM','COLD') NULL COMMENT 'Assigned after scoring',
    latest_score    INT             NULL COMMENT '0-100 composite score',
    status          ENUM('SCORED','PENDING_SCORE','DUPLICATE') NOT NULL DEFAULT 'PENDING_SCORE',
    is_repeat_lead  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    INDEX idx_leads_email   (email),
    INDEX idx_leads_phone   (phone),
    INDEX idx_leads_status  (status),
    INDEX idx_leads_category(category),
    INDEX idx_leads_score   (latest_score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
