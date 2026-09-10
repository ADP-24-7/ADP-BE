create index idx_runtime_execution_security_finding_scope
    on runtime.runtime_execution (institution_id, execution_pack, workload_id, execution_id);

create index idx_response_sensitive_finding_created
    on runtime.response_sensitive_finding (created_at desc, id desc, execution_id);

alter table runtime.runtime_execution
    drop constraint chk_runtime_execution_pack;

update runtime.runtime_execution execution
set execution_pack = evaluation.pack_type
from runtime.execution_pack_policy_evaluation evaluation
where evaluation.execution_id = execution.execution_id
  and execution.execution_pack is null;

update runtime.runtime_execution execution
set execution_pack = 'AI'
where execution.execution_pack is null
  and exists (
      select 1
      from runtime.ai_model_execution_evidence evidence
      where evidence.execution_id = execution.execution_id
  );

update runtime.runtime_execution execution
set execution_pack = 'DIGITAL_ASSET'
where execution.execution_pack is null
  and exists (
      select 1
      from runtime.digital_asset_transaction transaction_evidence
      where transaction_evidence.execution_id = execution.execution_id
  );

do $$
begin
    if exists (
        select 1
        from runtime.response_sensitive_finding finding
        join runtime.runtime_execution execution
          on execution.execution_id = finding.execution_id
        where execution.execution_pack is null
    ) then
        raise exception
            'Security findings require a resolved runtime execution pack';
    end if;
end
$$;

alter table runtime.runtime_execution
    add constraint chk_runtime_execution_pack
    check (
        (execution_pack is null or execution_pack in ('COMMON', 'AI', 'DIGITAL_ASSET', 'SAAS'))
        and (status <> 'REVIEW_REQUIRED' or execution_pack is not null)
    ) not valid;

do $$
begin
    if not exists (
        select 1
        from runtime.runtime_execution
        where execution_pack is not null
          and execution_pack not in ('COMMON', 'AI', 'DIGITAL_ASSET', 'SAAS')
           or status = 'REVIEW_REQUIRED' and execution_pack is null
    ) then
        alter table runtime.runtime_execution
            validate constraint chk_runtime_execution_pack;
    end if;
end
$$;
