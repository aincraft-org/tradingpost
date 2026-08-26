UPDATE {schema}.mailbox_items SET state='CLAIMING' WHERE id=? AND owner=? AND state='UNCLAIMED'
