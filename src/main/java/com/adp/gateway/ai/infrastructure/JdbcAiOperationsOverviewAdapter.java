package com.adp.gateway.ai.infrastructure;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.adp.gateway.ai.application.AiOperationsOverviewPort;
import com.adp.gateway.ai.domain.AiOverviewDataProtectionPolicy;
import com.adp.gateway.ai.domain.AiOperationsOverview;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcAiOperationsOverviewAdapter implements AiOperationsOverviewPort {
    private final JdbcClient jdbcClient;

    public JdbcAiOperationsOverviewAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public AiOperationsOverview load(
        String institutionId,
        Set<String> allowedWorkloads,
        OffsetDateTime from,
        OffsetDateTime to,
        OffsetDateTime generatedAt,
        String executionQuery,
        String executionStatus,
        int executionPage,
        int executionSize
    ) {
        OffsetDateTime previousFrom = from.minus(Duration.between(from, to));
        Counts current = counts(institutionId, allowedWorkloads, from, to);
        Counts previous = counts(institutionId, allowedWorkloads, previousFrom, from);
        return new AiOperationsOverview(
            "adp-ai-operations-overview/v1", generatedAt, from, to,
            metrics(current, previous), rates(current, previous),
            flow(institutionId, allowedWorkloads, from, to),
            trend(institutionId, allowedWorkloads, from, to),
            dataClassControls(institutionId, allowedWorkloads, from, to),
            latency(institutionId, allowedWorkloads, from, to),
            hourlyStatuses(institutionId, allowedWorkloads, from, to),
            policyCoverage(institutionId, allowedWorkloads, from, to),
            workloadViolations(institutionId, allowedWorkloads, from, to),
            workloadOutcomes(institutionId, allowedWorkloads, from, to),
            actionSummary(institutionId, allowedWorkloads, from, to),
            recentSignals(institutionId, allowedWorkloads, from, to),
            recentExecutions(
                institutionId, allowedWorkloads, from, to,
                executionQuery, executionStatus, executionPage, executionSize
            ),
            coverage(institutionId, allowedWorkloads, from, to)
        );
    }

    private Counts counts(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select count(*) as total,
                       count(*) filter (where exists (
                           select 1 from runtime.transform_execution transform
                           where transform.execution_id = re.execution_id
                       )) as minimized,
                       count(*) filter (where re.connector_status in ('SENT_UNKNOWN','ACKNOWLEDGED','COMPLETED','FAILED')) as external_calls,
                       count(*) filter (where re.final_action = 'BLOCK' or re.status = 'BLOCKED') as blocked,
                       count(*) filter (where re.final_action = 'REVIEW' or re.status = 'REVIEW_REQUIRED') as review_required,
                       count(*) filter (where re.response_guard_status = 'REJECTED' or exists (
                           select 1 from runtime.response_guard_result guard
                           where guard.execution_id = re.execution_id and guard.leakage_detected
                       )) as response_rejected,
                       count(*) filter (where exists (
                           select 1 from runtime.runtime_decision decision where decision.execution_id = re.execution_id
                       )) as policy_evidence,
                       count(*) filter (where exists (
                           select 1 from runtime.ai_model_execution_evidence model where model.execution_id = re.execution_id
                       )) as model_evidence,
                       count(*) filter (where exists (
                           select 1 from runtime.response_guard_result guard where guard.execution_id = re.execution_id
                       )) as response_guard_evidence,
                       count(*) filter (where exists (
                           select 1 from runtime.response_guard_result guard
                           where guard.execution_id = re.execution_id and guard.status = 'PASSED'
                       )) as response_passed
                from runtime.runtime_execution re
                """ + scopedWhere(workloads)), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query(Counts.class).single();
    }

    private AiOperationsOverview.Metrics metrics(Counts current, Counts previous) {
        return new AiOperationsOverview.Metrics(
            metric(current.total(), previous.total()),
            metric(current.minimized(), previous.minimized()),
            metric(current.externalCalls(), previous.externalCalls()),
            metric(current.blocked(), previous.blocked()),
            metric(current.reviewRequired(), previous.reviewRequired()),
            metric(current.responseRejected(), previous.responseRejected())
        );
    }

    private AiOperationsOverview.Rates rates(Counts current, Counts previous) {
        return new AiOperationsOverview.Rates(
            rate(current.policyEvidence(), current.total(), previous.policyEvidence(), previous.total()),
            rate(current.modelEvidence(), current.externalCalls(), previous.modelEvidence(), previous.externalCalls()),
            rate(
                current.responsePassed(), current.responseGuardEvidence(),
                previous.responsePassed(), previous.responseGuardEvidence()
            )
        );
    }

    private AiOperationsOverview.Metric metric(long current, long previous) {
        Double change = previous == 0 ? null : Math.round((current - previous) * 10_000.0 / previous) / 100.0;
        return new AiOperationsOverview.Metric(current, previous, change);
    }

    private AiOperationsOverview.Rate rate(long currentPart, long currentTotal, long previousPart, long previousTotal) {
        double current = percentage(currentPart, currentTotal);
        double previous = percentage(previousPart, previousTotal);
        Double changePoint = previousTotal == 0 ? null : Math.round((current - previous) * 100.0) / 100.0;
        return new AiOperationsOverview.Rate(current, previous, changePoint);
    }

    private double percentage(long part, long total) {
        return total == 0 ? 0 : Math.round(part * 10_000.0 / total) / 100.0;
    }

    private List<AiOperationsOverview.FlowLink> flow(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        String policy = "case when re.final_action = 'BLOCK' then 'POLICY_BLOCK' "
            + "when re.final_action = 'REVIEW' then 'POLICY_REVIEW' "
            + "when re.final_action = 'TRANSFORM' then 'POLICY_TRANSFORM' "
            + "when re.final_action = 'ALLOW' then 'POLICY_ALLOW' else 'POLICY_NOT_EVALUATED' end";
        String access = "case when re.final_action = 'BLOCK' then 'DATA_NOT_REQUIRED' "
            + "when exists (select 1 from data_access_event access where access.request_id = re.request_id "
            + "and access.workload_id = re.workload_id) then 'DATA_RETRIEVED' else 'DATA_NOT_RECORDED' end";
        String minimization = "case when re.final_action = 'BLOCK' then 'MINIMIZATION_NOT_REQUIRED' "
            + "when exists (select 1 from runtime.transform_execution transform "
            + "where transform.execution_id = re.execution_id) then 'MINIMIZATION_APPLIED' "
            + "else 'MINIMIZATION_NOT_RECORDED' end";
        String provider = "case when re.final_action = 'BLOCK' then 'PROVIDER_NOT_CALLED' "
            + "when re.connector_status = 'FAILED' then 'PROVIDER_FAILED' "
            + "when re.connector_status = 'SENT_UNKNOWN' then 'PROVIDER_UNKNOWN' "
            + "when re.connector_status in ('ACKNOWLEDGED','COMPLETED') then 'PROVIDER_COMPLETED' "
            + "else 'PROVIDER_NOT_CALLED' end";
        String response = "case when re.final_action = 'BLOCK' then 'RESPONSE_NOT_REQUIRED' "
            + "when re.response_guard_status = 'REJECTED' then 'RESPONSE_REJECTED' "
            + "when re.response_guard_status = 'PASSED' then 'RESPONSE_PASSED' "
            + "else 'RESPONSE_NOT_EVALUATED' end";
        List<AiOperationsOverview.FlowLink> result = new ArrayList<>();
        result.addAll(flowLinks("'WORKLOAD_' || re.workload_id", "'REQUESTED'", institutionId, workloads, from, to));
        result.addAll(flowLinks("'REQUESTED'", policy, institutionId, workloads, from, to));
        result.addAll(flowLinks(policy, access, institutionId, workloads, from, to));
        result.addAll(flowLinks(access, minimization, institutionId, workloads, from, to));
        result.addAll(flowLinks(minimization, provider, institutionId, workloads, from, to));
        result.addAll(flowLinks(provider, response, institutionId, workloads, from, to));
        result.addAll(flowLinks(response, "'FINAL_' || re.status", institutionId, workloads, from, to));
        return result;
    }

    private List<AiOperationsOverview.FlowLink> flowLinks(
        String source,
        String target,
        String institutionId,
        Set<String> workloads,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("select " + source + " as source, " + target
                + " as target, count(*) as count from runtime.runtime_execution re "
                + scopedWhere(workloads) + " group by source, target order by source, target"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.FlowLink(
                rs.getString("source"), rs.getString("target"), rs.getLong("count")
            )).list();
    }

    private List<AiOperationsOverview.TrendPoint> trend(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                with days as (
                    select generate_series((cast(:fromAt as timestamptz) at time zone 'Asia/Seoul')::date,
                        ((cast(:toAt as timestamptz) - interval '1 microsecond') at time zone 'Asia/Seoul')::date,
                        interval '1 day')::date as day
                ), counts as (
                    select (re.created_at at time zone 'Asia/Seoul')::date as day,
                           count(*) as total,
                           count(*) filter (where exists (
                               select 1 from runtime.transform_execution transform
                               where transform.execution_id = re.execution_id
                           )) as minimized,
                           count(*) filter (where re.connector_status in ('SENT_UNKNOWN','ACKNOWLEDGED','COMPLETED','FAILED')) as external_calls,
                           count(*) filter (where re.status = 'COMPLETED') as completed,
                           count(*) filter (where re.final_action = 'BLOCK' or re.status = 'BLOCKED') as blocked,
                           count(*) filter (where re.final_action = 'REVIEW' or re.status = 'REVIEW_REQUIRED') as review_required,
                           count(*) filter (where re.response_guard_status = 'REJECTED') as response_rejected
                    from runtime.runtime_execution re
                    """ + scopedWhere(workloads) + " group by day) " + """
                select days.day, coalesce(counts.total, 0) as total,
                       coalesce(counts.minimized, 0) as minimized,
                       coalesce(counts.external_calls, 0) as external_calls,
                       coalesce(counts.completed, 0) as completed,
                       coalesce(counts.blocked, 0) as blocked,
                       coalesce(counts.review_required, 0) as review_required,
                       coalesce(counts.response_rejected, 0) as response_rejected
                from days left join counts using(day) order by days.day
                """), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.TrendPoint(
                rs.getObject("day", LocalDate.class), rs.getLong("total"), rs.getLong("minimized"),
                rs.getLong("external_calls"), rs.getLong("completed"), rs.getLong("blocked"),
                rs.getLong("review_required"), rs.getLong("response_rejected")
            )).list();
    }

    private List<AiOperationsOverview.DataClassControl> dataClassControls(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        Map<String, MutableDataClassControl> controls = new TreeMap<>();
        bind(jdbcClient.sql("""
                select field.data_class,
                       count(*) filter (where field.strategy <> 'KEEP') as transformed_fields,
                       count(*) filter (where field.strategy = 'KEEP') as retained_fields
                from runtime.runtime_execution re
                join runtime.transform_execution transform on transform.execution_id = re.execution_id
                join runtime.transform_field field on field.transform_execution_id = transform.transform_execution_id
                """ + scopedWhere(workloads) + " group by field.data_class"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> {
                MutableDataClassControl value = controls.computeIfAbsent(
                    rs.getString("data_class"), ignored -> new MutableDataClassControl()
                );
                value.transformedFields = rs.getLong("transformed_fields");
                value.retainedFields = rs.getLong("retained_fields");
                return value;
            }).list();
        bind(jdbcClient.sql("""
                select coalesce(finding.source_data_class, 'UNKNOWN') as data_class, count(*) as response_findings
                from runtime.runtime_execution re
                join runtime.response_sensitive_finding finding on finding.execution_id = re.execution_id
                """ + scopedWhere(workloads) + " group by coalesce(finding.source_data_class, 'UNKNOWN')"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> {
                MutableDataClassControl value = controls.computeIfAbsent(
                    rs.getString("data_class"), ignored -> new MutableDataClassControl()
                );
                value.responseFindings = rs.getLong("response_findings");
                return value;
            }).list();
        return controls.entrySet().stream()
            .map(entry -> new AiOperationsOverview.DataClassControl(
                entry.getKey(), AiOverviewDataProtectionPolicy.requiresProtection(entry.getKey()),
                entry.getValue().transformedFields,
                entry.getValue().retainedFields, entry.getValue().responseFindings
            ))
            .sorted((left, right) -> Long.compare(
                right.transformedFields() + right.retainedFields() + right.responseFindings(),
                left.transformedFields() + left.retainedFields() + left.responseFindings()
            ))
            .toList();
    }

    private List<AiOperationsOverview.LatencyPoint> latency(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select re.workload_id,
                       percentile_cont(0.5) within group (order by model.full_response_latency_ms)::bigint as p50_ms,
                       percentile_cont(0.95) within group (order by model.full_response_latency_ms)::bigint as p95_ms,
                       count(*) as observations
                from runtime.runtime_execution re
                join runtime.ai_model_execution_evidence model on model.execution_id = re.execution_id
                """ + scopedWhere(workloads) + " and model.full_response_latency_ms is not null "
                + "group by re.workload_id order by p95_ms desc, re.workload_id"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.LatencyPoint(
                rs.getString("workload_id"), rs.getObject("p50_ms", Long.class),
                rs.getObject("p95_ms", Long.class), rs.getLong("observations")
            )).list();
    }

    private AiOperationsOverview.ActionSummary actionSummary(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select count(*) filter (where re.connector_status = 'FAILED') as provider_failures,
                       count(*) filter (where re.connector_status = 'SENT_UNKNOWN') as provider_unknown,
                       count(*) filter (where re.final_action = 'REVIEW' or re.status = 'REVIEW_REQUIRED') as review_required,
                       count(*) filter (where re.final_action = 'BLOCK' or re.status = 'BLOCKED') as policy_blocked,
                       count(*) filter (where re.response_guard_status = 'REJECTED' or exists (
                           select 1 from runtime.response_guard_result guard
                           where guard.execution_id = re.execution_id and guard.leakage_detected
                       )) as response_rejected
                from runtime.runtime_execution re
                """ + scopedWhere(workloads)), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.ActionSummary(
                rs.getLong("provider_failures"), rs.getLong("provider_unknown"),
                rs.getLong("review_required"), rs.getLong("policy_blocked"),
                rs.getLong("response_rejected")
            )).single();
    }

    private List<AiOperationsOverview.HourlyStatus> hourlyStatuses(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select extract(hour from re.created_at at time zone 'Asia/Seoul')::int as hour,
                       re.status, count(*) as count
                from runtime.runtime_execution re
                """ + scopedWhere(workloads) + " group by hour, re.status order by hour, re.status"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.HourlyStatus(
                rs.getInt("hour"), rs.getString("status"), rs.getLong("count")
            )).list();
    }

    private List<AiOperationsOverview.PolicyCoverage> policyCoverage(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select re.workload_id, count(*) as total,
                       count(*) filter (where re.status in ('COMPLETED','EXTERNALLY_RECONCILED')) as normal,
                       count(*) filter (where re.status in ('REVIEW_REQUIRED','EGRESSING')) as review,
                       count(*) filter (where re.status in ('BLOCKED','FAILED')) as blocked
                from runtime.runtime_execution re
                """ + scopedWhere(workloads) + " group by re.workload_id order by total desc, re.workload_id"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.PolicyCoverage(
                rs.getString("workload_id"), rs.getLong("total"), rs.getLong("normal"),
                rs.getLong("review"), rs.getLong("blocked")
            )).list();
    }

    private List<AiOperationsOverview.WorkloadViolation> workloadViolations(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        String reason = "coalesce(nullif(split_part(decision.reason_codes, ',', 1), ''), 'OTHER')";
        String violationType = "case "
            + "when " + reason + " ~ '(AMOUNT|NUMERIC)' then 'VALUE_MISMATCH' "
            + "when " + reason + " ~ '(DESTINATION|PROVIDER_REGION|WORKLOAD.*BINDING)' then 'DESTINATION_MISMATCH' "
            + "when " + reason + " ~ '(DATA_CLASS|SENSITIVE_SCOPE|FIELD_SCOPE|RELEASED_FIELD|SECRET_FIELD)' then 'DATA_SCOPE' "
            + "when " + reason + " ~ '(PURPOSE|RETENTION|REUSE)' then 'PURPOSE_SCOPE' "
            + "when " + reason + " ~ '(POLICY|APPROVAL|HUMAN_REVIEW)' then 'POLICY_CONTROL' "
            + "else 'OTHER' end";
        return bind(jdbcClient.sql("select re.workload_id, " + violationType
                + " as violation_type, count(*) as count from runtime.runtime_execution re "
                + "join runtime.runtime_decision decision on decision.execution_id = re.execution_id "
                + scopedWhere(workloads) + " and re.final_action in ('BLOCK','REVIEW') "
                + "group by re.workload_id, violation_type order by re.workload_id, violation_type"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.WorkloadViolation(
                rs.getString("workload_id"), rs.getString("violation_type"), rs.getLong("count")
            )).list();
    }

    private List<AiOperationsOverview.WorkloadOutcome> workloadOutcomes(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select re.workload_id,
                       count(*) filter (where re.final_action in ('ALLOW','TRANSFORM')) as policy_allowed,
                       count(*) filter (where re.status in ('COMPLETED','EXTERNALLY_RECONCILED')) as final_completed
                from runtime.runtime_execution re
                """ + scopedWhere(workloads) + " group by re.workload_id order by policy_allowed desc, re.workload_id"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.WorkloadOutcome(
                rs.getString("workload_id"), rs.getLong("policy_allowed"), rs.getLong("final_completed")
            )).list();
    }

    private List<AiOperationsOverview.OperationalSignal> recentSignals(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        String reason = "coalesce(nullif(split_part(re.response_guard_reason_codes, ',', 1), ''), "
            + "(select nullif(split_part(decision.reason_codes, ',', 1), '') from runtime.runtime_decision decision "
            + "where decision.execution_id = re.execution_id order by decision.id desc limit 1), re.status)";
        return bind(jdbcClient.sql("select re.execution_id, "
                + "case when re.response_guard_status = 'REJECTED' then 'RESPONSE_REJECTED' "
                + "when re.connector_status = 'FAILED' then 'PROVIDER_FAILED' "
                + "when re.connector_status = 'SENT_UNKNOWN' then 'PROVIDER_UNKNOWN' "
                + "when re.status = 'BLOCKED' then 'POLICY_BLOCKED' else 'REVIEW_REQUIRED' end as signal_type, "
                + "case when re.response_guard_status = 'REJECTED' or re.connector_status = 'FAILED' "
                + "then 'CRITICAL' else 'WARNING' end as severity, re.status, re.workload_id, "
                + "case when re.response_guard_status = 'REJECTED' then 'RESPONSE_GUARD' "
                + "when re.connector_status in ('FAILED','SENT_UNKNOWN') then 'EXTERNAL_AI' "
                + "else 'POLICY_DECISION' end as stage, " + reason + " as reason_code, "
                + "case when re.response_guard_status = 'REJECTED' then 'REVIEW_RESPONSE_FINDINGS' "
                + "when re.connector_status = 'FAILED' then 'INSPECT_PROVIDER_FAILURE' "
                + "when re.connector_status = 'SENT_UNKNOWN' then 'VERIFY_PROVIDER_STATUS' "
                + "else 'REVIEW_POLICY_DECISION' end as next_action, re.updated_at as occurred_at "
                + "from runtime.runtime_execution re " + scopedWhere(workloads)
                + " and (re.response_guard_status = 'REJECTED' or re.connector_status in ('FAILED','SENT_UNKNOWN') "
                + "or re.status in ('BLOCKED','REVIEW_REQUIRED')) "
                + "order by case when re.response_guard_status = 'REJECTED' then 0 "
                + "when re.connector_status = 'FAILED' then 1 when re.connector_status = 'SENT_UNKNOWN' then 2 "
                + "when re.status = 'REVIEW_REQUIRED' then 3 else 4 end, re.updated_at desc limit 10"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new AiOperationsOverview.OperationalSignal(
                rs.getString("execution_id"), rs.getString("signal_type"), rs.getString("severity"),
                rs.getString("status"), rs.getString("workload_id"), rs.getString("stage"),
                rs.getString("reason_code"), rs.getString("next_action"),
                rs.getObject("occurred_at", OffsetDateTime.class)
            )).list();
    }

    private AiOperationsOverview.RecentExecutionPage recentExecutions(
        String institutionId,
        Set<String> workloads,
        OffsetDateTime from,
        OffsetDateTime to,
        String query,
        String status,
        int page,
        int size
    ) {
        String filters = " and (:executionQuery = '' or lower(re.request_id) like :executionPattern"
            + " or lower(re.execution_id) like :executionPattern or lower(re.workload_id) like :executionPattern)"
            + " and (:executionStatus = '' or re.status = :executionStatus)";
        JdbcClient.StatementSpec countQuery = bind(jdbcClient.sql("select count(*) from runtime.runtime_execution re "
                + scopedWhere(workloads) + filters), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .param("executionQuery", query).param("executionPattern", "%" + query.toLowerCase() + "%")
            .param("executionStatus", status);
        long total = countQuery.query(Long.class).single();
        List<AiOperationsOverview.RecentExecution> items = bind(jdbcClient.sql("""
                select re.execution_id, re.request_id, re.workload_id, re.policy_version,
                       re.final_action, re.status, coalesce(model.provider_status, re.connector_status) as provider_status,
                       re.response_guard_status, re.requested_field_count, re.retrieved_field_count,
                       re.transformed_field_count, re.released_field_count, model.provider_model_id,
                       model.full_response_latency_ms,
                       coalesce(re.response_guard_reason_codes, decision.reason_codes) as reason_codes,
                       re.created_at
                from runtime.runtime_execution re
                left join runtime.ai_model_execution_evidence model on model.execution_id = re.execution_id
                left join lateral (
                    select item.reason_codes from runtime.runtime_decision item
                    where item.execution_id = re.execution_id order by item.id desc limit 1
                ) decision on true
                """ + scopedWhere(workloads) + filters
                + " order by re.created_at desc, re.execution_id desc limit :executionSize offset :executionOffset"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .param("executionQuery", query).param("executionPattern", "%" + query.toLowerCase() + "%")
            .param("executionStatus", status).param("executionSize", size).param("executionOffset", page * size)
            .query((rs, row) -> new AiOperationsOverview.RecentExecution(
                rs.getString("execution_id"), rs.getString("request_id"), rs.getString("workload_id"),
                rs.getString("policy_version"), rs.getString("final_action"), rs.getString("status"),
                rs.getString("provider_status"), rs.getString("response_guard_status"),
                rs.getObject("requested_field_count", Integer.class),
                rs.getObject("retrieved_field_count", Integer.class),
                rs.getObject("transformed_field_count", Integer.class),
                rs.getObject("released_field_count", Integer.class), rs.getString("provider_model_id"),
                rs.getObject("full_response_latency_ms", Long.class), rs.getString("reason_codes"),
                rs.getObject("created_at", OffsetDateTime.class)
            )).list();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new AiOperationsOverview.RecentExecutionPage(items, page, size, total, totalPages);
    }

    private AiOperationsOverview.Coverage coverage(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        CoverageCounts counts = bind(jdbcClient.sql("""
                select count(*) as runtime_executions,
                       count(*) filter (where exists (select 1 from runtime.runtime_decision x where x.execution_id=re.execution_id)) as policy_decisions,
                       count(*) filter (where exists (select 1 from data_access_event x where x.request_id=re.request_id and x.workload_id=re.workload_id)) as data_access_events,
                       count(*) filter (where exists (select 1 from runtime.transform_execution x where x.execution_id=re.execution_id)) as transform_evidence,
                       count(*) filter (where exists (select 1 from runtime.outbound_candidate x where x.execution_id=re.execution_id)) as outbound_evidence,
                       count(*) filter (where exists (select 1 from runtime.ai_model_execution_evidence x where x.execution_id=re.execution_id)) as model_evidence,
                       count(*) filter (where exists (select 1 from runtime.response_guard_result x where x.execution_id=re.execution_id)) as response_guard_evidence
                from runtime.runtime_execution re
                """ + scopedWhere(workloads)), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query(CoverageCounts.class).single();
        return new AiOperationsOverview.Coverage(
            counts.runtimeExecutions(), counts.policyDecisions(), counts.dataAccessEvents(),
            counts.transformEvidence(), counts.outboundEvidence(), counts.modelEvidence(),
            counts.responseGuardEvidence()
        );
    }

    private String scopedWhere(Set<String> workloads) {
        return " where re.institution_id = :institutionId and re.execution_pack = 'AI'"
            + " and re.created_at >= :fromAt and re.created_at < :toAt" + workloadPredicate(workloads);
    }

    private String workloadPredicate(Set<String> workloads) {
        if (workloads.contains("*")) return "";
        return workloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)";
    }

    private JdbcClient.StatementSpec bind(
        JdbcClient.StatementSpec statement, String institutionId, Set<String> workloads
    ) {
        statement = statement.param("institutionId", institutionId);
        if (!workloads.contains("*") && !workloads.isEmpty()) {
            statement = statement.param("allowedWorkloads", workloads);
        }
        return statement;
    }

    private static final class MutableDataClassControl {
        private long transformedFields;
        private long retainedFields;
        private long responseFindings;
    }

    private record Counts(
        long total,
        long minimized,
        long externalCalls,
        long blocked,
        long reviewRequired,
        long responseRejected,
        long policyEvidence,
        long modelEvidence,
        long responseGuardEvidence,
        long responsePassed
    ) { }

    private record CoverageCounts(
        long runtimeExecutions,
        long policyDecisions,
        long dataAccessEvents,
        long transformEvidence,
        long outboundEvidence,
        long modelEvidence,
        long responseGuardEvidence
    ) { }
}
