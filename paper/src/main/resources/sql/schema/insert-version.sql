INSERT INTO {schema}.schema_version(version) VALUES(?) ON CONFLICT (version) DO NOTHING
