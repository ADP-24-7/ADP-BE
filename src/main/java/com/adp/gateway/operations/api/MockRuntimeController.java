package com.adp.gateway.operations.api;

import java.util.List;
import java.util.stream.Collectors;

import com.adp.gateway.audit.application.AuditRecorder;
import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.auth.application.AuthorizationRequest;
import com.adp.gateway.auth.application.AuthorizationService;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.trace.RuntimeContextFactory;
import com.adp.gateway.connector.application.FakeConnector;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.application.RuntimeDecisionService;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.application.PolicyApplicabilityEvaluator;
import com.adp.gateway.policy.application.RuntimePolicyContextFactory;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicySelectionContext;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class MockRuntimeController {

    private final RuntimeContextFactory runtimeContextFactory;
    private final PolicySnapshotPort policySnapshotPort;
    private final RuntimeDecisionService runtimeDecisionService;
    private final FakeConnector fakeConnector;
    private final AuditRecorder auditRecorder;
    private final AuthorizationService authorizationService;
    private final RuntimePolicyContextFactory runtimePolicyContextFactory;
    private final PolicyApplicabilityEvaluator policyApplicabilityEvaluator;

    public MockRuntimeController(
        RuntimeContextFactory runtimeContextFactory,
        PolicySnapshotPort policySnapshotPort,
        RuntimeDecisionService runtimeDecisionService,
        FakeConnector fakeConnector,
        AuditRecorder auditRecorder,
        AuthorizationService authorizationService,
        RuntimePolicyContextFactory runtimePolicyContextFactory,
        PolicyApplicabilityEvaluator policyApplicabilityEvaluator
    ) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.policySnapshotPort = policySnapshotPort;
        this.runtimeDecisionService = runtimeDecisionService;
        this.fakeConnector = fakeConnector;
        this.auditRecorder = auditRecorder;
        this.authorizationService = authorizationService;
        this.runtimePolicyContextFactory = runtimePolicyContextFactory;
        this.policyApplicabilityEvaluator = policyApplicabilityEvaluator;
    }

    @PostMapping("/mock")
    public ResponseEntity<MockRuntimeResponse> execute(
        @Valid @RequestBody MockRuntimeRequest request,
        HttpServletRequest httpRequest,
        Authentication authentication
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (!authorizationService.authorize(new AuthorizationRequest(
            principal,
            request.workloadId(),
            RuntimeAction.RUNTIME_EXECUTE,
            request.purpose(),
            SubjectRef.from(request.subject())
        )).allowed()) {
            throw new AccessDeniedException("Runtime execution is not allowed");
        }

        RuntimeRequestContext context = runtimeContextFactory.create(
            httpRequest,
            request.workloadId(),
            request.purpose(),
            request.subject()
        );
        RuntimePolicyContext runtimePolicyContext = runtimePolicyContextFactory.from(context);
        PolicySnapshot snapshot = policySnapshotPort.load(new PolicySelectionContext(
            runtimePolicyContext.workloadId(),
            runtimePolicyContext.purpose(),
            runtimePolicyContext.provider(),
            runtimePolicyContext.processingContexts(),
            runtimePolicyContext.runtimeDataClasses()
        ));
        ApplicabilityResult applicabilityResult = policyApplicabilityEvaluator.evaluate(snapshot, runtimePolicyContext);
        RuntimeDecision decision = runtimeDecisionService.decide(
            runtimePolicyContext,
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            applicabilityResult
        );
        ConnectorResult connector = fakeConnector.execute(context, decision);
        AuditContext audit = auditRecorder.record(context, decision, connector);

        return ResponseEntity.ok(new MockRuntimeResponse(
            context.requestId(),
            context.traceId(),
            context.idempotencyKey(),
            snapshot.sourcePolicyEvaluationArtifactRef().artifactId(),
            snapshot.sourcePolicyEvaluationArtifactRef().artifactVersion(),
            snapshot.sourcePolicyEvaluationArtifactRef().artifactDigest().algorithm(),
            snapshot.sourcePolicyEvaluationArtifactRef().artifactDigest().value(),
            snapshot.lifecycleStage().name(),
            snapshot.policyVersion(),
            snapshot.snapshotDigest(),
            decision.decisionId(),
            decision.policyAction().name(),
            decision.finalAction().name(),
            decision.authorizationResult().name(),
            decision.applicabilityResult().name(),
            decision.runtimeContextDigest(),
            auditValue(decision.matchedRuleRefs()),
            auditValue(decision.evidenceRefs()),
            auditValue(decision.requiredControls()),
            decision.finalAction().name(),
            decision.primaryReasonCode().name(),
            connector.status(),
            audit.auditId()
        ));
    }

    private String auditValue(List<ArtifactReference> references) {
        return references.stream()
            .map(ArtifactReference::auditValue)
            .collect(Collectors.joining(","));
    }
}
