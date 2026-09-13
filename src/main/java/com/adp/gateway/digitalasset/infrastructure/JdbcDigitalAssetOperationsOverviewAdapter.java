package com.adp.gateway.digitalasset.infrastructure;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.adp.gateway.digitalasset.application.DigitalAssetOperationsOverviewPort;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperationsOverview;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcDigitalAssetOperationsOverviewAdapter implements DigitalAssetOperationsOverviewPort {
    private static final List<String> UNAVAILABLE_DIMENSIONS = List.of(
        "ASSET_SYMBOL", "EXACT_AMOUNT", "DESTINATION_CATEGORY"
    );

    private final JdbcClient jdbcClient;

    public JdbcDigitalAssetOperationsOverviewAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public DigitalAssetOperationsOverview load(
        String institutionId,
        Set<String> allowedWorkloads,
        OffsetDateTime from,
        OffsetDateTime to,
        OffsetDateTime generatedAt
    ) {
        OffsetDateTime previousFrom = from.minus(Duration.between(from, to));
        Counts current = counts(institutionId, allowedWorkloads, from, to);
        Counts previous = counts(institutionId, allowedWorkloads, previousFrom, from);
        return new DigitalAssetOperationsOverview(
            "adp-digital-asset-operations-overview/v1", generatedAt, from, to,
            metrics(current, previous),
            flow(institutionId, allowedWorkloads, from, to),
            trend(institutionId, allowedWorkloads, from, to),
            violations(institutionId, allowedWorkloads, from, to),
            hourlyStatuses(institutionId, allowedWorkloads, from, to),
            recentSignals(institutionId, allowedWorkloads, from, to),
            recentExecutions(institutionId, allowedWorkloads, from, to),
            coverage(institutionId, allowedWorkloads, from, to)
        );
    }

    private Counts counts(
        String institutionId,
        Set<String> allowedWorkloads,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select count(*) as total,
                       count(*) filter (where re.final_action in ('ALLOW', 'TRANSFORM')) as passed,
                       count(*) filter (where re.final_action = 'BLOCK') as blocked,
                       count(*) filter (where re.status = 'FAILED') as failed,
                       count(*) filter (where re.status = 'EGRESSING' and (
                           re.connector_status = 'SENT_UNKNOWN' or exists (
                               select 1 from runtime.digital_asset_transaction tx
                               where tx.execution_id = re.execution_id
                                 and tx.settlement_status = 'SENT_UNKNOWN'
                           )
                       )) as sent_unknown,
                       count(*) filter (where re.status = 'EXTERNALLY_RECONCILED') as reconciled
                from runtime.runtime_execution re
                where re.institution_id = :institutionId
                  and re.execution_pack = 'DIGITAL_ASSET'
                  and re.created_at >= :fromAt and re.created_at < :toAt
                """ + workloadPredicate(allowedWorkloads)), institutionId, allowedWorkloads)
            .param("fromAt", from)
            .param("toAt", to)
            .query(Counts.class)
            .single();
    }

    private DigitalAssetOperationsOverview.Metrics metrics(Counts current, Counts previous) {
        return new DigitalAssetOperationsOverview.Metrics(
            metric(current.total(), previous.total()),
            metric(current.passed(), previous.passed()),
            metric(current.blocked(), previous.blocked()),
            metric(current.failed(), previous.failed()),
            metric(current.sentUnknown(), previous.sentUnknown()),
            metric(current.reconciled(), previous.reconciled())
        );
    }

    private DigitalAssetOperationsOverview.Metric metric(long current, long previous) {
        Double change = previous == 0 ? null : Math.round(((current - previous) * 10_000.0 / previous)) / 100.0;
        return new DigitalAssetOperationsOverview.Metric(current, previous, change);
    }

    private List<DigitalAssetOperationsOverview.FlowLink> flow(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        String decisionExpression = "case when re.final_action in ('ALLOW', 'TRANSFORM') then 'DECISION_PASS' "
            + "when re.final_action = 'BLOCK' then 'DECISION_BLOCK' else 'DECISION_NOT_EVALUATED' end";
        String executionExpression = "case when re.final_action = 'BLOCK' then 'EXECUTION_NOT_SENT' "
            + "when re.status = 'EXTERNALLY_RECONCILED' then 'EXECUTION_RECONCILED' "
            + "when re.connector_status = 'SENT_UNKNOWN' or exists ("
            + "select 1 from runtime.digital_asset_transaction tx where tx.execution_id = re.execution_id "
            + "and tx.settlement_status = 'SENT_UNKNOWN') then 'EXECUTION_UNCERTAIN' "
            + "when re.status = 'FAILED' or re.connector_status = 'FAILED' then 'EXECUTION_FAILED' "
            + "when re.connector_status in ('ACKNOWLEDGED', 'COMPLETED') then 'EXECUTION_SUCCEEDED' "
            + "else 'EXECUTION_NOT_SENT' end";
        List<DigitalAssetOperationsOverview.FlowLink> result = new ArrayList<>();
        result.addAll(bind(jdbcClient.sql("select 'REQUESTED' as source, " + decisionExpression + " as target, "
                + "count(*) as count from runtime.runtime_execution re " + scopedWhere(workloads)
                + " group by target order by target"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.FlowLink(
                rs.getString("source"), rs.getString("target"), rs.getLong("count")
            )).list());
        result.addAll(bind(jdbcClient.sql("select " + decisionExpression + " as source, "
                + executionExpression + " as target, "
                + "count(*) as count from runtime.runtime_execution re " + scopedWhere(workloads)
                + " group by source, target order by source, target"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.FlowLink(
                rs.getString("source"), rs.getString("target"), rs.getLong("count")
            )).list());
        result.addAll(bind(jdbcClient.sql("select " + executionExpression + " as source, "
                + "'FINAL_' || re.status as target, count(*) as count "
                + "from runtime.runtime_execution re " + scopedWhere(workloads)
                + " group by source, target order by source, target"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.FlowLink(
                rs.getString("source"), rs.getString("target"), rs.getLong("count")
            )).list());
        return result;
    }

    private List<DigitalAssetOperationsOverview.TrendPoint> trend(
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
                           count(*) filter (where re.status = 'COMPLETED') as completed,
                           count(*) filter (where re.status = 'BLOCKED') as blocked,
                           count(*) filter (where re.status = 'FAILED') as failed,
                           count(*) filter (where re.status = 'EGRESSING' and (
                               re.connector_status = 'SENT_UNKNOWN' or exists (
                                   select 1 from runtime.digital_asset_transaction tx
                                   where tx.execution_id = re.execution_id
                                     and tx.settlement_status = 'SENT_UNKNOWN'
                               )
                           )) as sent_unknown,
                           count(*) filter (where re.status = 'EXTERNALLY_RECONCILED') as reconciled
                    from runtime.runtime_execution re
                    """ + scopedWhere(workloads) + " group by day) " + """
                select days.day, coalesce(counts.total, 0) as total,
                       coalesce(counts.completed, 0) as completed,
                       coalesce(counts.blocked, 0) as blocked,
                       coalesce(counts.failed, 0) as failed,
                       coalesce(counts.sent_unknown, 0) as sent_unknown,
                       coalesce(counts.reconciled, 0) as reconciled
                from days left join counts using(day) order by days.day
                """), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.TrendPoint(
                rs.getObject("day", LocalDate.class), rs.getLong("total"), rs.getLong("completed"),
                rs.getLong("blocked"), rs.getLong("failed"), rs.getLong("sent_unknown"),
                rs.getLong("reconciled")
            )).list();
    }

    private List<DigitalAssetOperationsOverview.ViolationCount> violations(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        String runtimeReasons = """
            select 'PRE_EXECUTION' as stage, trim(reason.value) as reason_code, count(*) as count
            from runtime.runtime_execution re
            join runtime.runtime_decision rd on rd.execution_id = re.execution_id
            cross join lateral regexp_split_to_table(coalesce(rd.reason_codes, ''), ',') reason(value)
            """ + scopedWhere(workloads) + " and trim(reason.value) <> '' and trim(reason.value) <> 'POLICY_APPLICABLE' "
            + "group by trim(reason.value)";
        String mismatchReasons = """
            select 'POST_EXECUTION' as stage, mismatch.value as reason_code, count(*) as count
            from runtime.runtime_execution re
            join runtime.digital_asset_mismatch_case mismatch_case on mismatch_case.execution_id = re.execution_id
            cross join lateral jsonb_array_elements_text(mismatch_case.mismatched_fields) mismatch(value)
            """ + scopedWhere(workloads) + " group by mismatch.value";
        return bind(jdbcClient.sql("select * from (" + runtimeReasons + " union all " + mismatchReasons
                + ") violations order by count desc, stage, reason_code"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.ViolationCount(
                rs.getString("stage"), rs.getString("reason_code"), rs.getLong("count")
            )).list();
    }

    private List<DigitalAssetOperationsOverview.HourlyStatus> hourlyStatuses(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select extract(hour from re.created_at at time zone 'Asia/Seoul')::int as hour,
                       re.status, count(*) as count
                from runtime.runtime_execution re
                """ + scopedWhere(workloads) + " group by hour, re.status order by hour, re.status"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.HourlyStatus(
                rs.getInt("hour"), rs.getString("status"), rs.getLong("count")
            )).list();
    }

    private List<DigitalAssetOperationsOverview.OperationalSignal> recentSignals(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select re.execution_id,
                       case when re.status = 'FAILED' then 'EXECUTION_FAILED'
                            when re.status = 'BLOCKED' then 'POLICY_BLOCKED'
                            when re.status = 'REVIEW_REQUIRED' then 'REVIEW_REQUIRED'
                            when re.status = 'EXTERNALLY_RECONCILED' then 'RECONCILED'
                            else 'SENT_UNKNOWN' end as signal_type,
                       case when re.status = 'FAILED' then 'CRITICAL'
                            when re.status in ('BLOCKED', 'REVIEW_REQUIRED', 'EGRESSING') then 'WARNING'
                            else 'INFO' end as severity,
                       re.status,
                       coalesce(rd.reason_codes, rr.last_error_code, re.connector_status) as reason_code,
                       re.updated_at as occurred_at
                from runtime.runtime_execution re
                left join runtime.runtime_decision rd on rd.execution_id = re.execution_id
                left join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
                """ + scopedWhere(workloads) + " and re.status in "
                + "('FAILED','BLOCKED','REVIEW_REQUIRED','EGRESSING','EXTERNALLY_RECONCILED') "
                + "order by re.updated_at desc, re.execution_id desc limit 8"), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.OperationalSignal(
                rs.getString("execution_id"), rs.getString("signal_type"), rs.getString("severity"),
                rs.getString("status"), rs.getString("reason_code"),
                rs.getObject("occurred_at", OffsetDateTime.class)
            )).list();
    }

    private List<DigitalAssetOperationsOverview.RecentExecution> recentExecutions(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        return bind(jdbcClient.sql("""
                select re.execution_id, re.request_id, re.workload_id, re.policy_version,
                       re.final_action, re.status, re.connector_status, rr.recovery_status, re.created_at
                from runtime.runtime_execution re
                left join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
                """ + scopedWhere(workloads) + " order by re.created_at desc, re.execution_id desc limit 10"),
            institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query((rs, row) -> new DigitalAssetOperationsOverview.RecentExecution(
                rs.getString("execution_id"), rs.getString("request_id"), rs.getString("workload_id"),
                rs.getString("policy_version"), rs.getString("final_action"), rs.getString("status"),
                rs.getString("connector_status"), rs.getString("recovery_status"),
                rs.getObject("created_at", OffsetDateTime.class)
            )).list();
    }

    private DigitalAssetOperationsOverview.Coverage coverage(
        String institutionId, Set<String> workloads, OffsetDateTime from, OffsetDateTime to
    ) {
        CoverageCounts counts = bind(jdbcClient.sql("""
                select count(*) as runtime_executions,
                       count(*) filter (where exists (select 1 from runtime.runtime_decision x where x.execution_id=re.execution_id)) as decision_evidence,
                       count(*) filter (where exists (select 1 from runtime.digital_asset_pre_execution_guard x where x.execution_id=re.execution_id)) as pre_execution_guard_evidence,
                       count(*) filter (where exists (select 1 from runtime.digital_asset_transaction x where x.execution_id=re.execution_id)) as transaction_evidence,
                       count(*) filter (where exists (select 1 from runtime.digital_asset_post_execution_evidence x where x.execution_id=re.execution_id)) as post_execution_evidence,
                       count(*) filter (where exists (select 1 from runtime.external_interaction_recovery x where x.execution_id=re.execution_id)) as recovery_evidence
                from runtime.runtime_execution re
                """ + scopedWhere(workloads)), institutionId, workloads)
            .param("fromAt", from).param("toAt", to)
            .query(CoverageCounts.class).single();
        return new DigitalAssetOperationsOverview.Coverage(
            counts.runtimeExecutions(), counts.decisionEvidence(), counts.preExecutionGuardEvidence(),
            counts.transactionEvidence(), counts.postExecutionEvidence(), counts.recoveryEvidence(),
            UNAVAILABLE_DIMENSIONS
        );
    }

    private String scopedWhere(Set<String> workloads) {
        return " where re.institution_id = :institutionId and re.execution_pack = 'DIGITAL_ASSET'"
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

    private record Counts(long total, long passed, long blocked, long failed, long sentUnknown, long reconciled) { }
    private record CoverageCounts(
        long runtimeExecutions,
        long decisionEvidence,
        long preExecutionGuardEvidence,
        long transactionEvidence,
        long postExecutionEvidence,
        long recoveryEvidence
    ) { }
}
