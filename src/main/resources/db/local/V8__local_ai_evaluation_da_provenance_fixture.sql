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
