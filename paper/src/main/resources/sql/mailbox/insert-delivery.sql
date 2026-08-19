INSERT INTO {schema}.mailbox_items (id,market_name,owner,item_blob,fingerprint,reason,state,settlement_id) VALUES(?,?,?,?,?,?, 'UNCLAIMED',?) ON CONFLICT (settlement_id) DO NOTHING
