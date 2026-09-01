package com.adp.gateway.egress.application;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;

public interface ResponseGuardPort {

    ResponseGuardResult guard(OutboundCandidatePayload payload, ConnectorResult connectorResult);
}
