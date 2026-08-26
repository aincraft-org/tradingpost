SELECT count(*) FILTER (WHERE status <> 'DELIVERED') FROM {schema}.fills WHERE operation_id=?
