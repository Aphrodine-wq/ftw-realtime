-- Multi-tenant foundation for the PriceGrid productized API.
--
-- Strategy: ADD tenant_id columns nullable. NULL = the default ("legacy")
-- tenant — that's what FTW's existing rows belong to. New API key holders
-- get their own tenant_id and their writes/reads are scoped accordingly.
-- Endpoints without an API key continue to behave exactly as before (they
-- see the default tenant). This is a non-breaking change.

CREATE TABLE tenants (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(120) NOT NULL,
    slug         VARCHAR(60)  NOT NULL UNIQUE,
    -- Tier governs rate limits, history retention, webhook count.
    tier         VARCHAR(40)  NOT NULL DEFAULT 'starter',
    -- Free-form config blob. Per-tenant ZIP, alert thresholds, feature flags.
    config       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    inserted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenants_slug ON tenants(slug);

-- Seed the default tenant. Every existing row gets backfilled to it below.
INSERT INTO tenants (id, name, slug, tier)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default (FTW)', 'default', 'enterprise');

-- API keys. The full key looks like `pgk_live_xxxxxxxx...` and we only
-- store a sha256 of the random portion. The visible prefix (`pgk_live_`)
-- + last-4 of the random portion is shown to humans for identification.
CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    key_prefix      VARCHAR(24)  NOT NULL,            -- "pgk_live_abcd"
    key_hash        VARCHAR(64)  NOT NULL UNIQUE,     -- sha256 hex of full key
    -- Read | write | admin. Today only "read" matters for buyer-side use.
    scope           VARCHAR(20)  NOT NULL DEFAULT 'read',
    -- Per-key override of tenant rate limit (NULL = use tenant tier default).
    rate_limit_per_hour  INT,
    last_used_at    TIMESTAMPTZ,
    last_used_ip    VARCHAR(64),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at      TIMESTAMPTZ,
    inserted_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_hash ON api_keys(key_hash) WHERE active = TRUE;
CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);

-- Backfill tenant_id on every PriceGrid table. Default tenant for
-- everything that already exists.
ALTER TABLE materials              ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE price_snapshots        ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE price_alerts           ADD COLUMN tenant_id UUID REFERENCES tenants(id);

UPDATE materials       SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE price_snapshots SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE price_alerts    SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;

-- webhook_subscriptions already has tenant_id (added in V7). Backfill it.
UPDATE webhook_subscriptions SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE webhook_subscriptions
    ADD CONSTRAINT fk_webhook_subs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_materials_tenant       ON materials(tenant_id);
CREATE INDEX idx_price_snapshots_tenant ON price_snapshots(tenant_id);
CREATE INDEX idx_price_alerts_tenant    ON price_alerts(tenant_id);
CREATE INDEX idx_webhook_subs_tenant    ON webhook_subscriptions(tenant_id);

-- Daily request counter per (tenant, day). Drives rate limiting display
-- in the dashboard ("you've used 4,200 of 5,000 calls today"). The
-- in-memory Bucket4j enforces the actual cap at the request boundary.
CREATE TABLE api_usage_daily (
    tenant_id     UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    day           DATE         NOT NULL,
    request_count BIGINT       NOT NULL DEFAULT 0,
    last_request  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, day)
);

CREATE INDEX idx_api_usage_day ON api_usage_daily(day);

-- Audit log. Every catalog/webhook/api-key change goes here. Hand it to
-- compliance teams as the SOC 2 evidence trail.
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID REFERENCES tenants(id) ON DELETE SET NULL,
    actor        VARCHAR(120),         -- "api_key:<prefix>" or "system" or "user:<email>"
    entity       VARCHAR(60)  NOT NULL,
    entity_id    UUID,
    action       VARCHAR(40)  NOT NULL,
    before_json  JSONB,
    after_json   JSONB,
    metadata     JSONB,
    inserted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_time ON audit_log(tenant_id, inserted_at DESC);
CREATE INDEX idx_audit_entity      ON audit_log(entity, entity_id);

-- Per-attempt webhook delivery log. Replaces the in-place counters on
-- webhook_subscriptions for production-grade observability. Each attempt
-- is one row; the scheduler retries failures with exponential backoff.
CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    delivery_id     VARCHAR(64) NOT NULL UNIQUE,
    event_type      VARCHAR(60) NOT NULL,
    payload         JSONB       NOT NULL,
    attempt         INT         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_attempted_at  TIMESTAMPTZ,
    last_status     INT,
    last_error      TEXT,
    succeeded       BOOLEAN     NOT NULL DEFAULT FALSE,
    abandoned       BOOLEAN     NOT NULL DEFAULT FALSE,
    inserted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wh_deliveries_due
    ON webhook_deliveries(next_attempt_at)
    WHERE succeeded = FALSE AND abandoned = FALSE;
CREATE INDEX idx_wh_deliveries_sub ON webhook_deliveries(subscription_id, inserted_at DESC);
