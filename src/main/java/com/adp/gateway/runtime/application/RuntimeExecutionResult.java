package com.adp.gateway.runtime.application;

import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.transform.domain.TransformResult;

public record RuntimeExecutionResult(
    String executionId,
    RuntimeExecutionStatus status,
    RuntimeDecision decision,
    TransformResult transformResult,
    String outboundGuardStatus,
    ConnectorResult connectorResult,
    String responseGuardStatus,
    AuditContext auditContext
) {
}
