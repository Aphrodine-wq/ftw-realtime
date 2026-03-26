-- FTW Realtime V1 Baseline Migration
-- Creates all tables matching JPA entity definitions

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- users
-- ============================================================
CREATE TABLE users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email              VARCHAR(255) NOT NULL UNIQUE,
    name               VARCHAR(255) NOT NULL,
    role               VARCHAR(50)  NOT NULL DEFAULT 'homeowner',
    phone              VARCHAR(50),
    location           VARCHAR(255),
    license_number     VARCHAR(255),
    insurance_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    rating             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    jobs_completed     INTEGER      NOT NULL DEFAULT 0,
    avatar_url         VARCHAR(1024),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    password_hash      VARCHAR(255),
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    quality_score      INTEGER,
    inserted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

-- ============================================================
-- clients
-- ============================================================
CREATE TABLE clients (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    phone         VARCHAR(50),
    address       TEXT,
    notes         TEXT,
    contractor_id UUID REFERENCES users(id),
    user_id       UUID REFERENCES users(id),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- jobs
-- ============================================================
CREATE TABLE jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    category     VARCHAR(255),
    budget_min   INTEGER,
    budget_max   INTEGER,
    location     VARCHAR(255),
    status       VARCHAR(50)  NOT NULL DEFAULT 'open',
    bid_count    INTEGER      NOT NULL DEFAULT 0,
    latitude     DOUBLE PRECISION,
    longitude    DOUBLE PRECISION,
    homeowner_id UUID REFERENCES users(id),
    inserted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jobs_homeowner_id ON jobs (homeowner_id);
CREATE INDEX idx_jobs_status ON jobs (status);
CREATE INDEX idx_jobs_category ON jobs (category);

-- ============================================================
-- bids
-- ============================================================
CREATE TABLE bids (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amount        INTEGER      NOT NULL DEFAULT 0,
    message       TEXT,
    timeline      VARCHAR(255),
    status        VARCHAR(50)  NOT NULL DEFAULT 'pending',
    job_id        UUID REFERENCES jobs(id),
    contractor_id UUID REFERENCES users(id),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (job_id, contractor_id)
);

CREATE INDEX idx_bids_job_id ON bids (job_id);
CREATE INDEX idx_bids_contractor_id ON bids (contractor_id);

-- ============================================================
-- estimates
-- ============================================================
CREATE TABLE estimates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    total         INTEGER      NOT NULL DEFAULT 0,
    status        VARCHAR(50)  NOT NULL DEFAULT 'draft',
    valid_until   TIMESTAMPTZ,
    notes         TEXT,
    contractor_id UUID REFERENCES users(id),
    client_id     UUID REFERENCES clients(id),
    job_id        UUID REFERENCES jobs(id),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- line_items
-- ============================================================
CREATE TABLE line_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    description TEXT,
    quantity    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    unit        VARCHAR(50),
    unit_price  INTEGER      NOT NULL DEFAULT 0,
    total       INTEGER      NOT NULL DEFAULT 0,
    category    VARCHAR(255),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    estimate_id UUID REFERENCES estimates(id),
    inserted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_line_items_estimate_id ON line_items (estimate_id);

-- ============================================================
-- projects
-- ============================================================
CREATE TABLE projects (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(50)  NOT NULL DEFAULT 'planning',
    start_date    DATE,
    end_date      DATE,
    budget        INTEGER      NOT NULL DEFAULT 0,
    spent         INTEGER      NOT NULL DEFAULT 0,
    contractor_id UUID REFERENCES users(id),
    homeowner_id  UUID REFERENCES users(id),
    job_id        UUID REFERENCES jobs(id),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- invoices
-- ============================================================
CREATE TABLE invoices (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(255) NOT NULL,
    amount         INTEGER      NOT NULL DEFAULT 0,
    status         VARCHAR(50)  NOT NULL DEFAULT 'draft',
    due_date       DATE,
    paid_at        TIMESTAMPTZ,
    notes          TEXT,
    contractor_id  UUID REFERENCES users(id),
    client_id      UUID REFERENCES clients(id),
    estimate_id    UUID REFERENCES estimates(id),
    project_id     UUID REFERENCES projects(id),
    inserted_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- conversations
-- ============================================================
CREATE TABLE conversations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id        UUID REFERENCES jobs(id),
    homeowner_id  UUID REFERENCES users(id),
    contractor_id UUID REFERENCES users(id),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (job_id, homeowner_id, contractor_id)
);

-- ============================================================
-- messages
-- ============================================================
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    body            TEXT         NOT NULL,
    conversation_id UUID REFERENCES conversations(id),
    sender_id       UUID REFERENCES users(id),
    inserted_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);

-- ============================================================
-- reviews
-- ============================================================
CREATE TABLE reviews (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating      INTEGER      NOT NULL DEFAULT 0,
    comment     TEXT,
    response    TEXT,
    reviewer_id UUID REFERENCES users(id),
    reviewed_id UUID REFERENCES users(id),
    job_id      UUID REFERENCES jobs(id),
    inserted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (reviewer_id, job_id)
);

CREATE INDEX idx_reviews_reviewed_id ON reviews (reviewed_id);

-- ============================================================
-- notifications
-- ============================================================
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(255) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    read        BOOLEAN      NOT NULL DEFAULT FALSE,
    metadata    JSONB,
    user_id     UUID REFERENCES users(id),
    inserted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);

