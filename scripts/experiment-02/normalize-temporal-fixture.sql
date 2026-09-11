begin;

with normalized(transaction_id, normalized_posted_at) as (
    values
        ('da-txn-3031153', date '2026-08-14'),
        ('da-txn-3048211', date '2026-08-28'),
        ('da-txn-3007618', date '2026-08-28'),
        ('da-txn-3024917', date '2026-08-14'),
        ('da-txn-3048536', date '2026-08-14'),
        ('da-txn-3007823', date '2026-08-28')
)
update synthetic_transaction target
set posted_at = normalized.normalized_posted_at
from normalized
where target.transaction_id = normalized.transaction_id;

do $$
declare
    matched_rows integer;
begin
    select count(*) into matched_rows
    from synthetic_transaction
    where transaction_id in (
        'da-txn-3031153', 'da-txn-3048211', 'da-txn-3007618',
        'da-txn-3024917', 'da-txn-3048536', 'da-txn-3007823'
    )
      and posted_at between date '2026-09-11' - 90 and date '2026-09-11';

    if matched_rows <> 6 then
        raise exception 'E2_TEMPORAL_CONSISTENCY_FAILED: expected 6 rows, found %', matched_rows;
    end if;
end $$;

commit;
