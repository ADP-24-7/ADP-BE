alter table policy.shadow_evaluation_evidence
    add constraint uq_policy_shadow_approval_binding unique (
        shadow_evaluation_id, institution_id,
        candidate_artifact_id, candidate_artifact_version, candidate_artifact_digest, candidate_revision,
        baseline_artifact_id, baseline_artifact_version, baseline_artifact_digest,
        evaluation_case_id, evaluation_case_version, result
    );

alter table policy.lifecycle_transition_event
    add column approval_gate_version varchar(40),
    add column shadow_evaluation_id varchar(80),
    add column shadow_candidate_revision bigint,
    add column shadow_baseline_artifact_id varchar(120),
    add column shadow_baseline_artifact_version varchar(120),
    add column shadow_baseline_artifact_digest varchar(64),
    add column shadow_evaluation_case_id varchar(120),
    add column shadow_evaluation_case_version varchar(120),
    add column shadow_result varchar(16),
    add column approval_policy_version varchar(120);

update policy.lifecycle_transition_event
set approval_gate_version = case
    when to_stage = 'APPROVED' then 'LEGACY_UNBOUND'
    else 'NOT_APPLICABLE'
end;

alter table policy.lifecycle_transition_event
    alter column approval_gate_version set not null,
    add constraint fk_policy_transition_shadow_approval foreign key (
        shadow_evaluation_id, institution_id,
        artifact_id, artifact_version, artifact_digest, shadow_candidate_revision,
        shadow_baseline_artifact_id, shadow_baseline_artifact_version,
        shadow_baseline_artifact_digest, shadow_evaluation_case_id,
        shadow_evaluation_case_version, shadow_result
    ) references policy.shadow_evaluation_evidence (
        shadow_evaluation_id, institution_id,
        candidate_artifact_id, candidate_artifact_version, candidate_artifact_digest, candidate_revision,
        baseline_artifact_id, baseline_artifact_version,
        baseline_artifact_digest, evaluation_case_id, evaluation_case_version, result
    ),
    add constraint chk_policy_transition_approval_binding check (
        (
            to_stage <> 'APPROVED'
            and approval_gate_version = 'NOT_APPLICABLE'
            and shadow_evaluation_id is null
            and shadow_candidate_revision is null
            and shadow_baseline_artifact_id is null
            and shadow_baseline_artifact_version is null
            and shadow_baseline_artifact_digest is null
            and shadow_evaluation_case_id is null
            and shadow_evaluation_case_version is null
            and shadow_result is null
            and approval_policy_version is null
        ) or (
            to_stage = 'APPROVED'
            and approval_gate_version = 'LEGACY_UNBOUND'
            and shadow_evaluation_id is null
            and shadow_candidate_revision is null
            and shadow_baseline_artifact_id is null
            and shadow_baseline_artifact_version is null
            and shadow_baseline_artifact_digest is null
            and shadow_evaluation_case_id is null
            and shadow_evaluation_case_version is null
            and shadow_result is null
            and approval_policy_version is null
        ) or (
            to_stage = 'APPROVED'
            and approval_gate_version = 'SHADOW_EVIDENCE_V1'
            and shadow_evaluation_id is not null
            and shadow_candidate_revision is not null
            and shadow_baseline_artifact_id is not null
            and shadow_baseline_artifact_version is not null
            and shadow_baseline_artifact_digest is not null
            and shadow_evaluation_case_id is not null
            and shadow_evaluation_case_version is not null
            and shadow_result is not null
            and approval_policy_version is not null
        )
    );

create index idx_policy_transition_shadow_evidence
    on policy.lifecycle_transition_event (shadow_evaluation_id)
    where shadow_evaluation_id is not null;
