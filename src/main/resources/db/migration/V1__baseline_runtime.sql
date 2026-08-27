create table audit_event (
    id bigserial primary key,
    audit_id varchar(80) not null unique,
    request_id varchar(80) not null,
    trace_id varchar(80) not null,
    idempotency_key varchar(120) not null,
    workload_id varchar(120) not null,
    decision_id varchar(120) not null,
    reason_code varchar(80) not null,
    connector_status varchar(80) not null,
    created_at timestamptz not null
);

create index idx_audit_event_request_id on audit_event (request_id);
create index idx_audit_event_trace_id on audit_event (trace_id);
create index idx_audit_event_workload_id on audit_event (workload_id);
