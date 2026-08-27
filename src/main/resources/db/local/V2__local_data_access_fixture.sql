insert into workload_registry (
    workload_id,
    display_name,
    description,
    enabled
) values (
    'customer_summary',
    'Customer Support Summary',
    'Synthetic financial customer summary retrieval for local runtime validation.',
    true
) on conflict (workload_id) do nothing;

insert into retrieval_profile (
    profile_id,
    workload_id,
    purpose,
    subject_type,
    enabled
) values (
    'profile_customer_summary_support',
    'customer_summary',
    'CUSTOMER_SUPPORT',
    'customer',
    true
) on conflict (profile_id) do nothing;

insert into retrieval_profile_dataset (profile_id, dataset_name, row_limit, time_window_days) values
    ('profile_customer_summary_support', 'customer', 1, null),
    ('profile_customer_summary_support', 'account', 2, null),
    ('profile_customer_summary_support', 'transaction', 2, 90)
on conflict (profile_id, dataset_name) do nothing;

insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class) values
    ('profile_customer_summary_support', 'customer', 'customer_id', 'CUSTOMER_IDENTIFIER'),
    ('profile_customer_summary_support', 'customer', 'segment', 'BUSINESS_METADATA'),
    ('profile_customer_summary_support', 'account', 'account_id', 'ACCOUNT_IDENTIFIER'),
    ('profile_customer_summary_support', 'account', 'account_type', 'FINANCIAL_METADATA'),
    ('profile_customer_summary_support', 'account', 'balance', 'FINANCIAL_AMOUNT'),
    ('profile_customer_summary_support', 'transaction', 'transaction_id', 'TRANSACTION_IDENTIFIER'),
    ('profile_customer_summary_support', 'transaction', 'posted_at', 'BUSINESS_METADATA'),
    ('profile_customer_summary_support', 'transaction', 'merchant_category', 'BUSINESS_METADATA'),
    ('profile_customer_summary_support', 'transaction', 'amount', 'FINANCIAL_AMOUNT')
on conflict (profile_id, dataset_name, field_name) do nothing;

insert into synthetic_customer (
    customer_id,
    customer_name,
    phone_number,
    email,
    resident_registration_number,
    segment
) values
    ('customer-100', 'Kim Minji', '010-1111-2222', 'minji.kim@example.test', '900101-2000000', 'preferred'),
    ('customer-999', 'Park Junho', '010-3333-4444', 'junho.park@example.test', '850505-1000000', 'standard')
on conflict (customer_id) do nothing;

insert into synthetic_account (
    account_id,
    customer_id,
    account_number,
    account_type,
    balance,
    opened_at
) values
    ('acct-100-1', 'customer-100', '110-123-456789', 'checking', 1200000.00, date '2023-01-10'),
    ('acct-999-1', 'customer-999', '110-987-654321', 'checking', 430000.00, date '2023-02-15')
on conflict (account_id) do nothing;

insert into synthetic_transaction (
    transaction_id,
    account_id,
    posted_at,
    merchant_category,
    amount,
    description
) values
    ('txn-100-1', 'acct-100-1', current_date - 10, 'grocery', -42000.00, 'Local market purchase'),
    ('txn-100-2', 'acct-100-1', current_date - 20, 'salary', 3000000.00, 'Monthly salary'),
    ('txn-100-3', 'acct-100-1', current_date - 120, 'travel', -210000.00, 'Old travel expense'),
    ('txn-999-1', 'acct-999-1', current_date - 5, 'utility', -88000.00, 'Utility payment')
on conflict (transaction_id) do nothing;
