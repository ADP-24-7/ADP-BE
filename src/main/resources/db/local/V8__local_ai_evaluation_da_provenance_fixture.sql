insert into synthetic_customer (
    customer_id,
    customer_name,
    phone_number,
    email,
    resident_registration_number,
    segment
) values (
    'da-customer-10832',
    'Nyla Aguirre',
    'synthetic-phone-10832',
    'customer-10832@example.test',
    'synthetic-rrn-10832',
    'customer_type_1'
) on conflict (customer_id) do nothing;

insert into auth_subject_grant (
    principal_id,
    workload_id,
    action_name,
    purpose,
    subject_type,
    subject_id
) values (
    'svc_local_runtime',
    'customer_summary',
    'RUNTIME_EXECUTE',
    'CUSTOMER_SUPPORT',
    'customer',
    'da-customer-10832'
) on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing;

insert into synthetic_account (
    account_id,
    customer_id,
    account_number,
    account_type,
    balance,
    opened_at
) values
    ('da-acct-200904', 'da-customer-10832', 'da-account-200904', 'Business', 84891.94, date '2021-12-12'),
    ('da-acct-200833', 'da-customer-10832', 'da-account-200833', 'Savings', 94714.26, date '2021-05-25')
on conflict (account_id) do nothing;

insert into synthetic_transaction (
    transaction_id,
    account_id,
    posted_at,
    merchant_category,
    amount,
    description
) values
    ('da-txn-3007618', 'da-acct-200904', current_date - 10, 'Withdrawal', 2123.67, 'DA source Transaction 7618'),
    ('da-txn-3024917', 'da-acct-200904', current_date - 20, 'Transfer', -2367.37, 'DA source Transaction 24917')
on conflict (transaction_id) do nothing;

insert into synthetic_customer (customer_id, customer_name, phone_number, email, resident_registration_number, segment)
values
    ('da-customer-10861', 'Roderick Mckee', 'synthetic-phone-10861', 'customer-10861@example.test', 'synthetic-rrn-10861', 'customer_type_3'),
    ('da-customer-10202', 'Elizabeth Tillman', 'synthetic-phone-10202', 'customer-10202@example.test', 'synthetic-rrn-10202', 'customer_type_1')
on conflict (customer_id) do nothing;

insert into auth_subject_grant (principal_id, workload_id, action_name, purpose, subject_type, subject_id)
values
    ('svc_local_runtime', 'customer_summary', 'RUNTIME_EXECUTE', 'CUSTOMER_SUPPORT', 'customer', 'da-customer-10861'),
    ('svc_local_runtime', 'customer_summary', 'RUNTIME_EXECUTE', 'CUSTOMER_SUPPORT', 'customer', 'da-customer-10202')
on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing;

insert into synthetic_account (account_id, customer_id, account_number, account_type, balance, opened_at)
values
    ('da-acct-200032', 'da-customer-10861', 'da-account-200032', 'Checking', 7264.93, date '2018-11-21'),
    ('da-acct-201288', 'da-customer-10202', 'da-account-201288', 'Business', 19110.41, date '2019-08-14')
on conflict (account_id) do nothing;

insert into synthetic_transaction (transaction_id, account_id, posted_at, merchant_category, amount, description)
values
    ('da-txn-3031153', 'da-acct-200032', date '2022-06-13', 'Transfer', 2259.69, 'DA source Transaction 3031153'),
    ('da-txn-3048211', 'da-acct-200032', date '2022-10-13', 'Deposit', 1178.79, 'DA source Transaction 3048211'),
    ('da-txn-3048536', 'da-acct-201288', date '2022-10-16', 'Transfer', 4999.59, 'DA source Transaction 3048536'),
    ('da-txn-3007823', 'da-acct-201288', date '2021-10-01', 'Withdrawal', 4875.14, 'DA source Transaction 3007823')
on conflict (transaction_id) do nothing;
