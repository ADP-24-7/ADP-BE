alter table egress.destination_profile
    add column tenant_id varchar(120),
    add column region varchar(80),
    add column retention_policy varchar(120),
    add column training_use_allowed boolean not null default false;

alter table runtime.runtime_execution
    add column institution_id varchar(120),
    add column approval_reference varchar(160),
    add column approval_version varchar(120),
    add column approval_scope_digest varchar(200),
    add column approval_reuse_status varchar(40),
    add column policy_layers_digest varchar(64),
    add column destination_tenant_id varchar(120),
    add column destination_region varchar(80),
    add column destination_retention_policy varchar(120),
    add column destination_training_use_allowed boolean,
    add column requested_fields_digest varchar(64),
    add column requested_field_count integer,
    add column retrieved_fields_digest varchar(64),
    add column retrieved_field_count integer,
    add column transformed_fields_digest varchar(64),
    add column transformed_field_count integer,
    add column released_fields_digest varchar(64),
    add column released_field_count integer,
    add column provider_request_id varchar(80),
    add column provider_request_digest varchar(64),
    add column provider_response_digest varchar(200),
    add column response_guard_reason_codes varchar(1000);

create table runtime.policy_harness_binding (
    id bigserial primary key,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    institution_id varchar(120) not null,
    approval_reference varchar(160) not null,
    approval_version varchar(120) not null,
    approval_scope_digest varchar(200) not null,
    approval_reuse_status varchar(40) not null,
    reason_codes varchar(1000) not null,
    policy_layers varchar(4000) not null,
    policy_layers_digest varchar(64) not null,
    requested_fields varchar(4000) not null,
    requested_fields_digest varchar(64) not null,
    retrieved_fields varchar(4000) not null,
    retrieved_fields_digest varchar(64) not null,
    transformed_fields varchar(4000) not null,
    transformed_fields_digest varchar(64) not null,
    released_fields varchar(4000) not null,
    released_fields_digest varchar(64) not null,
    created_at timestamptz not null
);

create table runtime.provider_request (
    provider_request_id varchar(80) primary key,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    outbound_payload_id varchar(80) not null references runtime.outbound_candidate (outbound_payload_id),
    provider_profile_id varchar(120) not null,
    destination_profile_id varchar(120) not null,
    destination_profile_version varchar(120) not null,
    tenant_id varchar(120) not null,
    region varchar(80) not null,
    retention_policy varchar(120) not null,
    training_use_allowed boolean not null,
    schema_version varchar(120) not null,
    canonical_payload_digest varchar(64) not null,
    field_count integer not null,
    created_at timestamptz not null
);

alter table runtime.response_guard_result
    add column response_digest varchar(200),
    add column detector_version varchar(120),
    add column finding_count integer not null default 0;

create table runtime.response_sensitive_finding (
    id bigserial primary key,
    connector_execution_id varchar(80) not null references runtime.connector_execution (connector_execution_id),
    execution_id varchar(80) not null references runtime.runtime_execution (execution_id),
    finding_type varchar(120) not null,
    location varchar(500) not null,
    start_offset integer not null,
    end_offset integer not null,
    detector_version varchar(120) not null,
    evidence_digest varchar(64) not null,
    created_at timestamptz not null
);

alter table runtime.policy_harness_binding
    add constraint chk_policy_harness_approval_reuse_status
    check (approval_reuse_status in ('REUSE_ALLOWED', 'TRANSFORM_REQUIRED', 'REVIEW_REQUIRED', 'BLOCKED'));

alter table runtime.runtime_execution
    add constraint chk_runtime_execution_approval_reuse_status
    check (approval_reuse_status is null or approval_reuse_status in ('REUSE_ALLOWED', 'TRANSFORM_REQUIRED', 'REVIEW_REQUIRED', 'BLOCKED'));

create index idx_policy_harness_approval_reference
    on runtime.policy_harness_binding (approval_reference, approval_version);
create index idx_provider_request_destination
    on runtime.provider_request (destination_profile_id, destination_profile_version);
create index idx_response_sensitive_finding_execution
    on runtime.response_sensitive_finding (execution_id);
