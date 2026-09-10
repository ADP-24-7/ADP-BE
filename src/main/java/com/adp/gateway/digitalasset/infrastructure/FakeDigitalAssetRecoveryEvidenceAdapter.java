package com.adp.gateway.digitalasset.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.digitalasset.application.DigitalAssetPostExecutionEvidenceService;
import com.adp.gateway.digitalasset.application.DigitalAssetRuntimeSnapshotPersistence;
import com.adp.gateway.digitalasset.application.DigitalAssetTransactionPersistencePort;
import com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionStatus;
import com.adp.gateway.recovery.application.ExternalReconciliationEvidencePort;
import com.adp.gateway.recovery.application.ExternalStatusQueryPermanentException;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class FakeDigitalAssetRecoveryEvidenceAdapter implements ExternalReconciliationEvidencePort {
    private final FakeDigitalAssetPlatformStateStore stateStore;
    private final DigitalAssetPostExecutionEvidenceService evidenceService;
    private final DigitalAssetRuntimeSnapshotPersistence snapshotPersistence;
    private final DigitalAssetTransactionPersistencePort transactionPersistence;
    private final Clock clock;

    public FakeDigitalAssetRecoveryEvidenceAdapter(
        FakeDigitalAssetPlatformStateStore stateStore,
        DigitalAssetPostExecutionEvidenceService evidenceService,
        DigitalAssetRuntimeSnapshotPersistence snapshotPersistence,
        DigitalAssetTransactionPersistencePort transactionPersistence,
        Clock clock
    ) {
        this.stateStore = stateStore;
        this.evidenceService = evidenceService;
        this.snapshotPersistence = snapshotPersistence;
        this.transactionPersistence = transactionPersistence;
        this.clock = clock;
    }

    @Override
    public boolean supports(String connectorId) {
        return FakeDigitalAssetStatusQueryAdapter.CONNECTOR_ID.equals(connectorId);
    }

    @Override
    public void reconcile(ExternalInteractionRecovery recovery, ExternalStatusQueryResult status) {
        FakeDigitalAssetRecoveryObservation observation = stateStore
            .findRecoveryObservation(recovery.providerCorrelationKey())
            .orElseThrow(() -> new ExternalStatusQueryPermanentException(
                "Recovered digital asset execution evidence is unavailable"
            ));
        var resolution = evidenceService.resolve(
            recovery.executionId(), observation.requestPayload(), observation.executionResult(),
            OffsetDateTime.now(clock)
        );
        if (resolution.evidence().status() != DigitalAssetPostExecutionStatus.VERIFIED) {
            throw new ExternalStatusQueryPermanentException(
                "Recovered digital asset execution evidence is not completion-safe"
            );
        }
        snapshotPersistence.savePostExecutionEvidence(resolution.evidence());
        var result = observation.executionResult();
        transactionPersistence.record(
            recovery.executionId(), recovery.providerCorrelationKey(), result.externalReference(),
            result.transactionHash(), "SETTLED", "RECOVERED", result.responseDigest()
        );
    }
}
