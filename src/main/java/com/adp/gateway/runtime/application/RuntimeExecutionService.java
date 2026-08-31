package com.adp.gateway.runtime.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.adp.gateway.audit.application.AuditRecorder;
import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.auth.application.AuthorizationRequest;
import com.adp.gateway.auth.application.AuthorizationService;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.FakeConnector;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.context.application.CanonicalContextBuilder;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.decision.application.RuntimeDecisionService;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class RuntimeExecutionService {

    private final AuthorizationService authorizationService;
    private final RetrievalService retrievalService;
    private final CanonicalContextBuilder contextBuilder;
    private final RuntimePolicyContextFactory runtimePolicyContextFactory;
    private final PolicySnapshotPort policySnapshotPort;
    private final PolicyApplicabilityEvaluator policyApplicabilityEvaluator;
    private final RuntimeDecisionService decisionService;
    private final FakeConnector fakeConnector;
    private final AuditRecorder auditRecorder;
    private final RuntimeExecutionPersistence persistence;
    private final SubjectRefHasher subjectRefHasher;
    private final Clock clock;

    public RuntimeExecutionService(
        AuthorizationService authorizationService,
        RetrievalService retrievalService,
        CanonicalContextBuilder contextBuilder,
        RuntimePolicyContextFactory runtimePolicyContextFactory,
        PolicySnapshotPort policySnapshotPort,
        PolicyApplicabilityEvaluator policyApplicabilityEvaluator,
        RuntimeDecisionService decisionService,
        FakeConnector fakeConnector,
        AuditRecorder auditRecorder,
        RuntimeExecutionPersistence persistence,
        SubjectRefHasher subjectRefHasher,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.runtimePolicyContextFactory = runtimePolicyContextFactory;
        this.policySnapshotPort = policySnapshotPort;
        this.policyApplicabilityEvaluator = policyApplicabilityEvaluator;
        this.decisionService = decisionService;
        this.fakeConnector = fakeConnector;
        this.auditRecorder = auditRecorder;
        this.persistence = persistence;
        this.subjectRefHasher = subjectRefHasher;
        this.clock = clock;
    }

    public RuntimeExecutionResult execute(
        RuntimeRequestContext requestContext,
        AuthPrincipal principal,
        String providerProfileId,
        java.util.List<String> processingContexts
    ) {
        SubjectRef subject = SubjectRef.from(requestContext.subject());
        String executionId = "exec_" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        persistence.recordReceived(new RuntimeExecutionTrace(
            executionId,
            requestContext.requestId(),
            requestContext.traceId(),
            requestContext.idempotencyKey(),
            requestContext.workloadId(),
            requestContext.purpose(),
            subject == null ? null : subjectRefHasher.hash(subject),
            providerProfileId,
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
            providerProfileId
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
        RuntimeExecutionStatus finalStatus = finalStatus(decision.finalAction());
        persistence.updateStatus(executionId, finalStatus);
        ConnectorResult connectorResult = fakeConnector.execute(requestContext, decision);
        AuditContext auditContext = auditRecorder.record(requestContext, decision, connectorResult);

        return new RuntimeExecutionResult(executionId, finalStatus, decision, connectorResult, auditContext);
    }

    public RuntimeExecutionTrace load(String executionId) {
        return persistence.load(executionId);
    }

    private RuntimeExecutionStatus finalStatus(FinalAction finalAction) {
        return switch (finalAction) {
            case ALLOW, TRANSFORM -> RuntimeExecutionStatus.DECIDED;
            case REVIEW -> RuntimeExecutionStatus.REVIEW_REQUIRED;
            case BLOCK -> RuntimeExecutionStatus.BLOCKED;
        };
    }
}
