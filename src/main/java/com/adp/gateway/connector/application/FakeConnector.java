package com.adp.gateway.connector.application;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.DecisionAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class FakeConnector {

    public ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision) {
        if (decision.finalAction() != DecisionAction.ALLOW) {
            return new ConnectorResult("fake-connector", "NOT_EXECUTED");
        }
        return new ConnectorResult("fake-connector", "EXECUTED");
    }
}
