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
    enabled boolean not null,
    created_at timestamptz not null default now(),
    unique (workload_id, purpose, subject_type)
);

create table retrieval_profile_dataset (
    profile_id varchar(120) not null references retrieval_profile (profile_id),
    dataset_name varchar(120) not null,
    row_limit integer not null,
    time_window_days integer,
    primary key (profile_id, dataset_name)
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
    subject_ref_digest varchar(64) not null,
    profile_id varchar(120) not null,
    selected_fields varchar(2000) not null,
    row_count integer not null,
    created_at timestamptz not null
);

create index idx_data_access_event_request_id on data_access_event (request_id);
create index idx_data_access_event_trace_id on data_access_event (trace_id);
create index idx_data_access_event_workload_id on data_access_event (workload_id);
