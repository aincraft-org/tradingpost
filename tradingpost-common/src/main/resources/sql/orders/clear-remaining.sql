UPDATE {schema}.sell_orders SET quantity_remaining=0 WHERE id=? AND status IN ('CANCELED','EXPIRED') AND quantity_remaining>0
