UPDATE {schema}.mailbox_items SET state='UNCLAIMED' WHERE id=? AND state='CLAIMING'
