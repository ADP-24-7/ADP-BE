package com.adp.gateway.runtime.application;

import com.adp.gateway.audit.application.AuditRecorder;
import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.transform.domain.TransformResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionOutcomeFinalizer {
    private final RuntimeExecutionPersistence persistence;
    private final ControlledDeliveryService controlledDeliveryService;
    private final AuditRecorder auditRecorder;

    public ExecutionOutcomeFinalizer(
        RuntimeExecutionPersistence persistence,
        ControlledDeliveryService controlledDeliveryService,
        AuditRecorder auditRecorder
    ) {
        this.persistence = persistence;
        this.controlledDeliveryService = controlledDeliveryService;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public RuntimeExecutionResult finalizeOutcome(
        String executionId,
        RuntimeRequestContext requestContext,
        RuntimeDecision decision,
        TransformResult transformResult,
        String outboundGuardStatus,
        DestinationProfile destinationProfile,
        OutboundCandidatePayload outboundPayload,
        ProviderRequestPayload providerRequest,
        ConnectorResult connectorResult,
        ResponseGuardPort responseGuard
    ) {
        ResponseGuardResult responseGuardResult = responseGuard.guard(outboundPayload, connectorResult);
        persistence.recordResponseGuard(executionId, connectorResult, responseGuardResult);
        ExecutionPackOutcome outcome = controlledDeliveryService.resolve(
            destinationProfile.packType(), executionId, providerRequest, connectorResult, responseGuardResult
        );
        persistence.recordControlledDelivery(executionId, outcome.controlledDelivery());
        persistence.updateStatus(executionId, outcome.runtimeStatus());
        AuditContext auditContext = auditRecorder.record(executionId, requestContext, decision, connectorResult);
        return new RuntimeExecutionResult(
            executionId, outcome.runtimeStatus(), decision, transformResult, outboundGuardStatus,
            connectorResult, responseGuardResult.status(), outcome.controlledDelivery(), auditContext
        );
    }
}
