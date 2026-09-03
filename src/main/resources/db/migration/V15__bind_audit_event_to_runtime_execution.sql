alter table audit_event
    add column execution_id varchar(80);

update audit_event ae
set execution_id = (
    select re.execution_id
    from runtime.runtime_execution re
    where re.decision_id = ae.decision_id
      and re.request_id = ae.request_id
      and re.trace_id = ae.trace_id
      and re.idempotency_key = ae.idempotency_key
      and re.workload_id = ae.workload_id
    order by re.created_at desc
    limit 1
)
where exists (
    select 1
    from runtime.runtime_execution re
    where re.decision_id = ae.decision_id
      and re.request_id = ae.request_id
      and re.trace_id = ae.trace_id
      and re.idempotency_key = ae.idempotency_key
      and re.workload_id = ae.workload_id
);

alter table audit_event
    add constraint fk_audit_event_runtime_execution
    foreign key (execution_id)
    references runtime.runtime_execution (execution_id);

create index idx_audit_event_execution_id
    on audit_event (execution_id);
