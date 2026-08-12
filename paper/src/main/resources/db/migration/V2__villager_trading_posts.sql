ALTER TABLE {{schema}}.trading_posts
    ADD COLUMN IF NOT EXISTS entity_uuid uuid;

CREATE UNIQUE INDEX IF NOT EXISTS trading_posts_entity_uuid_uq
    ON {{schema}}.trading_posts (entity_uuid)
    WHERE entity_uuid IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS trading_posts_market_npc_uq
    ON {{schema}}.trading_posts (market_name)
    WHERE entity_uuid IS NOT NULL;
