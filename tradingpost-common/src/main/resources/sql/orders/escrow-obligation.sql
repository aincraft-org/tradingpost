SELECT COALESCE(SUM(escrow_reserved), 0) FROM {schema}.buy_orders WHERE status IN ('CREATING','OPEN')
