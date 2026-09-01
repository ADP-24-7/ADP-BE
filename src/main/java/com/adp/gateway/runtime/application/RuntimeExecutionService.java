package com.adp.gateway.runtime.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.adp.gateway.audit.application.AuditRecorder;
import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.auth.application.AuthorizationRequest;
import com.adp.gateway.auth.application.AuthorizationService;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.context.application.CanonicalContextBuilder;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.decision.application.RuntimeDecisionService;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.application.OutboundCandidatePayloadBuilder;
import com.adp.gateway.egress.application.OutboundGuardChain;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.policy.application.PolicyApplicabilityEvaluator;
import com.adp.gateway.policy.application.RuntimePolicyContextFactory;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.PolicySelectionContext;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.retrieval.application.RetrievalService;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;
import com.adp.gateway.transform.application.TransformEngine;
import com.adp.gateway.transform.domain.TransformResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExecutionService {

    private final AuthorizationService authorizationService;
    private final RetrievalService retrievalService;
    private final CanonicalContextBuilder contextBuilder;
    private final RuntimePolicyContextFactory runtimePolicyContextFactory;
    private final PolicySnapshotPort policySnapshotPort;
    private final PolicyApplicabilityEvaluator policyApplicabilityEvaluator;
    private final RuntimeDecisionService decisionService;
    private final RuntimeConnectorPort runtimeConnector;
    private final AuditRecorder auditRecorder;
    private final RuntimeExecutionPersistence persistence;
    private final SubjectRefHasher subjectRefHasher;
    private final RuntimeInputHasher runtimeInputHasher;
    private final TransformEngine transformEngine;
    private final OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder;
    private final OutboundGuardChain outboundGuardChain;
    private final ResponseGuardPort responseGuardPort;
    private final Clock clock;

    public RuntimeExecutionService(
        AuthorizationService authorizationService,
        RetrievalService retrievalService,
        CanonicalContextBuilder contextBuilder,
        RuntimePolicyContextFactory runtimePolicyContextFactory,
        PolicySnapshotPort policySnapshotPort,
        PolicyApplicabilityEvaluator policyApplicabilityEvaluator,
        RuntimeDecisionService decisionService,
        RuntimeConnectorPort runtimeConnector,
        AuditRecorder auditRecorder,
        RuntimeExecutionPersistence persistence,
        SubjectRefHasher subjectRefHasher,
        RuntimeInputHasher runtimeInputHasher,
        TransformEngine transformEngine,
        OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder,
        OutboundGuardChain outboundGuardChain,
        ResponseGuardPort responseGuardPort,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.runtimePolicyContextFactory = runtimePolicyContextFactory;
        this.policySnapshotPort = policySnapshotPort;
        this.policyApplicabilityEvaluator = policyApplicabilityEvaluator;
        this.decisionService = decisionService;
        this.runtimeConnector = runtimeConnector;
        this.auditRecorder = auditRecorder;
        this.persistence = persistence;
        this.subjectRefHasher = subjectRefHasher;
        this.runtimeInputHasher = runtimeInputHasher;
        this.transformEngine = transformEngine;
        this.outboundCandidatePayloadBuilder = outboundCandidatePayloadBuilder;
        this.outboundGuardChain = outboundGuardChain;
        this.responseGuardPort = responseGuardPort;
        this.clock = clock;
    }

    public RuntimeExecutionResult execute(
        RuntimeRequestContext requestContext,
        AuthPrincipal principal,
        String providerProfileId,
        List<String> processingContexts,
        Map<String, Object> input
    ) {
        SubjectRef subject = SubjectRef.from(requestContext.subject());
        String executionId = "exec_" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String inputDigest = runtimeInputHasher.hash(input);
        persistence.recordReceived(new RuntimeExecutionTrace(
            executionId,
            requestContext.requestId(),
            requestContext.traceId(),
            requestContext.idempotencyKey(),
            requestContext.workloadId(),
            requestContext.purpose(),
            subject == null ? null : subjectRefHasher.hash(subject),
            providerProfileId,
            inputDigest,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            RuntimeExecutionStatus.RECEIVED.name(),
            now,
            now
        ));

        try {
            if (!authorizationService.authorize(new AuthorizationRequest(
                principal,
                requestContext.workloadId(),
                RuntimeAction.RUNTIME_EXECUTE,
                requestContext.purpose(),
                subject
            )).allowed()) {
                persistence.updateStatus(executionId, RuntimeExecutionStatus.BLOCKED);
                throw new AccessDeniedException("Runtime execution is not allowed");
            }
            persistence.updateStatus(executionId, RuntimeExecutionStatus.AUTHORIZED);

            RetrievalResult retrieval = retrievalService.retrieve(new DataAccessRequest(
                requestContext.requestId(),
                requestContext.traceId(),
                requestContext.workloadId(),
                requestContext.purpose(),
                subject
            ));
            CanonicalContext canonicalContext = contextBuilder.build(retrieval);
            persistence.recordRetrieved(executionId, canonicalContext);
            persistence.updateStatus(executionId, RuntimeExecutionStatus.RETRIEVED);

            RuntimePolicyContext runtimePolicyContext = runtimePolicyContextFactory.from(
                canonicalContext,
                processingContexts,
                providerProfileId,
                inputDigest
            );
            PolicySnapshot snapshot = policySnapshotPort.load(new PolicySelectionContext(
                runtimePolicyContext.workloadId(),
                runtimePolicyContext.purpose(),
                runtimePolicyContext.provider(),
                runtimePolicyContext.processingContexts(),
                runtimePolicyContext.runtimeDataClasses()
            ));
            persistence.recordPolicyEvaluation(executionId, snapshot);

            ApplicabilityResult applicability = policyApplicabilityEvaluator.evaluate(snapshot, runtimePolicyContext);
            RuntimeDecision decision = decisionService.decide(
                runtimePolicyContext,
                snapshot,
                RuntimeAuthorizationResult.ALLOWED,
                applicability
            );
            persistence.recordRuntimeDecision(executionId, decision);
            TransformResult transformResult = transformEngine.transform(
                executionId,
                canonicalContext,
                runtimePolicyContext,
                decision
            );
            persistence.recordTransform(executionId, decision, transformResult);
            RuntimeExecutionStatus finalStatus = finalStatus(decision.finalAction(), transformResult);
            persistence.updateStatus(executionId, finalStatus);
            if (decision.finalAction() != FinalAction.ALLOW && decision.finalAction() != FinalAction.TRANSFORM) {
                ConnectorResult connectorResult = ConnectorResult.notExecuted("runtime-connector-boundary");
                AuditContext auditContext = auditRecorder.record(requestContext, decision, connectorResult);
                return new RuntimeExecutionResult(
                    executionId,
                    finalStatus,
                    decision,
                    transformResult,
                    "NOT_EVALUATED",
                    connectorResult,
                    "NOT_EVALUATED",
                    auditContext
                );
            }
            var outboundPayload = outboundCandidatePayloadBuilder.build(
                requestContext,
                providerProfileId,
                decision,
                transformResult
            );
            OutboundGuardResult outboundGuardResult = outboundGuardChain.guard(
                requestContext,
                providerProfileId,
                decision,
                outboundPayload
            );
            persistence.recordOutbound(executionId, outboundPayload, outboundGuardResult);
            persistence.updateStatus(executionId, RuntimeExecutionStatus.OUTBOUND_READY);
            ConnectorResult connectorResult = runtimeConnector.execute(requestContext, decision, outboundPayload);
            persistence.recordConnector(executionId, connectorResult);
            persistence.updateStatus(executionId, RuntimeExecutionStatus.CONNECTOR_EXECUTED);
            ResponseGuardResult responseGuardResult = responseGuardPort.guard(outboundPayload, connectorResult);
            persistence.recordResponseGuard(executionId, connectorResult, responseGuardResult);
            AuditContext auditContext = auditRecorder.record(requestContext, decision, connectorResult);

            return new RuntimeExecutionResult(
                executionId,
                RuntimeExecutionStatus.CONNECTOR_EXECUTED,
                decision,
                transformResult,
                outboundGuardResult.status(),
                connectorResult,
                responseGuardResult.status(),
                auditContext
            );
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            persistence.updateStatus(executionId, RuntimeExecutionStatus.FAILED);
            throw exception;
        }
    }

    public RuntimeExecutionTrace load(String executionId) {
        return persistence.load(executionId);
    }

    private RuntimeExecutionStatus finalStatus(FinalAction finalAction, TransformResult transformResult) {
        return switch (finalAction) {
            case ALLOW -> RuntimeExecutionStatus.DECIDED;
            case TRANSFORM -> transformResult.applied()
                ? RuntimeExecutionStatus.TRANSFORMED
                : RuntimeExecutionStatus.FAILED;
            case REVIEW -> RuntimeExecutionStatus.REVIEW_REQUIRED;
            case BLOCK -> RuntimeExecutionStatus.BLOCKED;
        };
    }
}
