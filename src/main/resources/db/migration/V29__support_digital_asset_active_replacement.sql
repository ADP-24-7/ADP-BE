alter table policy.lifecycle_artifact
    drop constraint chk_policy_lifecycle_stage;

alter table policy.lifecycle_artifact
    add constraint chk_policy_lifecycle_stage check (lifecycle_stage in (
        'DRAFT', 'VALIDATED', 'CANDIDATE', 'REPLAY', 'SHADOW',
        'APPROVED', 'ACTIVE', 'SUPERSEDED', 'REVIEW', 'ROLLED_BACK'
    ));

alter table policy.lifecycle_transition_event
    drop constraint chk_policy_transition_reason;

alter table policy.lifecycle_transition_event
    add constraint chk_policy_transition_reason check (reason_code in (
        'VALIDATION_PASSED', 'CANDIDATE_PROMOTED', 'REPLAY_PASSED', 'SHADOW_PASSED',
        'APPROVAL_GRANTED', 'ACTIVATION_APPROVED', 'ACTIVE_VERSION_SUPERSEDED',
        'REVIEW_OPENED', 'ROLLBACK_APPROVED'
    ));
