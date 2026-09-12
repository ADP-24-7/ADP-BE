alter table evidence.reference_evidence_policy_artifact
    add column requirement_refs varchar(160)[] not null default '{}',
    add column control_refs varchar(160)[] not null default '{}';

comment on column evidence.reference_evidence_policy_artifact.requirement_refs is
    'Registry Requirement identities bound as trace metadata only; does not activate policy';
comment on column evidence.reference_evidence_policy_artifact.control_refs is
    'Registry Control identities bound as trace metadata only; does not activate policy';
