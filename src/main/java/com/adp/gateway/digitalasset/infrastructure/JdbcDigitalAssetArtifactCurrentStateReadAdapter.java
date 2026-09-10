package com.adp.gateway.digitalasset.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactCurrentStateReadPort;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStateDetail;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStateItem;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStatePage;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactRuntimeEvidence;
import com.adp.gateway.digitalasset.domain.DigitalAssetCurrentSelectionStatus;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcDigitalAssetArtifactCurrentStateReadAdapter
    implements DigitalAssetArtifactCurrentStateReadPort {

    private static final String CURRENT_SELECTION_EXPRESSION = """
        case
            when active.artifact_id is not null
              and active.artifact_digest = ingestion.artifact_digest
              and active.purpose_code = ingestion.purpose_code
              and lifecycle.lifecycle_stage = 'ACTIVE'
                then 'CURRENT'
            when active.artifact_id is not null or lifecycle.lifecycle_stage = 'ACTIVE'
                then 'INCONSISTENT'
            else 'NOT_CURRENT'
        end
        """;

    private final JdbcClient jdbcClient;

    public JdbcDigitalAssetArtifactCurrentStateReadAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public DigitalAssetArtifactCurrentStatePage search(
        String institutionId,
        Set<String> allowedWorkloads,
        PolicyLifecycleStage lifecycleStage,
        String workloadId,
        String query,
        boolean currentOnly,
        int page,
        int size
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            return new DigitalAssetArtifactCurrentStatePage(List.of(), page, size, 0);
        }
        StringBuilder where = new StringBuilder(" where ingestion.institution_id = :institutionId");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("institutionId", institutionId);
        appendWorkloadScope(where, parameters, allowedWorkloads);
        if (lifecycleStage != null) {
            where.append(" and lifecycle.lifecycle_stage = :lifecycleStage");
            parameters.put("lifecycleStage", lifecycleStage.name());
        }
        if (workloadId != null) {
            where.append(" and ingestion.workload_id = :workloadId");
            parameters.put("workloadId", workloadId);
        }
        if (query != null) {
            where.append("""
                 and (
                    lower(ingestion.artifact_id) like :query
                    or lower(ingestion.artifact_version) like :query
                    or lower(ingestion.workload_id) like :query
                    or lower(ingestion.purpose_code) like :query
                    or lower(ingestion.destination_profile_id) like :query
                 )
                """);
            parameters.put("query", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (currentOnly) {
            where.append(" and (").append(CURRENT_SELECTION_EXPRESSION).append(") = 'CURRENT'");
        }

        long total = jdbcClient.sql("select count(*) " + baseFromClause() + where)
            .params(parameters)
            .query(Long.class)
            .single();
        Map<String, Object> pageParameters = new HashMap<>(parameters);
        pageParameters.put("size", size);
        pageParameters.put("offset", page * size);
        List<DigitalAssetArtifactCurrentStateItem> items = jdbcClient.sql("""
                select ingestion.artifact_id, ingestion.artifact_version, ingestion.artifact_digest,
                       ingestion.workload_id, ingestion.purpose_code, ingestion.destination_profile_id,
                       lifecycle.lifecycle_stage, lifecycle.revision,
                """ + CURRENT_SELECTION_EXPRESSION + """
                       as current_selection_status,
                       (select count(*)
                          from runtime.digital_asset_runtime_snapshot counted
                         where counted.institution_id = ingestion.institution_id
                           and counted.artifact_id = ingestion.artifact_id
                           and counted.artifact_version = ingestion.artifact_version
                       ) as runtime_execution_count,
                       latest.execution_id as latest_execution_id,
                       latest.runtime_status as latest_runtime_status,
                       active.activated_at, ingestion.ingested_at, lifecycle.updated_at
                """ + listFromClause() + where + """

                order by
                    case when active.artifact_id is not null then 0 else 1 end,
                    lifecycle.updated_at desc, ingestion.artifact_id, ingestion.artifact_version
                limit :size offset :offset
                """)
            .params(pageParameters)
            .query((rs, rowNum) -> item(rs))
            .list();
        return new DigitalAssetArtifactCurrentStatePage(items, page, size, total);
    }

    @Override
    public DigitalAssetArtifactCurrentStateDetail load(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            throw notFound();
        }
        StringBuilder scope = new StringBuilder(" where ingestion.institution_id = :institutionId")
            .append(" and ingestion.artifact_id = :artifactId")
            .append(" and ingestion.artifact_version = :artifactVersion");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("institutionId", institutionId);
        parameters.put("artifactId", artifactId);
        parameters.put("artifactVersion", artifactVersion);
        appendWorkloadScope(scope, parameters, allowedWorkloads);

        return jdbcClient.sql("""
                select ingestion.artifact_id, ingestion.artifact_version, ingestion.artifact_digest,
                       ingestion.workload_id, ingestion.purpose_code, ingestion.destination_profile_id,
                       lifecycle.lifecycle_stage, lifecycle.revision,
                """ + CURRENT_SELECTION_EXPRESSION + """
                       as current_selection_status,
                       (select count(*)
                          from runtime.digital_asset_runtime_snapshot counted
                         where counted.institution_id = ingestion.institution_id
                           and counted.artifact_id = ingestion.artifact_id
                           and counted.artifact_version = ingestion.artifact_version
                       ) as runtime_execution_count,
                       latest.execution_id as latest_execution_id,
                       latest.runtime_status as latest_runtime_status,
                       active.activated_at, ingestion.ingested_at, lifecycle.updated_at,
                       ingestion.manifest_schema_version, ingestion.manifest_reference,
                       ingestion.canonical_contract_version, ingestion.canonical_contract_digest,
                       ingestion.runtime_control_version, ingestion.runtime_control_digest,
                       ingestion.crosswalk_version, ingestion.crosswalk_digest, ingestion.file_count,
                       lifecycle.created_by, lifecycle.created_at, ingestion.ingested_by,
                       active.institution_id as active_institution_id,
                       active.purpose_code as active_purpose_code,
                       active.artifact_digest as active_artifact_digest, active.activated_by,
                       latest.request_id, latest.trace_id, latest.final_action,
                       latest.snapshot_id, latest.snapshot_digest,
                       latest.approved_policy_snapshot_id, latest.approved_policy_version,
                       latest.approved_policy_digest, latest.snapshot_destination_profile_id,
                       latest.destination_profile_version, latest.destination_profile_digest,
                       latest.selected_at, latest.post_execution_status, latest.external_status,
                       latest.provider_status, latest.receipt_status, latest.finality_status,
                       latest.observed_at
                """ + listFromClause() + scope)
            .params(parameters)
            .query((rs, rowNum) -> detail(rs))
            .optional()
            .orElseThrow(this::notFound);
    }

    private String baseFromClause() {
        return """
            from policy.digital_asset_artifact_ingestion ingestion
            join policy.lifecycle_artifact lifecycle
              on lifecycle.institution_id = ingestion.institution_id
             and lifecycle.artifact_id = ingestion.artifact_id
             and lifecycle.artifact_version = ingestion.artifact_version
             and lifecycle.execution_pack = 'DIGITAL_ASSET'
            left join policy.digital_asset_active_artifact active
              on active.institution_id = ingestion.institution_id
             and active.workload_id = ingestion.workload_id
             and active.artifact_id = ingestion.artifact_id
             and active.artifact_version = ingestion.artifact_version
            """;
    }

    private String listFromClause() {
        return baseFromClause() + """
            left join lateral (
                select snapshot.execution_id, execution.request_id, execution.trace_id,
                       execution.status as runtime_status, execution.final_action,
                       snapshot.snapshot_id, snapshot.snapshot_digest,
                       snapshot.approved_policy_snapshot_id, snapshot.approved_policy_version,
                       snapshot.approved_policy_digest,
                       snapshot.destination_profile_id as snapshot_destination_profile_id,
                       snapshot.destination_profile_version, snapshot.destination_profile_digest,
                       snapshot.selected_at,
                       evidence.status as post_execution_status,
                       evidence.external_status, evidence.provider_status,
                       evidence.receipt_status, evidence.finality_status, evidence.observed_at
                from runtime.digital_asset_runtime_snapshot snapshot
                join runtime.runtime_execution execution
                  on execution.execution_id = snapshot.execution_id
                left join runtime.digital_asset_post_execution_evidence evidence
                  on evidence.execution_id = snapshot.execution_id
                where snapshot.institution_id = ingestion.institution_id
                  and snapshot.artifact_id = ingestion.artifact_id
                  and snapshot.artifact_version = ingestion.artifact_version
                order by snapshot.selected_at desc, snapshot.snapshot_id desc
                limit 1
            ) latest on true
            """;
    }

    private void appendWorkloadScope(
        StringBuilder sql,
        Map<String, Object> parameters,
        Set<String> allowedWorkloads
    ) {
        if (!allowedWorkloads.contains("*")) {
            sql.append(" and ingestion.workload_id in (:allowedWorkloads)");
            parameters.put("allowedWorkloads", allowedWorkloads);
        }
    }

    private DigitalAssetArtifactCurrentStateItem item(ResultSet rs) throws SQLException {
        return new DigitalAssetArtifactCurrentStateItem(
            rs.getString("artifact_id"), rs.getString("artifact_version"), rs.getString("artifact_digest"),
            rs.getString("workload_id"), rs.getString("purpose_code"), rs.getString("destination_profile_id"),
            PolicyLifecycleStage.valueOf(rs.getString("lifecycle_stage")), rs.getLong("revision"),
            DigitalAssetCurrentSelectionStatus.valueOf(rs.getString("current_selection_status")),
            rs.getLong("runtime_execution_count"), rs.getString("latest_execution_id"),
            rs.getString("latest_runtime_status"), rs.getObject("activated_at", OffsetDateTime.class),
            rs.getObject("ingested_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private DigitalAssetArtifactCurrentStateDetail detail(ResultSet rs) throws SQLException {
        DigitalAssetArtifactCurrentStateItem artifact = item(rs);
        DigitalAssetActiveArtifact active = rs.getString("active_institution_id") == null ? null
            : new DigitalAssetActiveArtifact(
                rs.getString("active_institution_id"), rs.getString("workload_id"),
                rs.getString("active_purpose_code"), rs.getString("artifact_id"),
                rs.getString("artifact_version"), rs.getString("active_artifact_digest"),
                rs.getString("destination_profile_id"), rs.getString("runtime_control_version"),
                rs.getString("runtime_control_digest"), rs.getString("crosswalk_version"),
                rs.getString("crosswalk_digest"), rs.getString("activated_by"),
                rs.getObject("activated_at", OffsetDateTime.class)
            );
        DigitalAssetArtifactRuntimeEvidence runtime = rs.getString("latest_execution_id") == null ? null
            : new DigitalAssetArtifactRuntimeEvidence(
                rs.getString("latest_execution_id"), rs.getString("request_id"), rs.getString("trace_id"),
                rs.getString("latest_runtime_status"), rs.getString("final_action"),
                rs.getString("snapshot_id"), rs.getString("snapshot_digest"),
                rs.getString("approved_policy_snapshot_id"), rs.getString("approved_policy_version"),
                rs.getString("approved_policy_digest"), rs.getString("snapshot_destination_profile_id"),
                rs.getString("destination_profile_version"), rs.getString("destination_profile_digest"),
                rs.getString("post_execution_status"), rs.getString("external_status"),
                rs.getString("provider_status"), rs.getString("receipt_status"),
                rs.getString("finality_status"), rs.getObject("selected_at", OffsetDateTime.class),
                rs.getObject("observed_at", OffsetDateTime.class),
                "/v1/runtime/executions/" + rs.getString("latest_execution_id") + "/trace",
                "/api/admin/audit/executions/" + rs.getString("latest_execution_id") + "/evidence"
            );
        return new DigitalAssetArtifactCurrentStateDetail(
            artifact, rs.getString("manifest_schema_version"), rs.getString("manifest_reference"),
            rs.getString("canonical_contract_version"), rs.getString("canonical_contract_digest"),
            rs.getString("runtime_control_version"), rs.getString("runtime_control_digest"),
            rs.getString("crosswalk_version"), rs.getString("crosswalk_digest"), rs.getInt("file_count"),
            rs.getString("created_by"), rs.getString("ingested_by"),
            rs.getObject("created_at", OffsetDateTime.class), active, runtime,
            "/api/admin/policy-lifecycle/" + rs.getString("artifact_id")
                + "/versions/" + rs.getString("artifact_version") + "/history"
        );
    }

    private DigitalAssetArtifactIngestionException notFound() {
        return new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_NOT_FOUND");
    }
}
