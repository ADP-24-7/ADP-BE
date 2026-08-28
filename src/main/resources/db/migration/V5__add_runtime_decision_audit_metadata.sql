alter table audit_event
    add column policy_action varchar(40) not null default 'ALLOW',
    add column final_action varchar(40) not null default 'ALLOW',
    add column authorization_result varchar(40) not null default 'ALLOWED',
    add column applicability_result varchar(40) not null default 'APPLICABLE',
    add column matched_rule_ids varchar(2000) not null default '';

alter table audit_event
    alter column policy_action drop default,
    alter column final_action drop default,
    alter column authorization_result drop default,
    alter column applicability_result drop default,
    alter column matched_rule_ids drop default;

create index idx_audit_event_runtime_decision
    on audit_event (policy_action, final_action, authorization_result, applicability_result);
