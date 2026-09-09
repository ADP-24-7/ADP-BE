package com.adp.gateway.evidence.infrastructure;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.evidence.application.ReferenceEvidenceException;
import com.adp.gateway.evidence.application.ReferenceEvidencePersistence;
import com.adp.gateway.evidence.application.ValidatedReferenceEvidenceBundle;
import com.adp.gateway.evidence.domain.ReferenceEvidence;
import com.adp.gateway.evidence.domain.ReferenceEvidenceBundleReceipt;
import com.adp.gateway.evidence.domain.ReferenceEvidencePage;
import com.adp.gateway.evidence.domain.ReferenceEvidenceStatus;
import com.adp.gateway.evidence.domain.ReferenceEvidenceType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcReferenceEvidencePersistence implements ReferenceEvidencePersistence {
    private static final String SELECT_EVIDENCE = """
        select e.evidence_id, e.evidence_version, e.bundle_id, e.bundle_version,
               e.evidence_type, e.authority, e.title, e.source_ref, e.source_url,
               e.source_date, e.effective_from, e.effective_to, e.claim_scope,
               e.claim_summary, e.source_locator, e.analysis_version, e.status,
               e.content_digest, e.created_at,
               array(select w.workload_id from evidence.reference_evidence_workload w
                     where w.institution_id = e.institution_id
                       and w.evidence_id = e.evidence_id
                       and w.evidence_version = e.evidence_version
                     order by w.workload_id) as workload_refs,
               array(select p.policy_artifact_ref from evidence.reference_evidence_policy_artifact p
                     where p.institution_id = e.institution_id
                       and p.evidence_id = e.evidence_id
                       and p.evidence_version = e.evidence_version
                     order by p.policy_artifact_ref) as policy_artifact_refs
        from evidence.reference_evidence e
        """;

    private final JdbcClient jdbcClient;

    public JdbcReferenceEvidencePersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void lockBundle(String institutionId, String bundleId, String bundleVersion) {
        jdbcClient.sql("""
                select 1
                from (select pg_advisory_xact_lock(hashtextextended(:identity, 0))) acquired
                """)
            .param("identity", institutionId + "\u001f" + bundleId + "\u001f" + bundleVersion)
            .query(Integer.class)
            .single();
    }

    @Override
    public Optional<ReferenceEvidenceBundleReceipt> findBundle(
        String institutionId, String bundleId, String bundleVersion
    ) {
        return jdbcClient.sql("""
                select bundle_id, bundle_version, schema_version, analysis_version,
                       content_digest, evidence_count, snapshot_at, ingested_at
                from evidence.reference_evidence_bundle
                where institution_id = :institutionId and bundle_id = :bundleId
                  and bundle_version = :bundleVersion
                """)
            .param("institutionId", institutionId)
            .param("bundleId", bundleId)
            .param("bundleVersion", bundleVersion)
            .query((rs, rowNum) -> bundleReceipt(rs, false))
            .optional();
    }

    @Override
    public ReferenceEvidenceBundleReceipt create(
        String institutionId,
        String actorId,
        ValidatedReferenceEvidenceBundle bundle,
        OffsetDateTime ingestedAt
    ) {
        try {
            jdbcClient.sql("""
                    insert into evidence.reference_evidence_bundle (
                        institution_id, bundle_id, bundle_version, schema_version,
                        analysis_version, content_digest, snapshot_at, evidence_count,
                        ingested_by, ingested_at
                    ) values (
                        :institutionId, :bundleId, :bundleVersion, :schemaVersion,
                        :analysisVersion, :contentDigest, :snapshotAt, :evidenceCount,
                        :actorId, :ingestedAt
                    )
                    """)
                .param("institutionId", institutionId)
                .param("bundleId", bundle.bundleId())
                .param("bundleVersion", bundle.bundleVersion())
                .param("schemaVersion", bundle.schemaVersion())
                .param("analysisVersion", bundle.analysisVersion())
                .param("contentDigest", bundle.contentDigest())
                .param("snapshotAt", bundle.snapshotAt())
                .param("evidenceCount", bundle.evidence().size())
                .param("actorId", actorId)
                .param("ingestedAt", ingestedAt)
                .update();
            for (ValidatedReferenceEvidenceBundle.Item item : bundle.evidence()) {
                insertEvidence(institutionId, bundle, item, ingestedAt);
                item.workloadRefs().forEach(workload -> insertWorkload(institutionId, item, workload));
                item.policyArtifactRefs().forEach(policy -> insertPolicy(institutionId, item, policy));
            }
            return new ReferenceEvidenceBundleReceipt(
                bundle.bundleId(), bundle.bundleVersion(), bundle.schemaVersion(),
                bundle.analysisVersion(), bundle.contentDigest(), bundle.evidence().size(),
                bundle.snapshotAt(), ingestedAt, false
            );
        } catch (DuplicateKeyException exception) {
            throw new ReferenceEvidenceException("REFERENCE_EVIDENCE_CONFLICT", exception);
        }
    }

    @Override
    public ReferenceEvidencePage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ReferenceEvidenceType evidenceType,
        ReferenceEvidenceStatus status,
        String workloadId,
        String policyArtifactRef,
        String query,
        int limit,
        int offset
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            return new ReferenceEvidencePage(List.of(), 0, limit, offset);
        }
        QueryParts parts = queryParts(
            institutionId, allowedWorkloads, evidenceType, status,
            workloadId, policyArtifactRef, query
        );
        long total = bind(jdbcClient.sql("select count(*) from evidence.reference_evidence e " + parts.where()),
            parts).query(Long.class).single();
        var spec = bind(jdbcClient.sql(SELECT_EVIDENCE + parts.where()
            + " order by e.created_at desc, e.evidence_id, e.evidence_version limit :limit offset :offset"), parts)
            .param("limit", limit)
            .param("offset", offset);
        List<ReferenceEvidence> items = spec.query(this::evidence).list();
        return new ReferenceEvidencePage(items, total, limit, offset);
    }

    @Override
    public Optional<ReferenceEvidence> find(
        String institutionId,
        Set<String> allowedWorkloads,
        String evidenceId,
        String evidenceVersion
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            return Optional.empty();
        }
        String scope = allowedWorkloads.contains("*") ? "" : """
            and (
                not exists (
                    select 1 from evidence.reference_evidence_workload scope_any
                    where scope_any.institution_id = e.institution_id
                      and scope_any.evidence_id = e.evidence_id
                      and scope_any.evidence_version = e.evidence_version
                )
                or exists (
                    select 1 from evidence.reference_evidence_workload scope_allowed
                    where scope_allowed.institution_id = e.institution_id
                      and scope_allowed.evidence_id = e.evidence_id
                      and scope_allowed.evidence_version = e.evidence_version
                      and scope_allowed.workload_id in (:allowedWorkloads)
                )
            )
            """;
        var spec = jdbcClient.sql(SELECT_EVIDENCE + """
                where e.institution_id = :institutionId
                  and e.evidence_id = :evidenceId and e.evidence_version = :evidenceVersion
                """ + scope)
            .param("institutionId", institutionId)
            .param("evidenceId", evidenceId)
            .param("evidenceVersion", evidenceVersion);
        if (!allowedWorkloads.contains("*")) {
            spec = spec.param("allowedWorkloads", allowedWorkloads);
        }
        return spec.query(this::evidence).optional();
    }

    private void insertEvidence(
        String institutionId,
        ValidatedReferenceEvidenceBundle bundle,
        ValidatedReferenceEvidenceBundle.Item item,
        OffsetDateTime createdAt
    ) {
        jdbcClient.sql("""
                insert into evidence.reference_evidence (
                    institution_id, evidence_id, evidence_version, bundle_id, bundle_version,
                    evidence_type, authority, title, source_ref, source_url, source_date,
                    effective_from, effective_to, claim_scope, claim_summary, source_locator,
                    analysis_version, status, content_digest, created_at
                ) values (
                    :institutionId, :evidenceId, :evidenceVersion, :bundleId, :bundleVersion,
                    :evidenceType, :authority, :title, :sourceRef, :sourceUrl, :sourceDate,
                    :effectiveFrom, :effectiveTo, :claimScope, :claimSummary, :sourceLocator,
                    :analysisVersion, :status, :contentDigest, :createdAt
                )
                """)
            .param("institutionId", institutionId)
            .param("evidenceId", item.evidenceId())
            .param("evidenceVersion", item.evidenceVersion())
            .param("bundleId", bundle.bundleId())
            .param("bundleVersion", bundle.bundleVersion())
            .param("evidenceType", item.evidenceType().name())
            .param("authority", item.authority())
            .param("title", item.title())
            .param("sourceRef", item.sourceRef())
            .param("sourceUrl", item.sourceUrl(), java.sql.Types.VARCHAR)
            .param("sourceDate", item.sourceDate(), java.sql.Types.DATE)
            .param("effectiveFrom", item.effectiveFrom(), java.sql.Types.DATE)
            .param("effectiveTo", item.effectiveTo(), java.sql.Types.DATE)
            .param("claimScope", item.claimScope())
            .param("claimSummary", item.claimSummary())
            .param("sourceLocator", item.sourceLocator())
            .param("analysisVersion", item.analysisVersion())
            .param("status", item.status().name())
            .param("contentDigest", item.contentDigest())
            .param("createdAt", createdAt)
            .update();
    }

    private void insertWorkload(
        String institutionId, ValidatedReferenceEvidenceBundle.Item item, String workload
    ) {
        jdbcClient.sql("""
                insert into evidence.reference_evidence_workload (
                    institution_id, evidence_id, evidence_version, workload_id
                ) values (:institutionId, :evidenceId, :evidenceVersion, :workload)
                """)
            .param("institutionId", institutionId)
            .param("evidenceId", item.evidenceId())
            .param("evidenceVersion", item.evidenceVersion())
            .param("workload", workload)
            .update();
    }

    private void insertPolicy(
        String institutionId, ValidatedReferenceEvidenceBundle.Item item, String policy
    ) {
        jdbcClient.sql("""
                insert into evidence.reference_evidence_policy_artifact (
                    institution_id, evidence_id, evidence_version, policy_artifact_ref
                ) values (:institutionId, :evidenceId, :evidenceVersion, :policy)
                """)
            .param("institutionId", institutionId)
            .param("evidenceId", item.evidenceId())
            .param("evidenceVersion", item.evidenceVersion())
            .param("policy", policy)
            .update();
    }

    private QueryParts queryParts(
        String institutionId,
        Set<String> allowedWorkloads,
        ReferenceEvidenceType evidenceType,
        ReferenceEvidenceStatus status,
        String workloadId,
        String policyArtifactRef,
        String query
    ) {
        StringBuilder where = new StringBuilder(" where e.institution_id = :institutionId ");
        if (!allowedWorkloads.contains("*")) {
            where.append("""
                and (
                    not exists (select 1 from evidence.reference_evidence_workload scope_any
                        where scope_any.institution_id = e.institution_id
                          and scope_any.evidence_id = e.evidence_id
                          and scope_any.evidence_version = e.evidence_version)
                    or exists (select 1 from evidence.reference_evidence_workload scope_allowed
                        where scope_allowed.institution_id = e.institution_id
                          and scope_allowed.evidence_id = e.evidence_id
                          and scope_allowed.evidence_version = e.evidence_version
                          and scope_allowed.workload_id in (:allowedWorkloads))
                )
                """);
        }
        if (evidenceType != null) {
            where.append(" and e.evidence_type = :evidenceType ");
        }
        if (status != null) {
            where.append(" and e.status = :status ");
        }
        if (workloadId != null) {
            where.append("""
                and exists (select 1 from evidence.reference_evidence_workload requested_workload
                    where requested_workload.institution_id = e.institution_id
                      and requested_workload.evidence_id = e.evidence_id
                      and requested_workload.evidence_version = e.evidence_version
                      and requested_workload.workload_id = :workloadId)
                """).append(" ");
        }
        if (policyArtifactRef != null) {
            where.append("""
                and exists (select 1 from evidence.reference_evidence_policy_artifact requested_policy
                    where requested_policy.institution_id = e.institution_id
                      and requested_policy.evidence_id = e.evidence_id
                      and requested_policy.evidence_version = e.evidence_version
                      and requested_policy.policy_artifact_ref = :policyArtifactRef)
                """).append(" ");
        }
        if (query != null) {
            where.append(" and (lower(e.evidence_id) like :query or lower(e.title) like :query"
                + " or lower(e.authority) like :query)");
        }
        return new QueryParts(
            where.toString(), institutionId, allowedWorkloads, evidenceType, status,
            workloadId, policyArtifactRef, query
        );
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, QueryParts parts) {
        spec = spec.param("institutionId", parts.institutionId());
        if (!parts.allowedWorkloads().contains("*")) {
            spec = spec.param("allowedWorkloads", parts.allowedWorkloads());
        }
        if (parts.evidenceType() != null) {
            spec = spec.param("evidenceType", parts.evidenceType().name());
        }
        if (parts.status() != null) {
            spec = spec.param("status", parts.status().name());
        }
        if (parts.workloadId() != null) {
            spec = spec.param("workloadId", parts.workloadId());
        }
        if (parts.policyArtifactRef() != null) {
            spec = spec.param("policyArtifactRef", parts.policyArtifactRef());
        }
        if (parts.query() != null) {
            spec = spec.param("query", "%" + parts.query().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        return spec;
    }

    private ReferenceEvidence evidence(ResultSet rs, int rowNum) throws SQLException {
        return new ReferenceEvidence(
            rs.getString("evidence_id"), rs.getString("evidence_version"),
            rs.getString("bundle_id"), rs.getString("bundle_version"),
            ReferenceEvidenceType.valueOf(rs.getString("evidence_type")),
            rs.getString("authority"), rs.getString("title"), rs.getString("source_ref"),
            rs.getString("source_url"), rs.getObject("source_date", LocalDate.class),
            rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
            rs.getString("claim_scope"), rs.getString("claim_summary"), rs.getString("source_locator"),
            rs.getString("analysis_version"), ReferenceEvidenceStatus.valueOf(rs.getString("status")),
            strings(rs.getArray("workload_refs")), strings(rs.getArray("policy_artifact_refs")),
            rs.getString("content_digest"), rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((String[]) array.getArray());
    }

    private ReferenceEvidenceBundleReceipt bundleReceipt(ResultSet rs, boolean replayed) throws SQLException {
        return new ReferenceEvidenceBundleReceipt(
            rs.getString("bundle_id"), rs.getString("bundle_version"), rs.getString("schema_version"),
            rs.getString("analysis_version"), rs.getString("content_digest"), rs.getInt("evidence_count"),
            rs.getObject("snapshot_at", OffsetDateTime.class), rs.getObject("ingested_at", OffsetDateTime.class),
            replayed
        );
    }

    private record QueryParts(
        String where,
        String institutionId,
        Set<String> allowedWorkloads,
        ReferenceEvidenceType evidenceType,
        ReferenceEvidenceStatus status,
        String workloadId,
        String policyArtifactRef,
        String query
    ) {
    }
}
