UPDATE {schema}.settlements SET lease_owner=?,lease_until=?,attempts=attempts+1,updated_at=? WHERE id=?
