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
import com.adp.gateway.connector.application.RuntimeConnectorResolver;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalContextBuilder;
import com.adp.gateway.context.application.ExecutionPackContextBuilderResolver;
import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.decision.application.RuntimeDecisionService;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.application.DestinationProfileNotFoundException;
import com.adp.gateway.egress.application.ExternalSchemaMapperResolver;
import com.adp.gateway.egress.application.DestinationProfilePort;
import com.adp.gateway.egress.application.OutboundCandidatePayloadBuilder;
import com.adp.gateway.egress.application.OutboundGuardChain;
import com.adp.gateway.egress.application.OutboundGuardException;
import com.adp.gateway.egress.application.ResponseGuardResolver;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.IdempotencyOutcome;
import com.adp.gateway.policy.application.PolicyApplicabilityEvaluator;
import com.adp.gateway.policy.application.RuntimePolicyContextFactory;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.PolicySelectionContext;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.policyharness.application.ApprovalScopeNotFoundException;
import com.adp.gateway.policyharness.application.ApprovalScopePort;
import com.adp.gateway.policyharness.application.FieldLineageFactory;
import com.adp.gateway.policyharness.application.PolicyHarnessEvaluator;
import com.adp.gateway.policyharness.domain.ApprovalReuseStatus;
import com.adp.gateway.retrieval.application.RetrievalService;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.transform.application.TransformEngine;
import com.adp.gateway.transform.domain.TransformResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RuntimeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeExecutionService.class);

    private final AuthorizationService authorizationService;
    private final RetrievalService retrievalService;
    private final CanonicalContextBuilder contextBuilder;
    private final RuntimePolicyContextFactory runtimePolicyContextFactory;
    private final PolicySnapshotPort policySnapshotPort;
    private final PolicyApplicabilityEvaluator policyApplicabilityEvaluator;
    private final RuntimeDecisionService decisionService;
    private final RuntimeConnectorResolver runtimeConnectorResolver;
    private final AuditRecorder auditRecorder;
    private final RuntimeExecutionPersistence persistence;
    private final SubjectRefHasher subjectRefHasher;
    private final RuntimeInputHasher runtimeInputHasher;
    private final RuntimeRequestHasher runtimeRequestHasher;
    private final TransformEngine transformEngine;
    private final DestinationProfilePort destinationProfilePort;
    private final OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder;
    private final OutboundGuardChain outboundGuardChain;
    private final ResponseGuardResolver responseGuardResolver;
    private final ExecutionPackContextBuilderResolver contextBuilderResolver;
    private final ApprovalScopePort approvalScopePort;
    private final FieldLineageFactory fieldLineageFactory;
    private final PolicyHarnessEvaluator policyHarnessEvaluator;
    private final ExternalSchemaMapperResolver externalSchemaMapperResolver;
    private final ControlledDeliveryService controlledDeliveryService;
    private final Clock clock;
    private final GatewayObservability observability;

    public RuntimeExecutionService(
        AuthorizationService authorizationService,
        RetrievalService retrievalService,
        CanonicalContextBuilder contextBuilder,
        RuntimePolicyContextFactory runtimePolicyContextFactory,
        PolicySnapshotPort policySnapshotPort,
        PolicyApplicabilityEvaluator policyApplicabilityEvaluator,
        RuntimeDecisionService decisionService,
        RuntimeConnectorResolver runtimeConnectorResolver,
        AuditRecorder auditRecorder,
        RuntimeExecutionPersistence persistence,
        SubjectRefHasher subjectRefHasher,
        RuntimeInputHasher runtimeInputHasher,
        RuntimeRequestHasher runtimeRequestHasher,
        TransformEngine transformEngine,
        DestinationProfilePort destinationProfilePort,
        OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder,
        OutboundGuardChain outboundGuardChain,
        ResponseGuardResolver responseGuardResolver,
        ExecutionPackContextBuilderResolver contextBuilderResolver,
        ApprovalScopePort approvalScopePort,
        FieldLineageFactory fieldLineageFactory,
        PolicyHarnessEvaluator policyHarnessEvaluator,
        ExternalSchemaMapperResolver externalSchemaMapperResolver,
        ControlledDeliveryService controlledDeliveryService,
        Clock clock,
        GatewayObservability observability
    ) {
        this.authorizationService = authorizationService;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.runtimePolicyContextFactory = runtimePolicyContextFactory;
        this.policySnapshotPort = policySnapshotPort;
        this.policyApplicabilityEvaluator = policyApplicabilityEvaluator;
        this.decisionService = decisionService;
        this.runtimeConnectorResolver = runtimeConnectorResolver;
        this.auditRecorder = auditRecorder;
        this.persistence = persistence;
        this.subjectRefHasher = subjectRefHasher;
        this.runtimeInputHasher = runtimeInputHasher;
        this.runtimeRequestHasher = runtimeRequestHasher;
        this.transformEngine = transformEngine;
        this.destinationProfilePort = destinationProfilePort;
        this.outboundCandidatePayloadBuilder = outboundCandidatePayloadBuilder;
        this.outboundGuardChain = outboundGuardChain;
        this.responseGuardResolver = responseGuardResolver;
        this.contextBuilderResolver = contextBuilderResolver;
        this.approvalScopePort = approvalScopePort;
        this.fieldLineageFactory = fieldLineageFactory;
        this.policyHarnessEvaluator = policyHarnessEvaluator;
        this.externalSchemaMapperResolver = externalSchemaMapperResolver;
        this.controlledDeliveryService = controlledDeliveryService;
        this.clock = clock;
        this.observability = observability;
    }

    public RuntimeExecutionResult execute(
        RuntimeRequestContext requestContext,
        AuthPrincipal principal,
        String institutionId,
        String approvalReference,
        String destinationProfileId,
        List<String> processingContexts,
        Map<String, Object> input
    ) {
        SubjectRef subject = SubjectRef.from(requestContext.subject());
        validateAuthorization(requestContext, principal, institutionId, subject);
        String executionId = "exec_" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String inputDigest = runtimeInputHasher.hash(input);
        String requestHash = runtimeRequestHasher.hash(
            institutionId,
            approvalReference,
            requestContext.workloadId(),
            requestContext.purpose(),
            requestContext.subject(),
            destinationProfileId,
            processingContexts,
            input
        );
        String subjectRefDigest = subject == null ? null : subjectRefHasher.hash(subject);
        persistence.recordReceived(RuntimeExecutionTrace.received(
            executionId,
            requestContext.requestId(),
            requestContext.traceId(),
            requestContext.idempotencyKey(),
            requestContext.workloadId(),
            requestContext.purpose(),
            subjectRefDigest,
            destinationProfileId,
            principal.institutionId(),
            approvalReference,
            inputDigest,
            now
        ), principal.institutionId(), requestHash);
        recordIdempotency(IdempotencyOutcome.NEW);

        try {
            persistence.recordAuthorization(executionId, "PASSED");
            updateStatus(executionId, RuntimeExecutionStatus.AUTHORIZED);
            var approvalScope = approvalScopePort.load(approvalReference, now);
            DestinationProfile destinationProfile = destinationProfilePort.load(destinationProfileId, now);
            persistence.recordDestinationProfile(executionId, destinationProfile);
            var packContextBuilder = contextBuilderResolver.resolve(destinationProfile.packType());
            var externalSchemaMapper = externalSchemaMapperResolver.resolve(destinationProfile.packType());
            var runtimeConnector = runtimeConnectorResolver.resolve(destinationProfile.packType());
            var responseGuard = responseGuardResolver.resolve(destinationProfile.packType());
            packContextBuilder.validate(input);

            RetrievalResult retrieval = retrievalService.retrieve(new DataAccessRequest(
                requestContext.requestId(),
                requestContext.traceId(),
                requestContext.workloadId(),
                requestContext.purpose(),
                subject
            ));
            CanonicalContext canonicalContext = contextBuilder.build(retrieval);
            canonicalContext = packContextBuilder.merge(canonicalContext, input);
            persistence.recordRetrieved(executionId, canonicalContext);
            updateStatus(executionId, RuntimeExecutionStatus.RETRIEVED);

            RuntimePolicyContext runtimePolicyContext = runtimePolicyContextFactory.from(
                canonicalContext,
                processingContexts,
                destinationProfile.providerProfileId(),
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
            updateStatus(executionId, finalStatus);
            if (decision.finalAction() != FinalAction.ALLOW && decision.finalAction() != FinalAction.TRANSFORM) {
                var fieldLineage = fieldLineageFactory.create(retrieval, canonicalContext, transformResult, null);
                var policyHarnessBinding = policyHarnessEvaluator.evaluate(
                    approvalScope,
                    institutionId,
                    principal,
                    requestContext.workloadId(),
                    requestContext.purpose(),
                    subjectRefDigest,
                    processingContexts,
                    destinationProfile,
                    snapshot,
                    decision,
                    fieldLineage,
                    now
                );
                persistence.recordPolicyHarness(executionId, policyHarnessBinding);
            }
            if (finalStatus == RuntimeExecutionStatus.FAILED) {
                ConnectorResult connectorResult = ConnectorResult.notExecuted("runtime-connector-boundary");
                AuditContext auditContext = auditRecorder.record(executionId, requestContext, decision, connectorResult);
                return new RuntimeExecutionResult(
                    executionId,
                    finalStatus,
                    decision,
                    transformResult,
                    "NOT_EVALUATED",
                    connectorResult,
                    "NOT_EVALUATED",
                    ControlledDeliveryResult.withheld(null, "NOT_REACHED"),
                    auditContext
                );
            }
            if (decision.finalAction() != FinalAction.ALLOW && decision.finalAction() != FinalAction.TRANSFORM) {
                ConnectorResult connectorResult = ConnectorResult.notExecuted("runtime-connector-boundary");
                AuditContext auditContext = auditRecorder.record(executionId, requestContext, decision, connectorResult);
                return new RuntimeExecutionResult(
                    executionId,
                    finalStatus,
                    decision,
                    transformResult,
                    "NOT_EVALUATED",
                    connectorResult,
                    "NOT_EVALUATED",
                    ControlledDeliveryResult.withheld(null, "NOT_REACHED"),
                    auditContext
                );
            }
            var outboundPayload = outboundCandidatePayloadBuilder.build(
                destinationProfile,
                canonicalContext,
                decision,
                transformResult
            );
            var fieldLineage = fieldLineageFactory.create(
                retrieval,
                canonicalContext,
                transformResult,
                outboundPayload
            );
            var policyHarnessBinding = policyHarnessEvaluator.evaluate(
                approvalScope,
                institutionId,
                principal,
                requestContext.workloadId(),
                requestContext.purpose(),
                subjectRefDigest,
                processingContexts,
                destinationProfile,
                snapshot,
                decision,
                fieldLineage,
                now
            );
            if (!policyHarnessBinding.permitsEgress()) {
                persistence.recordPolicyHarness(
                    executionId,
                    policyHarnessBinding.withFieldLineage(
                        fieldLineageFactory.create(retrieval, canonicalContext, transformResult, null)
                    )
                );
                RuntimeExecutionStatus harnessStatus = policyHarnessBinding.approvalReuseStatus()
                    == ApprovalReuseStatus.REVIEW_REQUIRED
                    ? RuntimeExecutionStatus.REVIEW_REQUIRED
                    : RuntimeExecutionStatus.BLOCKED;
                updateStatus(executionId, harnessStatus);
                ConnectorResult connectorResult = ConnectorResult.notExecuted("runtime-connector-boundary");
                AuditContext auditContext = auditRecorder.record(executionId, requestContext, decision, connectorResult);
                return new RuntimeExecutionResult(
                    executionId,
                    harnessStatus,
                    decision,
                    transformResult,
                    "NOT_EVALUATED",
                    connectorResult,
                    "NOT_EVALUATED",
                    ControlledDeliveryResult.withheld(null, "NOT_REACHED"),
                    auditContext
                );
            }
            OutboundGuardResult outboundGuardResult = outboundGuardChain.guard(
                destinationProfile,
                requestContext.workloadId(),
                requestContext.purpose(),
                now,
                decision,
                outboundPayload
            );
            persistence.recordOutbound(executionId, outboundPayload, outboundGuardResult);
            if (!outboundGuardResult.isPassed()) {
                persistence.recordPolicyHarness(
                    executionId,
                    policyHarnessBinding.withFieldLineage(
                        fieldLineageFactory.create(retrieval, canonicalContext, transformResult, null)
                    )
                );
                updateStatus(executionId, RuntimeExecutionStatus.BLOCKED);
                ConnectorResult connectorResult = ConnectorResult.notExecuted("runtime-connector-boundary");
                AuditContext auditContext = auditRecorder.record(executionId, requestContext, decision, connectorResult);
                return new RuntimeExecutionResult(
                    executionId,
                    RuntimeExecutionStatus.BLOCKED,
                    decision,
                    transformResult,
                    outboundGuardResult.status(),
                    connectorResult,
                    "NOT_EVALUATED",
                    ControlledDeliveryResult.withheld(null, "NOT_REACHED"),
                    auditContext
                );
            }
            persistence.recordPolicyHarness(executionId, policyHarnessBinding);
            var providerRequest = externalSchemaMapper.map(destinationProfile, outboundPayload);
            persistence.recordProviderRequest(executionId, destinationProfile, providerRequest);
            updateStatus(executionId, RuntimeExecutionStatus.EGRESSING);
            ConnectorResult connectorResult = runtimeConnector.execute(
                requestContext,
                decision,
                outboundPayload,
                providerRequest
            );
            persistence.recordConnector(executionId, connectorResult);
            ResponseGuardResult responseGuardResult = responseGuard.guard(outboundPayload, connectorResult);
            persistence.recordResponseGuard(executionId, connectorResult, responseGuardResult);
            ExecutionPackOutcome outcome = controlledDeliveryService.resolve(
                destinationProfile.packType(), executionId, providerRequest, connectorResult, responseGuardResult
            );
            ControlledDeliveryResult controlledDelivery = outcome.controlledDelivery();
            persistence.recordControlledDelivery(executionId, controlledDelivery);
            updateStatus(executionId, outcome.runtimeStatus());
            AuditContext auditContext = auditRecorder.record(executionId, requestContext, decision, connectorResult);

            return new RuntimeExecutionResult(
                executionId,
                outcome.runtimeStatus(),
                decision,
                transformResult,
                outboundGuardResult.status(),
                connectorResult,
                responseGuardResult.status(),
                controlledDelivery,
                auditContext
            );
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (DestinationProfileNotFoundException | ApprovalScopeNotFoundException
            | ExecutionPackInputRejectedException | OutboundGuardException exception) {
            updateStatus(executionId, RuntimeExecutionStatus.BLOCKED);
            throw exception;
        } catch (RuntimeException exception) {
            updateStatus(executionId, RuntimeExecutionStatus.FAILED);
            throw exception;
        }
    }

    private void validateAuthorization(
        RuntimeRequestContext requestContext,
        AuthPrincipal principal,
        String institutionId,
        SubjectRef subject
    ) {
        if (principal.institutionId() == null || !principal.institutionId().equals(institutionId)) {
            throw new AccessDeniedException("Runtime institution is not allowed");
        }
        if (!authorizationService.authorize(new AuthorizationRequest(
            principal,
            requestContext.workloadId(),
            RuntimeAction.RUNTIME_EXECUTE,
            requestContext.purpose(),
            subject
        )).allowed()) {
            throw new AccessDeniedException("Runtime execution is not allowed");
        }
    }

    public RuntimeExecutionSubmission submit(
        RuntimeRequestContext requestContext,
        AuthPrincipal principal,
        String institutionId,
        String approvalReference,
        String destinationProfileId,
        List<String> processingContexts,
        Map<String, Object> input
    ) {
        try {
            return RuntimeExecutionSubmission.created(execute(
                requestContext,
                principal,
                institutionId,
                approvalReference,
                destinationProfileId,
                processingContexts,
                input
            ));
        } catch (DuplicateRuntimeExecutionException exception) {
            SubjectRef subject = SubjectRef.from(requestContext.subject());
            if (principal.institutionId() == null
                || !principal.institutionId().equals(institutionId)
                || !authorizationService.authorize(new AuthorizationRequest(
                    principal,
                    requestContext.workloadId(),
                    RuntimeAction.RUNTIME_EXECUTE,
                    requestContext.purpose(),
                    subject
                )).allowed()) {
                throw new AccessDeniedException("Runtime execution replay is not allowed");
            }
            String requestHash = runtimeRequestHasher.hash(
                institutionId,
                approvalReference,
                requestContext.workloadId(),
                requestContext.purpose(),
                requestContext.subject(),
                destinationProfileId,
                processingContexts,
                input
            );
            IdempotentExecutionReplay replay = persistence.findIdempotentExecution(
                    institutionId,
                    requestContext.workloadId(),
                    requestContext.idempotencyKey()
                )
                .orElseThrow(() -> exception);
            if (!replay.requestHash().equals(requestHash)) {
                recordIdempotency(IdempotencyOutcome.CONFLICT);
                throw new IdempotencyKeyConflictException();
            }
            if (replay.inProgress()) {
                recordIdempotency(IdempotencyOutcome.IN_PROGRESS);
                throw new IdempotencyRequestInProgressException();
            }
            recordIdempotency(IdempotencyOutcome.REPLAY);
            return RuntimeExecutionSubmission.replayed(replay);
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

    private void updateStatus(String executionId, RuntimeExecutionStatus status) {
        persistence.updateStatus(executionId, status);
        if (isTerminal(status)) {
            observability.runtimeExecution(status);
            log.atInfo()
                .addKeyValue("event", "runtime_execution_transition")
                .addKeyValue("status", status.name())
                .log("Runtime execution reached terminal status");
        }
    }

    private void recordIdempotency(IdempotencyOutcome outcome) {
        observability.idempotency(outcome);
        log.atInfo()
            .addKeyValue("event", "idempotency_resolution")
            .addKeyValue("outcome", outcome.name())
            .log("Runtime idempotency resolved");
    }

    private boolean isTerminal(RuntimeExecutionStatus status) {
        return status == RuntimeExecutionStatus.EXTERNALLY_RECONCILED
            || status == RuntimeExecutionStatus.COMPLETED
            || status == RuntimeExecutionStatus.REVIEW_REQUIRED
            || status == RuntimeExecutionStatus.BLOCKED
            || status == RuntimeExecutionStatus.FAILED;
    }
}
