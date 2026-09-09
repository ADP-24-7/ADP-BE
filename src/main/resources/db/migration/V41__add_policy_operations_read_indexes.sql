create index idx_policy_lifecycle_operations_search
    on policy.lifecycle_artifact (
        institution_id, execution_pack, workload_id, lifecycle_stage,
        updated_at desc, artifact_id, artifact_version
    );

create index idx_policy_shadow_candidate_history
    on policy.shadow_evaluation_evidence (
        institution_id, candidate_artifact_id, candidate_artifact_version,
        evaluated_at desc, shadow_evaluation_id
    );
