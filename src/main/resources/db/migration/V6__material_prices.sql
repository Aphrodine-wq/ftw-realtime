-- Material price ingestion (FTW Material Price Scraper integration).
-- Companion repo: ~/Projects/ftw-scraper. Daily scrape produces a JSON
-- snapshot pushed to the `prices` branch; a GitHub webhook fires
-- POST /api/v1/prices/ingest on this service, which upserts materials,
-- inserts new price snapshots, and emits price_alerts.

-- Master material catalog. material_key is the stable identifier from the
-- scraper (e.g. "mat_2x4x8_spf"); the UUID id is the FTW-internal handle.
CREATE TABLE materials (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_key  VARCHAR(100) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    category      VARCHAR(50)  NOT NULL,
    subcategory   VARCHAR(100),
    unit          VARCHAR(20)  NOT NULL,
    dimensions    VARCHAR(100),
    brand         VARCHAR(100),
    sku           VARCHAR(100),
    inserted_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_materials_category ON materials(category);

-- Time-series price observations. Insert-only — never update a snapshot;
-- a price correction means inserting a newer snapshot.
CREATE TABLE price_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id     UUID NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
    source          VARCHAR(32)  NOT NULL,
    source_url      VARCHAR(1024) NOT NULL,
    sku             VARCHAR(100),
    price_cents     BIGINT       NOT NULL,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'USD',
    in_stock        BOOLEAN,
    store_location  VARCHAR(20),
    scraped_at      TIMESTAMPTZ  NOT NULL,
    price_type      VARCHAR(20)  NOT NULL DEFAULT 'regular',
    scrape_run_id   VARCHAR(100),
    inserted_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Latest-price-per-source-per-material lookups dominate the read pattern.
CREATE INDEX idx_price_snapshots_material_source_time
    ON price_snapshots(material_id, source, scraped_at DESC);

-- Day-over-day reads grouped by run.
CREATE INDEX idx_price_snapshots_run ON price_snapshots(scrape_run_id);

-- Day-over-day price moves above threshold. Persisted so the team can
-- acknowledge alerts and so the in-app feed shows yesterday's movers.
CREATE TABLE price_alerts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id          UUID NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
    source               VARCHAR(32)  NOT NULL,
    previous_price_cents BIGINT       NOT NULL,
    current_price_cents  BIGINT       NOT NULL,
    change_cents         BIGINT       NOT NULL,
    change_pct           DOUBLE PRECISION NOT NULL,
    direction            VARCHAR(10)  NOT NULL,
    threshold_pct        DOUBLE PRECISION NOT NULL,
    scrape_run_id        VARCHAR(100),
    acknowledged         BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_by      UUID,
    acknowledged_at      TIMESTAMPTZ,
    inserted_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_price_alerts_unack
    ON price_alerts(acknowledged, inserted_at DESC)
    WHERE acknowledged = FALSE;

CREATE INDEX idx_price_alerts_material ON price_alerts(material_id, inserted_at DESC);
