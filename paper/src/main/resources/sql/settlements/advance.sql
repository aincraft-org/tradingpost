UPDATE {schema}.settlements SET state=?,last_error=?,lease_owner=NULL,lease_until=NULL,updated_at={now} WHERE id=? AND state=?
