package com.adp.gateway.ai.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AiOperationsOverview(
    String schemaVersion,
    OffsetDateTime generatedAt,
    OffsetDateTime from,
    OffsetDateTime to,
    Metrics metrics,
    Rates rates,
    List<FlowLink> flow,
    List<TrendPoint> trend,
    List<DataClassControl> dataClassControls,
    List<LatencyPoint> latency,
    List<HourlyStatus> hourlyStatuses,
    List<PolicyCoverage> policyCoverage,
    List<WorkloadViolation> workloadViolations,
    List<WorkloadOutcome> workloadOutcomes,
    ActionSummary actionSummary,
    List<OperationalSignal> recentSignals,
    RecentExecutionPage recentExecutions,
    Coverage coverage
) {
    public AiOperationsOverview {
        flow = List.copyOf(flow);
        trend = List.copyOf(trend);
        dataClassControls = List.copyOf(dataClassControls);
        latency = List.copyOf(latency);
        hourlyStatuses = List.copyOf(hourlyStatuses);
        policyCoverage = List.copyOf(policyCoverage);
        workloadViolations = List.copyOf(workloadViolations);
        workloadOutcomes = List.copyOf(workloadOutcomes);
        recentSignals = List.copyOf(recentSignals);
    }

    public record Metrics(
        Metric total,
        Metric minimized,
        Metric externalCalls,
        Metric blocked,
        Metric reviewRequired,
        Metric responseRejected
    ) { }

    public record Rates(Rate policyCoverage, Rate evidenceCoverage, Rate responseSafe) { }

    public record Metric(long current, long previous, Double changePercent) { }

    public record Rate(double current, double previous, Double changePoint) { }

    public record FlowLink(String source, String target, long count) { }

    public record TrendPoint(
        LocalDate date,
        long total,
        long minimized,
        long externalCalls,
        long completed,
        long blocked,
        long reviewRequired,
        long responseRejected
    ) { }

    public record DataClassControl(
        String dataClass,
        boolean protectionRequired,
        long transformedFields,
        long retainedFields,
        long responseFindings
    ) { }

    public record LatencyPoint(String workloadId, Long p50Ms, Long p95Ms, long observations) { }

    public record ActionSummary(
        long providerFailures,
        long providerUnknown,
        long reviewRequired,
        long policyBlocked,
        long responseRejected
    ) { }

    public record HourlyStatus(int hour, String status, long count) { }

    public record PolicyCoverage(
        String workloadId,
        long total,
        long normal,
        long review,
        long blocked
    ) { }

    public record WorkloadViolation(String workloadId, String violationType, long count) { }

    public record WorkloadOutcome(String workloadId, long policyAllowed, long finalCompleted) { }

    public record OperationalSignal(
        String executionId,
        String signalType,
        String severity,
        String status,
        String workloadId,
        String stage,
        String reasonCode,
        String nextAction,
        OffsetDateTime occurredAt
    ) { }

    public record RecentExecution(
        String executionId,
        String requestId,
        String workloadId,
        String policyVersion,
        String finalAction,
        String runtimeStatus,
        String providerStatus,
        String responseGuardStatus,
        Integer requestedFieldCount,
        Integer retrievedFieldCount,
        Integer transformedFieldCount,
        Integer releasedFieldCount,
        String providerModelId,
        Long latencyMs,
        String reasonCodes,
        OffsetDateTime requestedAt
    ) { }

    public record RecentExecutionPage(
        List<RecentExecution> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public RecentExecutionPage {
            items = List.copyOf(items);
        }
    }

    public record Coverage(
        long runtimeExecutions,
        long policyDecisions,
        long dataAccessEvents,
        long transformEvidence,
        long outboundEvidence,
        long modelEvidence,
        long responseGuardEvidence
    ) { }
}
