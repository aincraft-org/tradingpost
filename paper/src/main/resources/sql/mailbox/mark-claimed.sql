UPDATE {schema}.mailbox_items SET state='CLAIMED',claimed_at={now} WHERE id=? AND state='CLAIMING'
