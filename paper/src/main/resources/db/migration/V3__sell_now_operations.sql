CREATE TABLE IF NOT EXISTS {{schema}}.sell_now_operations (
    operation_id uuid PRIMARY KEY,
    sell_order_id uuid NOT NULL UNIQUE REFERENCES {{schema}}.sell_orders(id),
    market_name text NOT NULL REFERENCES {{schema}}.markets(name),
    seller uuid NOT NULL,
    source_item_blob bytea NOT NULL,
    source_fingerprint text NOT NULL,
    original_quantity integer NOT NULL CHECK (original_quantity > 0),
    state text NOT NULL CHECK (state IN ('RESERVED', 'SETTLING', 'COMPLETED', 'FAILED', 'REVIEW')),
    failure_detail text,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE {{schema}}.fills
    ADD COLUMN IF NOT EXISTS operation_id uuid REFERENCES {{schema}}.sell_now_operations(operation_id);

ALTER TABLE {{schema}}.settlements
    ADD COLUMN IF NOT EXISTS operation_id uuid REFERENCES {{schema}}.sell_now_operations(operation_id);

CREATE INDEX IF NOT EXISTS sell_now_operations_state_idx
    ON {{schema}}.sell_now_operations (state, updated_at);
CREATE INDEX IF NOT EXISTS fills_operation_idx
    ON {{schema}}.fills (operation_id, status, created_at);
CREATE INDEX IF NOT EXISTS settlements_operation_idx
    ON {{schema}}.settlements (operation_id, state, created_at);
