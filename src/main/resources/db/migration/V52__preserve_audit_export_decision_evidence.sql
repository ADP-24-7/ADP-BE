alter table audit_export_job
    add column revoked_by varchar(120),
    add column revoked_at timestamptz,
    add column revocation_reason varchar(500);

alter table audit_export_event
    add column reason_text varchar(500);

update audit_export_event event
set reason_text = case
    when event.action = 'REQUESTED' then job.request_reason
    when event.action = 'REJECTED' then job.approval_reason
    when event.action = 'APPROVED' and job.status <> 'REVOKED' then job.approval_reason
    when event.action = 'REVOKED' then coalesce(job.approval_reason, job.failure_code, event.reason_code)
    else null
end
from audit_export_job job
where job.export_id = event.export_id;

update audit_export_job job
set revoked_by = revoked.actor_id,
    revoked_at = revoked.occurred_at,
    revocation_reason = coalesce(job.approval_reason, job.failure_code, revoked.reason_code),
    approval_reason = case
        -- The legacy manual revoke path overwrote approval_reason with its revocation reason.
        when revoked.reason_code = 'AUDIT_EXPORT_REVOKED' then null
        else job.approval_reason
    end
from (
    select distinct on (event.export_id)
        event.export_id, event.actor_id, event.occurred_at, event.reason_code
    from audit_export_event event
    where event.action = 'REVOKED'
    order by event.export_id, event.occurred_at desc, event.event_id desc
) revoked
where job.status = 'REVOKED' and revoked.export_id = job.export_id;

alter table audit_export_job
    add constraint chk_audit_export_revocation_snapshot check (
        (status = 'REVOKED' and revoked_by is not null and revoked_at is not null and revocation_reason is not null)
        or status <> 'REVOKED'
    );
