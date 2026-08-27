alter table audit_event
    add column policy_artifact_id varchar(120) not null default 'PROJECT_PROVISIONAL',
    add column policy_version varchar(80) not null default '0.0.0',
    add column policy_digest varchar(160) not null default 'local-fixture';

alter table audit_event
    alter column policy_artifact_id drop default,
    alter column policy_version drop default,
    alter column policy_digest drop default;

create index idx_audit_event_policy_snapshot
    on audit_event (policy_artifact_id, policy_version, policy_digest);
