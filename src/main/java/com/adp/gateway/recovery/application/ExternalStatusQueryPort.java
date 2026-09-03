package com.adp.gateway.recovery.application;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;

public interface ExternalStatusQueryPort {

    boolean supports(String connectorId);

    default boolean fallback() {
        return false;
    }

    ConnectorStatus query(ExternalInteractionRecovery recovery);
}
