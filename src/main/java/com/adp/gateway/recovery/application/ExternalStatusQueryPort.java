package com.adp.gateway.recovery.application;

import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;

public interface ExternalStatusQueryPort {

    boolean supports(String connectorId);

    default boolean fallback() {
        return false;
    }

    default boolean requiresReconciliationEvidence() {
        return false;
    }

    ExternalStatusQueryResult query(ExternalInteractionRecovery recovery);
}
