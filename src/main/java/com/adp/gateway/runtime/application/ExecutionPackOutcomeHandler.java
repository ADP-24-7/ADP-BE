package com.adp.gateway.runtime.application;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;

public interface ExecutionPackOutcomeHandler {
    ExecutionPackType supportedPack();

    ExecutionPackOutcome resolve(
        String executionId,
        ProviderRequestPayload providerRequest,
        ConnectorResult connectorResult,
        ResponseGuardResult responseGuardResult
    );
}