-- ============================================================
-- uploads
-- ============================================================
CREATE TABLE uploads (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size         INTEGER,
    path         VARCHAR(1024) NOT NULL,
    entity_type  VARCHAR(255) NOT NULL,
    entity_id    UUID         NOT NULL,
    uploader_id  UUID REFERENCES users(id),
    inserted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- user_settings
-- ============================================================
CREATE TABLE user_settings (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notifications_email    BOOLEAN      NOT NULL DEFAULT TRUE,
    notifications_push     BOOLEAN      NOT NULL DEFAULT TRUE,
    notifications_sms      BOOLEAN      NOT NULL DEFAULT FALSE,
    appearance_theme       VARCHAR(50)  NOT NULL DEFAULT 'light',
    language               VARCHAR(10)  NOT NULL DEFAULT 'en',
    timezone               VARCHAR(50)  NOT NULL DEFAULT 'America/Chicago',
    privacy_profile_visible BOOLEAN     NOT NULL DEFAULT TRUE,
    privacy_show_rating    BOOLEAN      NOT NULL DEFAULT TRUE,
    user_id                UUID REFERENCES users(id),
    inserted_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);

-- ============================================================
-- verifications
-- ============================================================
CREATE TABLE verifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step          VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'pending',
    data          JSONB,
    reviewed_at   TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    notes         TEXT,
    contractor_id UUID REFERENCES users(id),
    reviewed_by   UUID REFERENCES users(id),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (contractor_id, step)
);

-- ============================================================
-- fair_records
-- ============================================================
CREATE TABLE fair_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(255) UNIQUE,
    category                    VARCHAR(255),
    location_city               VARCHAR(255),
    scope_summary               TEXT,
    estimated_budget            INTEGER      NOT NULL DEFAULT 0,
    final_cost                  INTEGER      NOT NULL DEFAULT 0,
    budget_accuracy_pct         DOUBLE PRECISION,
    on_budget                   BOOLEAN      NOT NULL DEFAULT FALSE,
    estimated_end_date          DATE,
    actual_completion_date      DATE,
    on_time                     BOOLEAN      NOT NULL DEFAULT FALSE,
    quality_score_at_completion INTEGER      NOT NULL DEFAULT 0,
    avg_rating                  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    review_count                INTEGER      NOT NULL DEFAULT 0,
    dispute_count               INTEGER      NOT NULL DEFAULT 0,
    photos                      TEXT[],
    homeowner_confirmed         BOOLEAN      NOT NULL DEFAULT FALSE,
    confirmed_at                TIMESTAMPTZ,
    signature_hash              VARCHAR(255),
    project_id                  UUID REFERENCES projects(id),
    contractor_id               UUID REFERENCES users(id),
    homeowner_id                UUID REFERENCES users(id),
    job_id                      UUID REFERENCES jobs(id),
    inserted_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- push_tokens
-- ============================================================
CREATE TABLE push_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(512) NOT NULL UNIQUE,
    platform    VARCHAR(50)  NOT NULL,
    user_id     UUID REFERENCES users(id),
    inserted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- content_flags
-- ============================================================
CREATE TABLE content_flags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(255) NOT NULL,
    entity_id   UUID         NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'open',
    resolved_at TIMESTAMPTZ,
    notes       TEXT,
    flagged_by  UUID REFERENCES users(id),
    resolved_by UUID REFERENCES users(id),
    inserted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- disputes
-- ============================================================
CREATE TABLE disputes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reason           VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'open',
    description      TEXT,
    resolution_notes TEXT,
    opened_at        TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    job_id           UUID REFERENCES jobs(id),
    opened_by        UUID REFERENCES users(id),
    contractor_id    UUID REFERENCES users(id),
    homeowner_id     UUID REFERENCES users(id),
    resolved_by      UUID REFERENCES users(id),
    inserted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- dispute_evidence
-- ============================================================
CREATE TABLE dispute_evidence (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type         VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    submitted_at TIMESTAMPTZ,
    dispute_id   UUID REFERENCES disputes(id),
    submitted_by UUID REFERENCES users(id),
    inserted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- revenue_snapshots
-- ============================================================
CREATE TABLE revenue_snapshots (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date               DATE         NOT NULL UNIQUE,
    total_revenue      INTEGER      NOT NULL DEFAULT 0,
    commission_revenue INTEGER      NOT NULL DEFAULT 0,
    jobs_completed     INTEGER      NOT NULL DEFAULT 0,
    bids_placed        INTEGER      NOT NULL DEFAULT 0,
    users_signed_up    INTEGER      NOT NULL DEFAULT 0,
    disputes_opened    INTEGER      NOT NULL DEFAULT 0,
    breakdown          JSONB,
    inserted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- transaction_logs
-- ============================================================
CREATE TABLE transaction_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type          VARCHAR(255) NOT NULL,
    amount        INTEGER      NOT NULL DEFAULT 0,
    metadata      JSONB,
    recorded_at   TIMESTAMPTZ,
    job_id        UUID REFERENCES jobs(id),
    contractor_id UUID REFERENCES users(id),
    homeowner_id  UUID REFERENCES users(id),
    invoice_id    UUID REFERENCES invoices(id),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- fair_prices
-- ============================================================
CREATE TABLE fair_prices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category      VARCHAR(255) NOT NULL,
    zip_prefix    VARCHAR(10)  NOT NULL,
    size          VARCHAR(50)  NOT NULL,
    low           INTEGER      NOT NULL DEFAULT 0,
    high          INTEGER      NOT NULL DEFAULT 0,
    materials_pct DOUBLE PRECISION,
    labor_pct     DOUBLE PRECISION,
    confidence    VARCHAR(50),
    raw_response  TEXT,
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (category, zip_prefix, size)
);
