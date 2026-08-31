package com.adp.gateway.connector.application;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.transform.domain.TransformResult;

public interface RuntimeConnectorPort {

    ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision, TransformResult transformResult);
}
