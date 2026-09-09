package com.adp.gateway.observability.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.observability.application.OperationsMonitoringPort;
import com.adp.gateway.observability.domain.OperationalMetricSnapshot;
import com.adp.gateway.observability.domain.OperationsSummary;
import com.adp.gateway.observability.domain.PolicyOperationEvent;
import com.adp.gateway.observability.domain.PolicyOperationEvent.PolicyEventCategory;
import com.adp.gateway.observability.domain.PolicyOperationEventPage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcOperationsMonitoringAdapter implements OperationsMonitoringPort {

    private final JdbcClient jdbcClient;

    public JdbcOperationsMonitoringAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public OperationsSummary loadSummary(
        String institutionId,
        Set<String> allowedWorkloads,
        OffsetDateTime windowStart,
        OffsetDateTime now,
        int windowMinutes
    ) {
        RuntimeCounts runtime = bindScope(jdbcClient.sql("""
                select count(*) as total,
                       count(*) filter (where re.status in ('COMPLETED', 'EXTERNALLY_RECONCILED')) as completed,
                       count(*) filter (where re.status = 'FAILED') as failed,
                       count(*) filter (where re.status = 'BLOCKED') as blocked,
                       count(*) filter (where re.status = 'REVIEW_REQUIRED') as review_required
                from runtime.runtime_execution re
                where re.institution_id = :institutionId and re.created_at >= :windowStart
                """ + workloadPredicate("re", allowedWorkloads)), institutionId, allowedWorkloads)
            .param("windowStart", windowStart)
            .query(RuntimeCounts.class)
            .single();

        RecoveryCounts recovery = bindScope(jdbcClient.sql("""
                select count(*) filter (
                           where r.recovery_status in ('PENDING', 'CLAIMED', 'RETRY_SCHEDULED')
                       ) as backlog,
                       cast(extract(epoch from (:now - min(r.created_at) filter (
                           where r.recovery_status in ('PENDING', 'CLAIMED', 'RETRY_SCHEDULED')
                       ))) as bigint) as oldest_backlog_age_seconds,
                       count(*) filter (where r.recovery_status = 'MANUAL_REVIEW') as manual_review,
                       count(*) filter (where r.recovery_status = 'EXHAUSTED') as exhausted
                from runtime.external_interaction_recovery r
                join runtime.runtime_execution re on re.execution_id = r.execution_id
                where re.institution_id = :institutionId
                """ + workloadPredicate("re", allowedWorkloads)), institutionId, allowedWorkloads)
            .param("now", now)
            .query(RecoveryCounts.class)
            .single();
        RecoveryOperationCounts recoveryOperations = bindOperationScope(jdbcClient.sql("""
                select count(*) as completed_operations,
                       cast(avg(extract(epoch from (e.completed_at - e.created_at)) * 1000) as bigint)
                           as average_operation_latency_millis
                from runtime.recovery_operation_event e
                where e.institution_id = :institutionId
                  and e.completed_at is not null and e.completed_at >= :windowStart
                """ + operationWorkloadPredicate(allowedWorkloads)), institutionId, allowedWorkloads)
            .param("windowStart", windowStart)
            .query(RecoveryOperationCounts.class)
            .single();

        PolicyCounts policy = bindSelectionScope(jdbcClient.sql("""
                select count(*) as current_selections,
                       count(*) filter (where la.artifact_id is null
                           or la.lifecycle_stage <> 'ACTIVE'
                           or la.artifact_digest <> cs.artifact_digest
                           or la.revision <> cs.artifact_revision) as drifted_selections
                from policy.current_selection cs
                left join policy.lifecycle_artifact la
                  on la.institution_id = cs.institution_id
                 and la.artifact_id = cs.artifact_id
                 and la.artifact_version = cs.artifact_version
                where cs.institution_id = :institutionId
                """ + selectionWorkloadPredicate(allowedWorkloads)), institutionId, allowedWorkloads)
            .query(PolicyCounts.class)
            .single();
        PolicyEventCounts policyEvents = bindSelectionScope(jdbcClient.sql("""
                select count(*) filter (where event_type = 'ACTIVATED') as activations,
                       count(*) filter (where event_type = 'ROLLED_BACK') as rollbacks
                from policy.current_selection_event cs
                where cs.institution_id = :institutionId and cs.occurred_at >= :windowStart
                """ + selectionWorkloadPredicate(allowedWorkloads)), institutionId, allowedWorkloads)
            .param("windowStart", windowStart)
            .query(PolicyEventCounts.class)
            .single();

        SecurityCounts security = bindAttemptScope(jdbcClient.sql("""
                select count(*) as denied_attempts,
                       count(*) filter (where reason_code = 'INSTITUTION_SCOPE_MISMATCH')
                           as institution_scope_mismatch,
                       count(*) filter (where reason_code = 'AUTHORIZATION_POLICY_DENIED')
                           as authorization_policy_denied
                from runtime.request_attempt ra
                where ra.institution_id = :institutionId and ra.created_at >= :windowStart
                """ + attemptWorkloadPredicate(allowedWorkloads)), institutionId, allowedWorkloads)
            .param("windowStart", windowStart)
            .query(SecurityCounts.class)
            .single();

        return new OperationsSummary(
            "adp-operations-summary/v1", windowMinutes, now,
            new OperationsSummary.RuntimeHealth(
                runtime.total(), runtime.completed(), runtime.failed(), runtime.blocked(), runtime.reviewRequired()
            ),
            new OperationsSummary.RecoveryHealth(
                recovery.backlog(), recovery.oldestBacklogAgeSeconds(), recovery.manualReview(), recovery.exhausted(),
                recoveryOperations.completedOperations(), recoveryOperations.averageOperationLatencyMillis()
            ),
            new OperationsSummary.PolicyHealth(
                policy.currentSelections(), policy.driftedSelections(),
                policyEvents.activations(), policyEvents.rollbacks()
            ),
            new OperationsSummary.SecurityHealth(
                security.deniedAttempts(), security.institutionScopeMismatch(),
                security.authorizationPolicyDenied()
            )
        );
    }

    @Override
    public PolicyOperationEventPage loadPolicyEvents(
        String institutionId,
        Set<String> allowedWorkloads,
        String workloadId,
        PolicyEventCategory category,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    ) {
        String lifecycleWhere = eventWhere("la", "lt", allowedWorkloads, workloadId, from, to,
            category == PolicyEventCategory.CURRENT_SELECTION);
        String selectionWhere = eventWhere("cs", "cs", allowedWorkloads, workloadId, from, to,
            category == PolicyEventCategory.LIFECYCLE_TRANSITION);
        String events = """
            select concat('transition:', lt.transition_id) as event_id,
                   'LIFECYCLE_TRANSITION' as category, lt.to_stage as event_type,
                   la.execution_pack, la.workload_id, la.purpose_code,
                   lt.artifact_id, lt.artifact_version, lt.artifact_digest,
                   null::varchar as previous_artifact_id, null::varchar as previous_artifact_version,
                   null::varchar as previous_artifact_digest, null::bigint as artifact_revision,
                   null::bigint as selection_revision, lt.actor_id, lt.reason_code, lt.occurred_at
            from policy.lifecycle_transition_event lt
            join policy.lifecycle_artifact la
              on la.institution_id = lt.institution_id
             and la.artifact_id = lt.artifact_id
             and la.artifact_version = lt.artifact_version
            """ + lifecycleWhere + " union all " + """
            select concat('selection:', cs.selection_event_id) as event_id,
                   'CURRENT_SELECTION' as category, cs.event_type,
                   cs.execution_pack, cs.workload_id, cs.purpose_code,
                   cs.selected_artifact_id as artifact_id,
                   cs.selected_artifact_version as artifact_version,
                   cs.selected_artifact_digest as artifact_digest,
                   cs.previous_artifact_id, cs.previous_artifact_version, cs.previous_artifact_digest,
                   cs.selected_artifact_revision as artifact_revision, cs.selection_revision,
                   cs.actor_id, cs.reason_code, cs.occurred_at
            from policy.current_selection_event cs
            """ + selectionWhere;

        JdbcClient.StatementSpec select = bindEventParameters(jdbcClient.sql("select * from (" + events
                + ") event_rows order by occurred_at desc, event_id desc limit :size offset :offset"),
            institutionId, allowedWorkloads, workloadId, from, to)
            .param("size", size)
            .param("offset", page * size);
        JdbcClient.StatementSpec count = bindEventParameters(
            jdbcClient.sql("select count(*) from (" + events + ") event_rows"),
            institutionId, allowedWorkloads, workloadId, from, to
        );
        List<PolicyOperationEvent> items = select.query((rs, rowNum) -> new PolicyOperationEvent(
            rs.getString("event_id"), PolicyEventCategory.valueOf(rs.getString("category")),
            rs.getString("event_type"), rs.getString("execution_pack"), rs.getString("workload_id"),
            rs.getString("purpose_code"), rs.getString("artifact_id"), rs.getString("artifact_version"),
            rs.getString("artifact_digest"), rs.getString("previous_artifact_id"),
            rs.getString("previous_artifact_version"), rs.getString("previous_artifact_digest"),
            rs.getObject("artifact_revision", Long.class), rs.getObject("selection_revision", Long.class),
            rs.getString("actor_id"), rs.getString("reason_code"),
            rs.getObject("occurred_at", OffsetDateTime.class)
        )).list();
        return new PolicyOperationEventPage(items, page, size, count.query(Long.class).single());
    }

    @Override
    public OperationalMetricSnapshot loadGlobalMetricSnapshot(OffsetDateTime now) {
        GlobalRecoveryCounts recovery = jdbcClient.sql("""
                select count(*) filter (
                           where recovery_status in ('PENDING', 'CLAIMED', 'RETRY_SCHEDULED')
                       ) as backlog,
                       coalesce(cast(extract(epoch from (:now - min(created_at) filter (
                           where recovery_status in ('PENDING', 'CLAIMED', 'RETRY_SCHEDULED')
                       ))) as bigint), 0) as oldest_age_seconds,
                       count(*) filter (where recovery_status = 'MANUAL_REVIEW') as manual_review,
                       count(*) filter (where recovery_status = 'EXHAUSTED') as exhausted
                from runtime.external_interaction_recovery
                """)
            .param("now", now)
            .query(GlobalRecoveryCounts.class)
            .single();
        PolicyCounts policy = jdbcClient.sql("""
                select count(*) as current_selections,
                       count(*) filter (where la.artifact_id is null
                           or la.lifecycle_stage <> 'ACTIVE'
                           or la.artifact_digest <> cs.artifact_digest
                           or la.revision <> cs.artifact_revision) as drifted_selections
                from policy.current_selection cs
                left join policy.lifecycle_artifact la
                  on la.institution_id = cs.institution_id
                 and la.artifact_id = cs.artifact_id
                 and la.artifact_version = cs.artifact_version
                """)
            .query(PolicyCounts.class)
            .single();
        return new OperationalMetricSnapshot(
            recovery.backlog(), recovery.oldestAgeSeconds(), recovery.manualReview(), recovery.exhausted(),
            policy.currentSelections(), policy.driftedSelections()
        );
    }

    private String eventWhere(
        String workloadAlias,
        String timeAlias,
        Set<String> allowedWorkloads,
        String workloadId,
        OffsetDateTime from,
        OffsetDateTime to,
        boolean exclude
    ) {
        StringBuilder where = new StringBuilder(" where ").append(timeAlias).append(".institution_id = :institutionId");
        if (exclude) where.append(" and 1 = 0");
        if (!allowedWorkloads.contains("*")) {
            where.append(allowedWorkloads.isEmpty() ? " and 1 = 0" : " and ")
                .append(allowedWorkloads.isEmpty() ? "" : workloadAlias + ".workload_id in (:allowedWorkloads)");
        }
        if (workloadId != null) where.append(" and ").append(workloadAlias).append(".workload_id = :workloadId");
        if (from != null) where.append(" and ").append(timeAlias).append(".occurred_at >= :fromAt");
        if (to != null) where.append(" and ").append(timeAlias).append(".occurred_at <= :toAt");
        return where.toString();
    }

    private JdbcClient.StatementSpec bindEventParameters(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> allowedWorkloads,
        String workloadId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        statement = statement.param("institutionId", institutionId);
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            statement = statement.param("allowedWorkloads", allowedWorkloads);
        }
        if (workloadId != null) statement = statement.param("workloadId", workloadId);
        if (from != null) statement = statement.param("fromAt", from);
        if (to != null) statement = statement.param("toAt", to);
        return statement;
    }

    private String workloadPredicate(String alias, Set<String> workloads) {
        if (workloads.contains("*")) return "";
        return workloads.isEmpty() ? " and 1 = 0" : " and " + alias + ".workload_id in (:allowedWorkloads)";
    }

    private String operationWorkloadPredicate(Set<String> workloads) {
        return workloadPredicate("e", workloads);
    }

    private String selectionWorkloadPredicate(Set<String> workloads) {
        return workloadPredicate("cs", workloads);
    }

    private String attemptWorkloadPredicate(Set<String> workloads) {
        return workloadPredicate("ra", workloads);
    }

    private JdbcClient.StatementSpec bindScope(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> workloads
    ) {
        return bindWorkloads(statement.param("institutionId", institutionId), workloads);
    }

    private JdbcClient.StatementSpec bindOperationScope(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> workloads
    ) {
        return bindScope(statement, institutionId, workloads);
    }

    private JdbcClient.StatementSpec bindSelectionScope(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> workloads
    ) {
        return bindScope(statement, institutionId, workloads);
    }

    private JdbcClient.StatementSpec bindAttemptScope(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> workloads
    ) {
        return bindScope(statement, institutionId, workloads);
    }

    private JdbcClient.StatementSpec bindWorkloads(JdbcClient.StatementSpec statement, Set<String> workloads) {
        if (!workloads.contains("*") && !workloads.isEmpty()) {
            return statement.param("allowedWorkloads", workloads);
        }
        return statement;
    }

    private record RuntimeCounts(long total, long completed, long failed, long blocked, long reviewRequired) {
    }

    private record RecoveryCounts(long backlog, Long oldestBacklogAgeSeconds, long manualReview, long exhausted) {
    }

    private record RecoveryOperationCounts(long completedOperations, Long averageOperationLatencyMillis) {
    }

    private record PolicyCounts(long currentSelections, long driftedSelections) {
    }

    private record PolicyEventCounts(long activations, long rollbacks) {
    }

    private record SecurityCounts(
        long deniedAttempts,
        long institutionScopeMismatch,
        long authorizationPolicyDenied
    ) {
    }

    private record GlobalRecoveryCounts(
        long backlog,
        long oldestAgeSeconds,
        long manualReview,
        long exhausted
    ) {
    }
}
