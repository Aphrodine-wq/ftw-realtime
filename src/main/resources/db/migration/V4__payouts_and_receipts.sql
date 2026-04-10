-- Payouts: contractor payment records linked to bids
CREATE TABLE payouts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_id          UUID NOT NULL REFERENCES bids(id) ON DELETE CASCADE,
    gross_amount    INT NOT NULL,
    platform_fee    INT NOT NULL,
    net_amount      INT NOT NULL,
    fee_percent     DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    failure_reason  TEXT,
    qb_bill_id      VARCHAR(255),
    qb_bill_payment_id VARCHAR(255),
    paid_at         TIMESTAMPTZ,
    inserted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payouts_bid UNIQUE (bid_id)
);

CREATE INDEX idx_payouts_bid_id ON payouts(bid_id);
CREATE INDEX idx_payouts_status ON payouts(status);

-- Receipts: payment receipts for completed transactions
CREATE TABLE receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number  VARCHAR(50) NOT NULL UNIQUE,
    bid_id          UUID NOT NULL REFERENCES bids(id) ON DELETE CASCADE,
    gross_amount    INT NOT NULL,
    platform_fee    INT NOT NULL,
    total_charged   INT NOT NULL,
    job_title       VARCHAR(500) NOT NULL DEFAULT '',
    contractor_name VARCHAR(255) NOT NULL DEFAULT '',
    homeowner_name  VARCHAR(255) NOT NULL DEFAULT '',
    paid_at         TIMESTAMPTZ,
    inserted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_receipts_bid UNIQUE (bid_id)
);

CREATE INDEX idx_receipts_bid_id ON receipts(bid_id);
