-- V2: SubContractor support
-- Adds sub_contractors, sub_jobs, sub_bids, sub_payouts tables
-- Updates users table with roles and active_role columns

-- ============================================================
-- users: add roles and active_role columns
-- ============================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS active_role VARCHAR(50) NOT NULL DEFAULT 'homeowner';
ALTER TABLE users ADD COLUMN IF NOT EXISTS roles VARCHAR(255) NOT NULL DEFAULT '';

-- Backfill: set active_role = role and roles = role for existing users
UPDATE users SET active_role = role, roles = role WHERE active_role = 'homeowner' AND role != 'homeowner';
UPDATE users SET active_role = role, roles = role WHERE roles = '';

-- ============================================================
-- sub_contractors
-- ============================================================
CREATE TABLE sub_contractors (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    company            VARCHAR(255),
    bio                TEXT,
    specialty          VARCHAR(255),
    skills             TEXT,
    location           VARCHAR(255),
    service_radius     INTEGER NOT NULL DEFAULT 50,
    years_experience   INTEGER,
    hourly_rate        DOUBLE PRECISION,
    verified           BOOLEAN NOT NULL DEFAULT FALSE,
    licensed           BOOLEAN NOT NULL DEFAULT FALSE,
    insured            BOOLEAN NOT NULL DEFAULT FALSE,
    rating             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    review_count       INTEGER NOT NULL DEFAULT 0,
    sub_jobs_completed INTEGER NOT NULL DEFAULT 0,
    inserted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sub_contractors_user_id ON sub_contractors (user_id);
CREATE INDEX idx_sub_contractors_location ON sub_contractors (location);
CREATE INDEX idx_sub_contractors_specialty ON sub_contractors (specialty);

-- ============================================================
-- sub_jobs
-- ============================================================
CREATE TABLE sub_jobs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contractor_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id         VARCHAR(255) NOT NULL,
    milestone_label    VARCHAR(255) NOT NULL,
    milestone_index    INTEGER NOT NULL DEFAULT 0,
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    category           VARCHAR(255),
    skills             TEXT,
    location           VARCHAR(255),
    budget_min         INTEGER,
    budget_max         INTEGER,
    payment_path       VARCHAR(50) NOT NULL DEFAULT 'contractor_escrow',
    disclosed_to_owner BOOLEAN NOT NULL DEFAULT FALSE,
    status             VARCHAR(50) NOT NULL DEFAULT 'open',
    deadline           TIMESTAMPTZ,
    bid_count          INTEGER NOT NULL DEFAULT 0,
    inserted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sub_jobs_contractor_id ON sub_jobs (contractor_id);
CREATE INDEX idx_sub_jobs_status ON sub_jobs (status);
CREATE INDEX idx_sub_jobs_category ON sub_jobs (category);

-- ============================================================
-- sub_bids
-- ============================================================
CREATE TABLE sub_bids (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_job_id         UUID NOT NULL REFERENCES sub_jobs(id) ON DELETE CASCADE,
    sub_contractor_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount             INTEGER NOT NULL DEFAULT 0,
    message            TEXT,
    timeline           VARCHAR(255),
    status             VARCHAR(50) NOT NULL DEFAULT 'pending',
    inserted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (sub_job_id, sub_contractor_id)
);

CREATE INDEX idx_sub_bids_sub_job_id ON sub_bids (sub_job_id);
CREATE INDEX idx_sub_bids_sub_contractor_id ON sub_bids (sub_contractor_id);

-- ============================================================
-- sub_payouts
-- ============================================================
CREATE TABLE sub_payouts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_job_id         UUID NOT NULL UNIQUE REFERENCES sub_jobs(id) ON DELETE CASCADE,
    sub_contractor_id  UUID NOT NULL,
    gross_amount       DOUBLE PRECISION NOT NULL,
    platform_fee       DOUBLE PRECISION NOT NULL,
    net_amount         DOUBLE PRECISION NOT NULL,
    fee_percent        DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    payment_path       VARCHAR(50) NOT NULL DEFAULT 'contractor_escrow',
    status             VARCHAR(50) NOT NULL DEFAULT 'queued',
    paid_at            TIMESTAMPTZ,
    inserted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
