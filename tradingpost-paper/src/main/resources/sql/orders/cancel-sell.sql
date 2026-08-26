UPDATE {schema}.sell_orders SET status='CANCELED' WHERE id=? AND seller=? AND status='ACTIVE'
