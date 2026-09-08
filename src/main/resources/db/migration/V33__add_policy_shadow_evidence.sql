alter table policy.lifecycle_artifact
    add constraint uq_policy_lifecycle_shadow_binding unique (
        institution_id, artifact_id, artifact_version, artifact_digest, workload_id, purpose_code
    );

create table policy.shadow_evaluation_evidence (
    shadow_evaluation_id varchar(80) primary key,
    institution_id varchar(120) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(120) not null,
    baseline_artifact_id varchar(120) not null,
    baseline_artifact_version varchar(120) not null,
    baseline_artifact_digest varchar(64) not null,
    candidate_artifact_id varchar(120) not null,
    candidate_artifact_version varchar(120) not null,
    candidate_artifact_digest varchar(64) not null,
    candidate_revision bigint not null,
    evaluation_case_id varchar(120) not null,
    evaluation_case_version varchar(120) not null,
    input_digest varchar(64) not null,
    baseline_outcome_digest varchar(64) not null,
    candidate_outcome_digest varchar(64) not null,
    diff_fields jsonb not null,
    result varchar(16) not null,
    evaluated_by varchar(120) not null,
    evaluated_at timestamptz not null,
    foreign key (
        institution_id, baseline_artifact_id, baseline_artifact_version,
        baseline_artifact_digest, workload_id, purpose_code
    ) references policy.lifecycle_artifact (
        institution_id, artifact_id, artifact_version, artifact_digest, workload_id, purpose_code
    ),
    foreign key (
        institution_id, candidate_artifact_id, candidate_artifact_version,
        candidate_artifact_digest, workload_id, purpose_code
    ) references policy.lifecycle_artifact (
        institution_id, artifact_id, artifact_version, artifact_digest, workload_id, purpose_code
    ),
    constraint uq_policy_shadow_candidate_case_revision unique (
        institution_id, candidate_artifact_id, candidate_artifact_version,
        candidate_revision, evaluation_case_id, evaluation_case_version
    ),
    constraint chk_policy_shadow_digests check (
        baseline_artifact_digest ~ '^[0-9a-f]{64}$'
        and candidate_artifact_digest ~ '^[0-9a-f]{64}$'
        and input_digest ~ '^[0-9a-f]{64}$'
        and baseline_outcome_digest ~ '^[0-9a-f]{64}$'
        and candidate_outcome_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint chk_policy_shadow_diff_fields check (jsonb_typeof(diff_fields) = 'array'),
    constraint chk_policy_shadow_result check (result in ('MATCH', 'DIFF')),
    constraint chk_policy_shadow_result_consistency check (
        (result = 'MATCH' and diff_fields = '[]'::jsonb)
        or (result = 'DIFF' and jsonb_array_length(diff_fields) > 0)
    )
);

create index idx_policy_shadow_scope
    on policy.shadow_evaluation_evidence (
        institution_id, workload_id, candidate_artifact_id, candidate_artifact_version, evaluated_at desc
    );
