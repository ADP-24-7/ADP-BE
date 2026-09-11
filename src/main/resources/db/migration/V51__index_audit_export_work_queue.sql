create index idx_audit_export_job_requester_work
    on audit_export_job (institution_id, requester_id, status, created_at desc);

create index idx_audit_export_job_approval_work
    on audit_export_job (institution_id, status, created_at)
    where status in ('REQUESTED', 'APPROVED', 'GENERATING', 'READY', 'REJECTED', 'FAILED', 'EXPIRED');
