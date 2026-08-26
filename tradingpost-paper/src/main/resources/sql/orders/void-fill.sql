UPDATE {schema}.fills SET status='VOIDED' WHERE fill_id=? AND status='RESERVED'
