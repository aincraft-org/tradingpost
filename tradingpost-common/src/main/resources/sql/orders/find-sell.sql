SELECT id,market_name,seller,material,item_blob,fingerprint,quantity,quantity_remaining,unit_price,mode,status,expires_at,created_at FROM {schema}.sell_orders WHERE id=?
