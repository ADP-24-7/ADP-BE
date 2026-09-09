package com.adp.gateway.recovery.infrastructure;

import com.adp.gateway.recovery.application.ExternalRetryPort;
import com.adp.gateway.recovery.application.ExternalRetryUnavailableException;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalRetryResult;
import org.springframework.stereotype.Component;

@Component
public class UnconfiguredExternalRetryAdapter implements ExternalRetryPort {

    @Override
    public boolean supports(String connectorId) {
        return true;
    }

    @Override
    public boolean fallback() {
        return true;
    }

    @Override
    public ExternalRetryResult retry(ExternalInteractionRecovery recovery) {
        throw new ExternalRetryUnavailableException();
    }
}
