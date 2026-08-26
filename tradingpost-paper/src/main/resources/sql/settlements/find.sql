SELECT id,kind,idempotency_key,fill_id,order_id,amount,state,attempts,last_error,lease_owner,lease_until,created_at,updated_at FROM {schema}.settlements WHERE id=?
