package com.adp.gateway.recovery.application;

import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalRetryResult;

public interface ExternalRetryPort {

    boolean supports(String connectorId);

    default boolean fallback() {
        return false;
    }

    ExternalRetryResult retry(ExternalInteractionRecovery recovery);
}
