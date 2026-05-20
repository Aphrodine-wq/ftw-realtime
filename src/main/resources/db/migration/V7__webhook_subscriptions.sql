-- Outbound webhook subscriptions for the PriceGrid material price API.
-- Buyers/integrators register a URL + secret here; when a price_alert lands
-- (day-over-day move above threshold), the dispatch service POSTs a signed
-- payload to every active subscription matching the event type.
--
-- The signature header (X-PriceGrid-Signature) is HMAC-SHA256 of the raw
-- body using the subscriber's secret — same shape as GitHub's
-- X-Hub-Signature-256, on purpose, because every dev knows that pattern.

CREATE TABLE webhook_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Multi-tenant placeholder. Today everything's tenant_id NULL (single
    -- tenant). Productization adds an FK to a tenants table.
    tenant_id       UUID,
    name            VARCHAR(120) NOT NULL,
    url             VARCHAR(2048) NOT NULL,
    secret          VARCHAR(128) NOT NULL,
    event_types     VARCHAR(255) NOT NULL DEFAULT 'price.changed',
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    last_delivery_at      TIMESTAMPTZ,
    last_delivery_status  INT,
    consecutive_failures  INT NOT NULL DEFAULT 0,
    inserted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_subs_active ON webhook_subscriptions(active) WHERE active = TRUE;
