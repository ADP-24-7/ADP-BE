package com.adp.gateway.recovery.application;

import java.time.OffsetDateTime;

import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryReconciliationCoordinator {
    private final ExternalInteractionRecoveryPersistence persistence;
    private final ExternalReconciliationEvidenceResolver evidenceResolver;

    public RecoveryReconciliationCoordinator(
        ExternalInteractionRecoveryPersistence persistence,
        ExternalReconciliationEvidenceResolver evidenceResolver
    ) {
        this.persistence = persistence;
        this.evidenceResolver = evidenceResolver;
    }

    @Transactional
    public void commit(
        ExternalInteractionRecovery recovery,
        String workerId,
        ExternalStatusQueryResult result,
        OffsetDateTime now
    ) {
        if (!persistence.reconcile(recovery.recoveryId(), workerId, result, now)) {
            throw new StaleRecoveryLeaseException();
        }
        evidenceResolver.reconcile(recovery, result);
    }
}
