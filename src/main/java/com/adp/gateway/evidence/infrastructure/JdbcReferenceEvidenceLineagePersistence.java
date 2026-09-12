package com.adp.gateway.evidence.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.evidence.application.ReferenceEvidenceLineagePersistence;
import com.adp.gateway.evidence.domain.ReferenceEvidencePolicyLineage;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcReferenceEvidenceLineagePersistence implements ReferenceEvidenceLineagePersistence {
    private final JdbcClient jdbcClient;

    public JdbcReferenceEvidenceLineagePersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean bind(
        String institutionId,
        String evidenceId,
        String evidenceVersion,
        String artifactId,
        String artifactVersion,
        String sourceDigest,
        List<String> requirementRefs,
        List<String> controlRefs,
        String actorId,
        OffsetDateTime boundAt
    ) {
        int updated = jdbcClient.sql("""
                insert into evidence.reference_evidence_policy_artifact (
                    institution_id, evidence_id, evidence_version,
                    artifact_id, artifact_version, source_digest,
                    requirement_refs, control_refs, bound_by, bound_at
                )
                select e.institution_id, e.evidence_id, e.evidence_version,
                       p.artifact_id, p.artifact_version, :sourceDigest,
                       :requirementRefs, :controlRefs, :actorId, :boundAt
                from evidence.reference_evidence e
                join evidence.reference_evidence_workload ew
                  on ew.institution_id = e.institution_id
                 and ew.evidence_id = e.evidence_id
                 and ew.evidence_version = e.evidence_version
                join policy.lifecycle_artifact p
                  on p.institution_id = e.institution_id
                 and p.workload_id = ew.workload_id
                where e.institution_id = :institutionId
                  and e.evidence_id = :evidenceId
                  and e.evidence_version = :evidenceVersion
                  and p.artifact_id = :artifactId
                  and p.artifact_version = :artifactVersion
                on conflict do nothing
                """)
            .param("institutionId", institutionId)
            .param("evidenceId", evidenceId)
            .param("evidenceVersion", evidenceVersion)
            .param("artifactId", artifactId)
            .param("artifactVersion", artifactVersion)
            .param("sourceDigest", sourceDigest)
            .param("requirementRefs", requirementRefs.toArray(String[]::new))
            .param("controlRefs", controlRefs.toArray(String[]::new))
            .param("actorId", actorId)
            .param("boundAt", boundAt)
            .update();
        if (updated == 1) {
            return true;
        }
        return bindingExists(institutionId, evidenceId, evidenceVersion, artifactId, artifactVersion);
    }

    @Override
    public List<ReferenceEvidencePolicyLineage> findByPolicyArtifact(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            return List.of();
        }
        String workloadScope = allowedWorkloads.contains("*")
            ? ""
            : " and p.workload_id in (:allowedWorkloads) ";
        var spec = jdbcClient.sql("""
                select e.evidence_id, e.evidence_version, b.source_digest,
                       e.title, e.authority, e.source_ref, e.source_url,
                       e.source_locator, e.effective_from,
                       p.artifact_id, p.artifact_version, p.lifecycle_stage,
                       p.execution_pack, p.workload_id, p.purpose_code,
                       'CONNECTED' as review_status,
                       b.requirement_refs, b.control_refs,
                       b.bound_at
                from evidence.reference_evidence_policy_artifact b
                join evidence.reference_evidence e
                  on e.institution_id = b.institution_id
                 and e.evidence_id = b.evidence_id
                 and e.evidence_version = b.evidence_version
                join policy.lifecycle_artifact p
                  on p.institution_id = b.institution_id
                 and p.artifact_id = b.artifact_id
                 and p.artifact_version = b.artifact_version
                where b.institution_id = :institutionId
                  and b.artifact_id = :artifactId
                  and b.artifact_version = :artifactVersion
                """ + workloadScope + " order by e.evidence_id, e.evidence_version")
            .param("institutionId", institutionId)
            .param("artifactId", artifactId)
            .param("artifactVersion", artifactVersion);
        if (!allowedWorkloads.contains("*")) {
            spec = spec.param("allowedWorkloads", allowedWorkloads);
        }
        return spec.query(this::lineage).list();
    }

    private boolean bindingExists(
        String institutionId,
        String evidenceId,
        String evidenceVersion,
        String artifactId,
        String artifactVersion
    ) {
        return jdbcClient.sql("""
                select count(*)
                from evidence.reference_evidence_policy_artifact
                where institution_id = :institutionId
                  and evidence_id = :evidenceId and evidence_version = :evidenceVersion
                  and artifact_id = :artifactId and artifact_version = :artifactVersion
                """)
            .param("institutionId", institutionId)
            .param("evidenceId", evidenceId)
            .param("evidenceVersion", evidenceVersion)
            .param("artifactId", artifactId)
            .param("artifactVersion", artifactVersion)
            .query(Long.class)
            .single() == 1L;
    }

    private ReferenceEvidencePolicyLineage lineage(ResultSet rs, int rowNum) throws SQLException {
        return new ReferenceEvidencePolicyLineage(
            rs.getString("evidence_id"), rs.getString("evidence_version"),
            rs.getString("source_digest"), rs.getString("title"),
            rs.getString("authority"), rs.getString("source_ref"),
            rs.getString("source_url"), rs.getString("source_locator"),
            rs.getObject("effective_from", LocalDate.class), rs.getString("artifact_id"),
            rs.getString("artifact_version"),
            PolicyLifecycleStage.valueOf(rs.getString("lifecycle_stage")),
            ExecutionPackType.valueOf(rs.getString("execution_pack")),
            rs.getString("workload_id"), rs.getString("purpose_code"),
            rs.getString("review_status"),
            stringList(rs, "requirement_refs"), stringList(rs, "control_refs"),
            rs.getObject("bound_at", OffsetDateTime.class)
        );
    }

    private List<String> stringList(ResultSet rs, String column) throws SQLException {
        Object[] values = (Object[]) rs.getArray(column).getArray();
        return java.util.Arrays.stream(values).map(String::valueOf).toList();
    }
}
