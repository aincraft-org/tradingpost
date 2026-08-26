SELECT id,market_name,buyer,material,template_fingerprint,quantity,quantity_remaining,unit_price,escrow_reserved,status,expires_at,created_at FROM {schema}.buy_orders WHERE id=?
