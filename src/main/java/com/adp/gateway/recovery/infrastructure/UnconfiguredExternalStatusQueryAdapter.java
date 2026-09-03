package com.adp.gateway.recovery.infrastructure;

import com.adp.gateway.recovery.application.ExternalStatusQueryPort;
import com.adp.gateway.recovery.application.ExternalStatusQueryUnavailableException;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import org.springframework.stereotype.Component;

@Component
public class UnconfiguredExternalStatusQueryAdapter implements ExternalStatusQueryPort {

    @Override
    public boolean supports(String connectorId) {
        return true;
    }

    @Override
    public boolean fallback() {
        return true;
    }

    @Override
    public com.adp.gateway.connector.domain.ConnectorStatus query(ExternalInteractionRecovery recovery) {
        throw new ExternalStatusQueryUnavailableException();
    }
}
