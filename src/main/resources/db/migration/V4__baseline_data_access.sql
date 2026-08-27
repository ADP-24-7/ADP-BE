create table workload_registry (
    workload_id varchar(120) primary key,
    display_name varchar(160) not null,
    description varchar(500) not null,
    enabled boolean not null,
    created_at timestamptz not null default now()
);

create table retrieval_profile (
    profile_id varchar(120) primary key,
    workload_id varchar(120) not null references workload_registry (workload_id),
    purpose varchar(160) not null,
    subject_type varchar(80) not null,
    time_window_days integer not null,
    row_limit integer not null,
    enabled boolean not null,
    created_at timestamptz not null default now(),
    unique (workload_id, purpose, subject_type)
);

create table retrieval_profile_field (
    profile_id varchar(120) not null references retrieval_profile (profile_id),
    dataset_name varchar(120) not null,
    field_name varchar(120) not null,
    data_class varchar(80) not null,
    primary key (profile_id, dataset_name, field_name)
);

create table synthetic_customer (
    customer_id varchar(80) primary key,
    customer_name varchar(160) not null,
    phone_number varchar(40) not null,
    email varchar(160) not null,
    resident_registration_number varchar(40) not null,
    segment varchar(80) not null,
    created_at timestamptz not null default now()
);

create table synthetic_account (
    account_id varchar(80) primary key,
    customer_id varchar(80) not null references synthetic_customer (customer_id),
    account_number varchar(80) not null,
    account_type varchar(80) not null,
    balance numeric(18, 2) not null,
    opened_at date not null
);

create table synthetic_transaction (
    transaction_id varchar(80) primary key,
    account_id varchar(80) not null references synthetic_account (account_id),
    posted_at date not null,
    merchant_category varchar(120) not null,
    amount numeric(18, 2) not null,
    description varchar(240) not null
);

create table data_access_event (
    id bigserial primary key,
    data_access_id varchar(80) not null unique,
    request_id varchar(80) not null,
    trace_id varchar(80) not null,
    workload_id varchar(120) not null,
    purpose varchar(160) not null,
    subject_type varchar(80) not null,
    subject_id varchar(160) not null,
    profile_id varchar(120) not null,
    selected_fields varchar(2000) not null,
    row_count integer not null,
    created_at timestamptz not null
);

create index idx_data_access_event_request_id on data_access_event (request_id);
create index idx_data_access_event_trace_id on data_access_event (trace_id);
create index idx_data_access_event_workload_id on data_access_event (workload_id);

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
);

insert into retrieval_profile (
    profile_id,
    workload_id,
    purpose,
    subject_type,
    time_window_days,
    row_limit,
    enabled
) values (
    'profile_customer_summary_support',
    'customer_summary',
    'CUSTOMER_SUPPORT',
    'customer',
    90,
    2,
    true
);

insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class) values
    ('profile_customer_summary_support', 'customer', 'customer_id', 'CUSTOMER_IDENTIFIER'),
    ('profile_customer_summary_support', 'customer', 'segment', 'BUSINESS_METADATA'),
    ('profile_customer_summary_support', 'account', 'account_id', 'ACCOUNT_IDENTIFIER'),
    ('profile_customer_summary_support', 'account', 'account_type', 'FINANCIAL_METADATA'),
    ('profile_customer_summary_support', 'account', 'balance', 'FINANCIAL_AMOUNT'),
    ('profile_customer_summary_support', 'transaction', 'transaction_id', 'TRANSACTION_IDENTIFIER'),
    ('profile_customer_summary_support', 'transaction', 'posted_at', 'BUSINESS_METADATA'),
    ('profile_customer_summary_support', 'transaction', 'merchant_category', 'BUSINESS_METADATA'),
    ('profile_customer_summary_support', 'transaction', 'amount', 'FINANCIAL_AMOUNT');

insert into synthetic_customer (
    customer_id,
    customer_name,
    phone_number,
    email,
    resident_registration_number,
    segment
) values
    ('customer-100', 'Kim Minji', '010-1111-2222', 'minji.kim@example.test', '900101-2000000', 'preferred'),
    ('customer-999', 'Park Junho', '010-3333-4444', 'junho.park@example.test', '850505-1000000', 'standard');

insert into synthetic_account (
    account_id,
    customer_id,
    account_number,
    account_type,
    balance,
    opened_at
) values
    ('acct-100-1', 'customer-100', '110-123-456789', 'checking', 1200000.00, date '2023-01-10'),
    ('acct-999-1', 'customer-999', '110-987-654321', 'checking', 430000.00, date '2023-02-15');

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
    ('txn-999-1', 'acct-999-1', current_date - 5, 'utility', -88000.00, 'Utility payment');
