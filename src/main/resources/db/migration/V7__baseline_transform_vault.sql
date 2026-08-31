create schema if not exists vault;

create table runtime.transform_execution (
    id bigserial primary key,
    transform_execution_id varchar(80) not null unique,
    execution_id varchar(80) not null references runtime.runtime_execution (execution_id),
    decision_id varchar(120) not null,
    status varchar(40) not null,
    output_digest varchar(64),
    field_count integer not null,
    created_at timestamptz not null
);

create table runtime.transform_field (
    id bigserial primary key,
    transform_execution_id varchar(80) not null references runtime.transform_execution (transform_execution_id),
    field_path varchar(500) not null,
    dataset_name varchar(120) not null,
    field_name varchar(120) not null,
    data_class varchar(80) not null,
    strategy varchar(80) not null,
    source_value_digest varchar(64) not null,
    transformed_value_digest varchar(64),
    token_ref varchar(120),
    created_at timestamptz not null
);

create table vault.token_mapping (
    id bigserial primary key,
    token_ref varchar(120) not null unique,
    mapping_scope varchar(240) not null,
    data_class varchar(80) not null,
    source_value_digest varchar(64) not null,
    key_version varchar(120) not null,
    mapping_version varchar(120) not null,
    expires_at timestamptz,
    created_at timestamptz not null,
    unique (mapping_scope, data_class, source_value_digest)
);

alter table runtime.runtime_execution
    add column transform_execution_id varchar(80),
    add column transform_status varchar(40),
    add column transform_output_digest varchar(64);

create index idx_transform_execution_execution_id on runtime.transform_execution (execution_id);
create index idx_transform_field_execution_id on runtime.transform_field (transform_execution_id);
create index idx_vault_token_mapping_scope on vault.token_mapping (mapping_scope, data_class);
