package com.adp.gateway.digitalasset.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DigitalAssetOperationsOverview(
    String schemaVersion,
    OffsetDateTime generatedAt,
    OffsetDateTime from,
    OffsetDateTime to,
    Metrics metrics,
    List<FlowLink> flow,
    List<TrendPoint> trend,
    List<ViolationCount> violations,
    List<HourlyStatus> hourlyStatuses,
    List<OperationalSignal> recentSignals,
    List<RecentExecution> recentExecutions,
    Coverage coverage
) {
    public DigitalAssetOperationsOverview {
        flow = List.copyOf(flow);
        trend = List.copyOf(trend);
        violations = List.copyOf(violations);
        hourlyStatuses = List.copyOf(hourlyStatuses);
        recentSignals = List.copyOf(recentSignals);
        recentExecutions = List.copyOf(recentExecutions);
    }

    public record Metrics(
        Metric total,
        Metric passed,
        Metric blocked,
        Metric failed,
        Metric sentUnknown,
        Metric reconciled
    ) { }

    public record Metric(long current, long previous, Double changePercent) { }

    public record FlowLink(String source, String target, long count) { }

    public record TrendPoint(
        LocalDate date,
        long total,
        long completed,
        long blocked,
        long failed,
        long sentUnknown,
        long reconciled
    ) { }

    public record ViolationCount(String stage, String reasonCode, long count) { }

    public record HourlyStatus(int hour, String status, long count) { }

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
        String connectorStatus,
        String recoveryStatus,
        OffsetDateTime requestedAt
    ) { }

    public record Coverage(
        long runtimeExecutions,
        long decisionEvidence,
        long preExecutionGuardEvidence,
        long transactionEvidence,
        long postExecutionEvidence,
        long recoveryEvidence,
        List<String> unavailableDimensions
    ) {
        public Coverage {
            unavailableDimensions = List.copyOf(unavailableDimensions);
        }
    }
}
