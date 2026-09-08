package com.adp.gateway.digitalasset.application;

import java.util.Map;
import java.time.OffsetDateTime;
import java.time.Clock;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.runtime.application.ExecutionPackOutcome;
import com.adp.gateway.runtime.application.ExecutionPackOutcomeHandler;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.recovery.application.ExternalInteractionRecoveryPersistence;
import com.adp.gateway.digitalasset.domain.DigitalAssetExternalStatus;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetSettlementOutcomeHandler implements ExecutionPackOutcomeHandler {
    private final DigitalAssetTransactionPersistencePort persistence;
    private final DigitalAssetMismatchPersistencePort mismatchPersistence;
    private final DigitalAssetReconciliationEvaluator reconciliationEvaluator;
    private final DigitalAssetPostExecutionEvidenceService postExecutionEvidenceService;
    private final DigitalAssetRuntimeSnapshotPersistence snapshotPersistence;
    private final ExternalInteractionRecoveryPersistence recoveryPersistence;
    private final Clock clock;

    public DigitalAssetSettlementOutcomeHandler(
        DigitalAssetTransactionPersistencePort persistence,
        DigitalAssetMismatchPersistencePort mismatchPersistence,
        DigitalAssetReconciliationEvaluator reconciliationEvaluator,
        DigitalAssetPostExecutionEvidenceService postExecutionEvidenceService,
        DigitalAssetRuntimeSnapshotPersistence snapshotPersistence,
        ExternalInteractionRecoveryPersistence recoveryPersistence,
        Clock clock
    ) {
        this.persistence = persistence;
        this.mismatchPersistence = mismatchPersistence;
        this.reconciliationEvaluator = reconciliationEvaluator;
        this.postExecutionEvidenceService = postExecutionEvidenceService;
        this.snapshotPersistence = snapshotPersistence;
        this.recoveryPersistence = recoveryPersistence;
        this.clock = clock;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public ExecutionPackOutcome resolve(String executionId, ProviderRequestPayload request,
                                        ConnectorResult connector, ResponseGuardResult guard) {
        if (connector.status() == ConnectorStatus.SENT_UNKNOWN) {
            persistence.record(executionId, request.providerCorrelationKey(), null, null,
                "SENT_UNKNOWN", "WAIT", null);
            return outcome(RuntimeExecutionStatus.EGRESSING, connector, "SETTLEMENT_UNKNOWN");
        }
        if (connector.status() == ConnectorStatus.FAILED) {
            persistence.record(executionId, request.providerCorrelationKey(), null, null,
                "FAILED", "WAIT", connector.responseDigest());
            return outcome(RuntimeExecutionStatus.FAILED, connector, "ASSET_PLATFORM_FAILED");
        }
        if (!guard.isPassed() || !(connector.responsePayload() instanceof Map<?, ?> response)) {
            return outcome(RuntimeExecutionStatus.BLOCKED, connector, "SETTLEMENT_RESPONSE_REJECTED");
        }

        ExternalExecutionResult externalResult;
        try {
            externalResult = ExternalExecutionResult.from(response, connector.responseDigest());
        } catch (IllegalArgumentException exception) {
            return outcome(RuntimeExecutionStatus.BLOCKED, connector, "EXTERNAL_EXECUTION_RESULT_INVALID");
        }
        if (!request.providerCorrelationKey().equals(externalResult.externalRequestId())) {
            var assessment = reconciliationEvaluator.criticalCorrelationMismatch(
                request.providerCorrelationKey(), externalResult.externalRequestId()
            );
            snapshotPersistence.savePostExecutionEvidence(postExecutionEvidenceService.resolve(
                executionId, externalResult, assessment, OffsetDateTime.now(clock)
            ));
            mismatchPersistence.open(executionId, assessment);
            return outcome(RuntimeExecutionStatus.REVIEW_REQUIRED, connector, "EXTERNAL_REQUEST_CORRELATION_MISMATCH");
        }

        String settlementStatus = externalResult.externalStatus().name();
        var assessment = reconciliationEvaluator.evaluate(request.payload(), externalResult);
        var postExecutionEvidence = postExecutionEvidenceService.resolve(
            executionId, externalResult, assessment, OffsetDateTime.now(clock)
        );
        snapshotPersistence.savePostExecutionEvidence(postExecutionEvidence);
        if (externalResult.externalStatus() == DigitalAssetExternalStatus.SENT_UNKNOWN
            && connector.status() != ConnectorStatus.SENT_UNKNOWN) {
            recoveryPersistence.scheduleUnknown(executionId, connector, OffsetDateTime.now(clock));
        }
        String reconciliation = assessment.result().name();
        persistence.record(executionId, request.providerCorrelationKey(), externalResult.externalReference(),
            externalResult.transactionHash(),
            settlementStatus, reconciliation, connector.responseDigest());
        if (assessment.requiresReview()) {
            mismatchPersistence.open(executionId, assessment);
        }

        if (postExecutionEvidence.permitsCompletion()) {
            return new ExecutionPackOutcome(RuntimeExecutionStatus.COMPLETED,
                ControlledDeliveryResult.delivered("SETTLED", connector.responseDigest()));
        }
        if ("MISMATCH".equals(reconciliation) || "CRITICAL_MISMATCH".equals(reconciliation)
            || "RECONCILIATION_REQUIRED".equals(settlementStatus)) {
            return outcome(RuntimeExecutionStatus.REVIEW_REQUIRED, connector, "SETTLEMENT_RECONCILIATION_REQUIRED");
        }
        if (externalResult.externalStatus() == DigitalAssetExternalStatus.FAILED) {
            return outcome(RuntimeExecutionStatus.FAILED, connector, "SETTLEMENT_FAILED");
        }
        if (postExecutionEvidence.status()
            == com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionStatus.REVIEW_REQUIRED) {
            return outcome(RuntimeExecutionStatus.REVIEW_REQUIRED, connector, "POST_EXECUTION_EVIDENCE_INCOMPLETE");
        }
        return outcome(RuntimeExecutionStatus.EGRESSING, connector, "SETTLEMENT_PENDING");
    }

    private ExecutionPackOutcome outcome(RuntimeExecutionStatus status, ConnectorResult connector, String reason) {
        return new ExecutionPackOutcome(status, ControlledDeliveryResult.withheld(connector.responseDigest(), reason));
    }
}
