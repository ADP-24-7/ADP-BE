package com.adp.gateway.connector.application;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.DecisionResult;
import org.springframework.stereotype.Component;

@Component
public class FakeConnector {

    public ConnectorResult execute(RuntimeRequestContext context, DecisionResult decisionResult) {
        return new ConnectorResult("fake-connector", "EXECUTED");
    }
}
