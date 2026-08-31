package com.adp.gateway.connector.application;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.transform.domain.TransformResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class FakeConnector implements RuntimeConnectorPort {

    public ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision, TransformResult transformResult) {
        if (decision.finalAction() != FinalAction.ALLOW && decision.finalAction() != FinalAction.TRANSFORM) {
            return new ConnectorResult("fake-connector", "NOT_EXECUTED");
        }
        if (decision.finalAction() == FinalAction.TRANSFORM && !transformResult.applied()) {
            return new ConnectorResult("fake-connector", "TRANSFORM_REQUIRED");
        }
        return new ConnectorResult("fake-connector", "EXECUTED");
    }
}
