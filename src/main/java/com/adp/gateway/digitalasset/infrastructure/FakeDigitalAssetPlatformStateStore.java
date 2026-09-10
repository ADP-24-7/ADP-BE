package com.adp.gateway.digitalasset.infrastructure;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetDescriptor;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class FakeDigitalAssetPlatformStateStore {

    private final ConcurrentMap<String, ConnectorStatus> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FakeDigitalAssetExecutionObservation> executions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FakeDigitalAssetRecoveryObservation> recoveries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> externalEffectCounts = new ConcurrentHashMap<>();

    public void record(String providerCorrelationKey, ConnectorStatus status) {
        states.put(providerCorrelationKey, status);
    }

    public Optional<ConnectorStatus> find(String providerCorrelationKey) {
        return Optional.ofNullable(states.get(providerCorrelationKey));
    }

    public ConnectorStatus retryIfNotSent(String providerCorrelationKey) {
        return states.compute(providerCorrelationKey, (key, status) -> {
            if (status == null) {
                throw new IllegalStateException("Provider request was not found");
            }
            return status == ConnectorStatus.NOT_SENT ? ConnectorStatus.ACKNOWLEDGED : status;
        });
    }

    public void recordExecution(String externalReference, FakeDigitalAssetExecutionObservation observation) {
        executions.put(externalReference, observation);
    }

    public void recordExecution(ExternalExecutionResult result) {
        var asset = new DigitalAssetDescriptor(
            result.executedChainId(), result.executedAssetKind(), result.executedAssetSymbol(),
            result.executedAssetContractAddress(), result.operation(), result.tokenId()
        );
        recordExecution(result.externalReference(), new FakeDigitalAssetExecutionObservation(
            result.transactionHash(), asset, result.executedRecipientAddress(), result.nativeValue(),
            result.executedAssetKind() == DigitalAssetKind.NATIVE ? null : result.executedAmount(),
            result.receiptStatus(), result.finalityStatus(), result.tokenTransferEvidenceRef(),
            result.internalTraceEvidenceRef(), result.executedAt(), result.finalizedAt()
        ));
    }

    public Optional<FakeDigitalAssetExecutionObservation> findExecution(String externalReference) {
        return Optional.ofNullable(executions.get(externalReference));
    }

    public void recordExternalEffect(String providerCorrelationKey) {
        externalEffectCounts.merge(providerCorrelationKey, 1, Integer::sum);
    }

    public int externalEffectCount(String providerCorrelationKey) {
        return externalEffectCounts.getOrDefault(providerCorrelationKey, 0);
    }

    public void recordRecoveryObservation(
        String providerCorrelationKey,
        java.util.Map<String, Object> requestPayload,
        ExternalExecutionResult result
    ) {
        recoveries.put(providerCorrelationKey, new FakeDigitalAssetRecoveryObservation(requestPayload, result));
    }

    Optional<FakeDigitalAssetRecoveryObservation> findRecoveryObservation(String providerCorrelationKey) {
        return Optional.ofNullable(recoveries.get(providerCorrelationKey));
    }
}
