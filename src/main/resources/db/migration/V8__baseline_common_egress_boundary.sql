create schema if not exists egress;

create table egress.destination_profile (
    id bigserial primary key,
    destination_profile_id varchar(120) not null unique,
    profile_version varchar(120) not null,
    profile_digest varchar(200) not null,
    contract_version varchar(120) not null,
    provider_profile_id varchar(120) not null,
    pack_type varchar(40) not null,
    schema_version varchar(120) not null,
    status varchar(40) not null,
    effective_at timestamptz not null,
    expires_at timestamptz,
    allowed_bindings varchar(2000) not null,
    field_contracts varchar(4000) not null,
    created_at timestamptz not null
);

create table runtime.outbound_candidate (
    id bigserial primary key,
    outbound_payload_id varchar(80) not null unique,
    execution_id varchar(80) not null references runtime.runtime_execution (execution_id),
    destination_profile_id varchar(120) not null,
    destination_profile_version varchar(120) not null,
    destination_profile_digest varchar(200) not null,
    pack_type varchar(40) not null,
    schema_version varchar(120) not null,
    candidate_payload_digest varchar(64) not null,
    field_count integer not null,
    guard_status varchar(40) not null,
    guard_reason_codes varchar(1000) not null,
    created_at timestamptz not null
);

create table runtime.connector_execution (
    id bigserial primary key,
    connector_execution_id varchar(80) not null unique,
    execution_id varchar(80) not null references runtime.runtime_execution (execution_id),
    outbound_payload_id varchar(80) not null references runtime.outbound_candidate (outbound_payload_id),
    outbound_payload_digest varchar(64) not null,
    connector_id varchar(120) not null,
    status varchar(40) not null,
    response_digest varchar(200),
    response_schema_version varchar(120),
    created_at timestamptz not null
);

create table runtime.response_guard_result (
    id bigserial primary key,
    connector_execution_id varchar(80) not null references runtime.connector_execution (connector_execution_id),
    execution_id varchar(80) not null references runtime.runtime_execution (execution_id),
    connector_id varchar(120) not null,
    connector_status varchar(40) not null,
    status varchar(40) not null,
    leakage_detected boolean not null,
    reason_codes varchar(1000) not null,
    created_at timestamptz not null
);

alter table runtime.runtime_execution
    add column outbound_payload_id varchar(80),
    add column outbound_payload_digest varchar(64),
    add column outbound_guard_status varchar(40),
    add column connector_execution_id varchar(80),
    add column connector_status varchar(40),
    add column response_guard_status varchar(40);

create unique index uq_destination_profile_provider_version
    on egress.destination_profile (provider_profile_id, profile_version);
create index idx_destination_profile_provider on egress.destination_profile (provider_profile_id, status);
create index idx_outbound_candidate_execution_id on runtime.outbound_candidate (execution_id);
create index idx_connector_execution_execution_id on runtime.connector_execution (execution_id);
create index idx_response_guard_execution_id on runtime.response_guard_result (execution_id);
