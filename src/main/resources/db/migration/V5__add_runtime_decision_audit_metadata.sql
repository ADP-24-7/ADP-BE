alter table audit_event
    add column policy_action varchar(40) not null default 'UNKNOWN',
    add column policy_artifact_version varchar(120) not null default 'legacy-not-evaluated',
    add column policy_artifact_digest_algorithm varchar(40) not null default 'legacy',
    add column policy_artifact_digest_value varchar(200) not null default 'legacy-not-evaluated',
    add column final_action varchar(40) not null default 'UNKNOWN',
    add column authorization_result varchar(40) not null default 'NOT_EVALUATED',
    add column applicability_result varchar(40) not null default 'NOT_EVALUATED',
    add column runtime_context_digest varchar(64) not null default 'legacy-not-evaluated',
    add column matched_rule_ids varchar(2000) not null default '',
    add column evidence_refs varchar(2000) not null default '',
    add column required_controls varchar(2000) not null default '';

alter table audit_event
    alter column policy_action drop default,
    alter column policy_artifact_version drop default,
    alter column policy_artifact_digest_algorithm drop default,
    alter column policy_artifact_digest_value drop default,
    alter column final_action drop default,
    alter column authorization_result drop default,
    alter column applicability_result drop default,
    alter column runtime_context_digest drop default,
    alter column matched_rule_ids drop default,
    alter column evidence_refs drop default,
    alter column required_controls drop default;

create index idx_audit_event_runtime_decision
    on audit_event (policy_action, final_action, authorization_result, applicability_result);
