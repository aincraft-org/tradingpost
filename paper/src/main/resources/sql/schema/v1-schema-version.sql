CREATE TABLE IF NOT EXISTS {schema}.schema_version (version {integer} PRIMARY KEY, applied_at {ts} NOT NULL{defaultNow})
