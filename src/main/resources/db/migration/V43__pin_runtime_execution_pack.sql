alter table runtime.runtime_execution
    add column execution_pack varchar(40);

update runtime.runtime_execution execution
set execution_pack = profile.pack_type
from egress.destination_profile profile
where execution.destination_profile_id = profile.destination_profile_id
  and execution.destination_profile_version = profile.profile_version
  and execution.execution_pack is null;

alter table runtime.runtime_execution
    add constraint chk_runtime_execution_pack
    check (
        (execution_pack is null or execution_pack in ('COMMON', 'AI', 'DIGITAL_ASSET', 'SAAS'))
        and (status <> 'REVIEW_REQUIRED' or execution_pack is not null)
    );

create index idx_runtime_execution_review_queue
    on runtime.runtime_execution (institution_id, execution_pack, updated_at desc, execution_id desc)
    where status = 'REVIEW_REQUIRED';
