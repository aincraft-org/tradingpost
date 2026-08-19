UPDATE {schema}.buy_orders SET status='CANCELED' WHERE id=? AND buyer=? AND status='OPEN'
