package com.adp.gateway.digitalasset.application;

import java.util.Map;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.runtime.application.ExecutionPackOutcome;
import com.adp.gateway.runtime.application.ExecutionPackOutcomeHandler;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetExternalStatus;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetSettlementOutcomeHandler implements ExecutionPackOutcomeHandler {
    private final DigitalAssetTransactionPersistencePort persistence;
    private final DigitalAssetMismatchPersistencePort mismatchPersistence;
    private final DigitalAssetReconciliationEvaluator reconciliationEvaluator;

    public DigitalAssetSettlementOutcomeHandler(
        DigitalAssetTransactionPersistencePort persistence,
        DigitalAssetMismatchPersistencePort mismatchPersistence,
        DigitalAssetReconciliationEvaluator reconciliationEvaluator
    ) {
        this.persistence = persistence;
        this.mismatchPersistence = mismatchPersistence;
        this.reconciliationEvaluator = reconciliationEvaluator;
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
            mismatchPersistence.open(executionId, reconciliationEvaluator.criticalCorrelationMismatch(
                request.providerCorrelationKey(), externalResult.externalRequestId()
            ));
            return outcome(RuntimeExecutionStatus.REVIEW_REQUIRED, connector, "EXTERNAL_REQUEST_CORRELATION_MISMATCH");
        }

        String settlementStatus = externalResult.externalStatus().name();
        var assessment = reconciliationEvaluator.evaluate(request.payload(), externalResult);
        String reconciliation = assessment.result().name();
        persistence.record(executionId, request.providerCorrelationKey(), externalResult.externalReference(),
            externalResult.transactionHash(),
            settlementStatus, reconciliation, connector.responseDigest());
        if (assessment.requiresReview()) {
            mismatchPersistence.open(executionId, assessment);
        }

        if (("MATCH".equals(reconciliation) || "RECOVERED".equals(reconciliation))
            && externalResult.isFinalSuccess()) {
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
        return outcome(RuntimeExecutionStatus.EGRESSING, connector, "SETTLEMENT_PENDING");
    }

    private ExecutionPackOutcome outcome(RuntimeExecutionStatus status, ConnectorResult connector, String reason) {
        return new ExecutionPackOutcome(status, ControlledDeliveryResult.withheld(connector.responseDigest(), reason));
    }
}
