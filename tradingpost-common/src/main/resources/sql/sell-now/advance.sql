UPDATE {schema}.sell_now_operations SET state=?,failure_detail=?,updated_at={now} WHERE operation_id=? AND state=?
