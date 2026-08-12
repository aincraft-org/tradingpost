CREATE SCHEMA IF NOT EXISTS {{schema}};

CREATE TABLE IF NOT EXISTS {{schema}}.schema_version (
    version integer PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS {{schema}}.markets (
    name text PRIMARY KEY,
    display_name text NOT NULL,
    fee_bps integer NOT NULL CHECK (fee_bps BETWEEN 0 AND 10000),
    tax_bps integer NOT NULL CHECK (tax_bps BETWEEN 0 AND 10000),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS {{schema}}.trading_posts (
    id uuid PRIMARY KEY,
    market_name text NOT NULL REFERENCES {{schema}}.markets(name),
    world text NOT NULL,
    x integer NOT NULL,
    y integer NOT NULL,
    z integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (world, x, y, z)
);

CREATE TABLE IF NOT EXISTS {{schema}}.sell_orders (
    id uuid PRIMARY KEY,
    market_name text NOT NULL REFERENCES {{schema}}.markets(name),
    seller uuid NOT NULL,
    material text NOT NULL,
    item_blob bytea NOT NULL,
    fingerprint text NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    quantity_remaining integer NOT NULL CHECK (quantity_remaining >= 0 AND quantity_remaining <= quantity),
    unit_price numeric NOT NULL CHECK (unit_price > 0),
    mode text NOT NULL CHECK (mode IN ('NORMAL', 'INSTANT')),
    status text NOT NULL CHECK (status IN ('CREATING', 'ACTIVE', 'FILLED', 'CANCELED', 'EXPIRED', 'VOIDED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS {{schema}}.buy_orders (
    id uuid PRIMARY KEY,
    market_name text NOT NULL REFERENCES {{schema}}.markets(name),
    buyer uuid NOT NULL,
    material text NOT NULL,
    template_fingerprint text,
    quantity integer NOT NULL CHECK (quantity > 0),
    quantity_remaining integer NOT NULL CHECK (quantity_remaining >= 0 AND quantity_remaining <= quantity),
    unit_price numeric NOT NULL CHECK (unit_price > 0),
    escrow_reserved numeric NOT NULL CHECK (escrow_reserved >= 0),
    status text NOT NULL CHECK (status IN ('CREATING', 'OPEN', 'FILLED', 'CANCELED', 'EXPIRED', 'VOIDED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS {{schema}}.fills (
    fill_id uuid PRIMARY KEY,
    market_name text NOT NULL REFERENCES {{schema}}.markets(name),
    sell_order_id uuid NOT NULL REFERENCES {{schema}}.sell_orders(id),
    buy_order_id uuid NOT NULL REFERENCES {{schema}}.buy_orders(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric NOT NULL CHECK (unit_price > 0),
    item_blob bytea NOT NULL,
    remaining_item_blob bytea NOT NULL,
    status text NOT NULL CHECK (status IN ('RESERVED', 'MONEY_SETTLED', 'DELIVERED', 'VOIDED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS {{schema}}.settlements (
    id uuid PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('BUY_ESCROW', 'LISTING_FEE', 'MATCH_SETTLEMENT', 'REFUND', 'FEE_REFUND')),
    idempotency_key text NOT NULL UNIQUE,
    fill_id uuid UNIQUE REFERENCES {{schema}}.fills(fill_id),
    order_id uuid,
    amount numeric NOT NULL CHECK (amount > 0),
    state text NOT NULL CHECK (state IN ('RESERVED', 'MONEY_SETTLED', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error text,
    lease_owner text,
    lease_until timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL))
);

CREATE TABLE IF NOT EXISTS {{schema}}.mailbox_items (
    id uuid PRIMARY KEY,
    market_name text NOT NULL REFERENCES {{schema}}.markets(name),
    owner uuid NOT NULL,
    item_blob bytea NOT NULL,
    fingerprint text NOT NULL,
    reason text NOT NULL,
    state text NOT NULL CHECK (state IN ('UNCLAIMED', 'CLAIMING', 'CLAIMED', 'REVIEW')),
    settlement_id uuid UNIQUE,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    claimed_at timestamptz
);

CREATE TABLE IF NOT EXISTS {{schema}}.review_queue (
    id uuid PRIMARY KEY,
    player uuid,
    fingerprint text,
    detail jsonb NOT NULL,
    resolved_by uuid,
    resolved_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS sell_orders_market_book_idx
    ON {{schema}}.sell_orders (market_name, status, unit_price, created_at);
CREATE INDEX IF NOT EXISTS buy_orders_market_book_idx
    ON {{schema}}.buy_orders (market_name, status, unit_price DESC, created_at);
CREATE INDEX IF NOT EXISTS sell_orders_expiry_idx
    ON {{schema}}.sell_orders (status, expires_at);
CREATE INDEX IF NOT EXISTS buy_orders_expiry_idx
    ON {{schema}}.buy_orders (status, expires_at);
CREATE INDEX IF NOT EXISTS settlements_recovery_idx
    ON {{schema}}.settlements (state, lease_until, updated_at);
CREATE INDEX IF NOT EXISTS mailbox_owner_idx
    ON {{schema}}.mailbox_items (owner, market_name, state, created_at);
