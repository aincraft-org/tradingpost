CREATE UNIQUE INDEX IF NOT EXISTS {name} ON {schema}.{table} ({columns}) WHERE entity_uuid IS NOT NULL
