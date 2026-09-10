package com.adp.gateway.recovery.application;

import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;

public interface ExternalReconciliationEvidencePort {
    boolean supports(String connectorId);

    void reconcile(ExternalInteractionRecovery recovery, ExternalStatusQueryResult status);
}
