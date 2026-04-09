-- QuickBooks Online OAuth credentials per contractor
CREATE TABLE qb_credentials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    realm_id        VARCHAR(255) NOT NULL,
    access_token    TEXT NOT NULL,
    refresh_token   TEXT NOT NULL,
    token_expires_at TIMESTAMPTZ NOT NULL,
    company_name    VARCHAR(255),
    inserted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_qb_credentials_user UNIQUE (user_id)
);

CREATE INDEX idx_qb_credentials_user_id ON qb_credentials(user_id);

-- Track which FTW invoices have been synced to QuickBooks
ALTER TABLE invoices ADD COLUMN qb_invoice_id VARCHAR(255);
ALTER TABLE invoices ADD COLUMN qb_synced_at TIMESTAMPTZ;
