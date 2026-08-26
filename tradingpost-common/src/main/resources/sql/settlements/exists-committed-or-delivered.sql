SELECT EXISTS (SELECT 1 FROM {schema}.settlements WHERE operation_id=? AND state IN ('MONEY_SETTLED','DELIVERED'))
