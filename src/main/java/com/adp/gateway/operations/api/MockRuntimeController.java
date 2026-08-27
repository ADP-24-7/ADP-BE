package com.adp.gateway.operations.api;

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
import com.adp.gateway.decision.application.FakeDecisionService;
import com.adp.gateway.decision.domain.DecisionResult;
import com.adp.gateway.policy.domain.PolicyArtifact;
import com.adp.gateway.policy.domain.PolicyArtifactPort;
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
    private final PolicyArtifactPort policyArtifactPort;
    private final FakeDecisionService fakeDecisionService;
    private final FakeConnector fakeConnector;
    private final AuditRecorder auditRecorder;
    private final AuthorizationService authorizationService;

    public MockRuntimeController(
        RuntimeContextFactory runtimeContextFactory,
        PolicyArtifactPort policyArtifactPort,
        FakeDecisionService fakeDecisionService,
        FakeConnector fakeConnector,
        AuditRecorder auditRecorder,
        AuthorizationService authorizationService
    ) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.policyArtifactPort = policyArtifactPort;
        this.fakeDecisionService = fakeDecisionService;
        this.fakeConnector = fakeConnector;
        this.auditRecorder = auditRecorder;
        this.authorizationService = authorizationService;
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
        PolicyArtifact artifact = policyArtifactPort.load(context.workloadId());
        DecisionResult decision = fakeDecisionService.evaluate(context, artifact);
        ConnectorResult connector = fakeConnector.execute(context, decision);
        AuditContext audit = auditRecorder.record(context, decision, connector);

        return ResponseEntity.ok(new MockRuntimeResponse(
            context.requestId(),
            context.traceId(),
            context.idempotencyKey(),
            artifact.artifactId(),
            artifact.status().name(),
            artifact.artifactVersion(),
            artifact.digest(),
            decision.decisionId(),
            decision.outcome(),
            decision.reasonCode().name(),
            connector.status(),
            audit.auditId()
        ));
    }
}
