package com.adp.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.adp.gateway.audit.application.AuditRecorder;
import com.adp.gateway.ai.application.AiCanonicalContextBuilder;
import com.adp.gateway.ai.infrastructure.AiExternalSchemaMapper;
import com.adp.gateway.auth.application.AuthorizationDecision;
import com.adp.gateway.auth.application.AuthorizationService;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.application.RuntimeConnectorResolver;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalContextBuilder;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.application.ExecutionPackContextBuilderResolver;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.decision.application.RuntimeDecisionService;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.application.DestinationProfilePort;
import com.adp.gateway.egress.application.ExternalSchemaMapperResolver;
import com.adp.gateway.egress.application.OutboundCandidatePayloadBuilder;
import com.adp.gateway.egress.application.OutboundGuardChain;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.application.ResponseGuardResolver;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.detection.infrastructure.RegexSensitiveDataDetector;
import com.adp.gateway.policyharness.application.FieldLineageFactory;
import com.adp.gateway.policyharness.application.PolicyHarnessEvaluator;
import com.adp.gateway.policyharness.infrastructure.ProjectProvisionalApprovalScopeAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.adp.gateway.policy.application.PolicyApplicabilityEvaluator;
import com.adp.gateway.policy.application.RuntimePolicyContextFactory;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySelectionContext;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.retrieval.application.RetrievalService;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.transform.application.TransformEngine;
import org.junit.jupiter.api.Test;

class RuntimeExecutionServiceTests {

    private RuntimeExecutionService service(
        AuthorizationService authorizationService,
        RetrievalService retrievalService,
        CanonicalContextBuilder contextBuilder,
        RuntimePolicyContextFactory runtimePolicyContextFactory,
        PolicySnapshotPort policySnapshotPort,
        PolicyApplicabilityEvaluator applicabilityEvaluator,
        RuntimeDecisionService decisionService,
        RuntimeConnectorPort connector,
        AuditRecorder auditRecorder,
        RuntimeExecutionPersistence persistence,
        SubjectRefHasher subjectRefHasher,
        RuntimeInputHasher runtimeInputHasher,
        TransformEngine transformEngine,
        DestinationProfilePort destinationProfilePort,
        OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder,
        OutboundGuardChain outboundGuardChain,
        ResponseGuardPort responseGuardPort,
        Clock clock
    ) {
        CanonicalValueHasher hasher = new CanonicalValueHasher();
        when(connector.supportedPack()).thenReturn(ExecutionPackType.COMMON);
        when(responseGuardPort.supportedPack()).thenReturn(ExecutionPackType.AI);
        return new RuntimeExecutionService(
            authorizationService,
            retrievalService,
            contextBuilder,
            runtimePolicyContextFactory,
            policySnapshotPort,
            applicabilityEvaluator,
            decisionService,
            new RuntimeConnectorResolver(List.of(connector)),
            auditRecorder,
            persistence,
            subjectRefHasher,
            runtimeInputHasher,
            new RuntimeRequestHasher(new ObjectMapper()),
            transformEngine,
            destinationProfilePort,
            outboundCandidatePayloadBuilder,
            outboundGuardChain,
            new ResponseGuardResolver(List.of(responseGuardPort)),
            new ExecutionPackContextBuilderResolver(List.of(
                new AiCanonicalContextBuilder(hasher, new RegexSensitiveDataDetector(hasher))
            )),
            new ProjectProvisionalApprovalScopeAdapter(subjectRefHasher),
            new FieldLineageFactory(hasher),
            new PolicyHarnessEvaluator(hasher),
            new ExternalSchemaMapperResolver(List.of(new AiExternalSchemaMapper(new ObjectMapper(), hasher))),
            new ControlledDeliveryService(),
            clock,
            mock(com.adp.gateway.observability.GatewayObservability.class)
        );
    }

    @Test
    void transformFailureMarksRuntimeFailedAndDoesNotExecuteConnector() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        RetrievalService retrievalService = mock(RetrievalService.class);
        CanonicalContextBuilder contextBuilder = mock(CanonicalContextBuilder.class);
        RuntimePolicyContextFactory runtimePolicyContextFactory = mock(RuntimePolicyContextFactory.class);
        PolicySnapshotPort policySnapshotPort = mock(PolicySnapshotPort.class);
        PolicyApplicabilityEvaluator applicabilityEvaluator = mock(PolicyApplicabilityEvaluator.class);
        RuntimeDecisionService decisionService = mock(RuntimeDecisionService.class);
        RuntimeConnectorPort connector = mock(RuntimeConnectorPort.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        RuntimeExecutionPersistence persistence = mock(RuntimeExecutionPersistence.class);
        SubjectRefHasher subjectRefHasher = mock(SubjectRefHasher.class);
        RuntimeInputHasher runtimeInputHasher = mock(RuntimeInputHasher.class);
        TransformEngine transformEngine = mock(TransformEngine.class);
        DestinationProfilePort destinationProfilePort = mock(DestinationProfilePort.class);
        OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder = mock(OutboundCandidatePayloadBuilder.class);
        OutboundGuardChain outboundGuardChain = mock(OutboundGuardChain.class);
        ResponseGuardPort responseGuardPort = mock(ResponseGuardPort.class);
        RuntimeExecutionService service = service(
            authorizationService,
            retrievalService,
            contextBuilder,
            runtimePolicyContextFactory,
            policySnapshotPort,
            applicabilityEvaluator,
            decisionService,
            connector,
            auditRecorder,
            persistence,
            subjectRefHasher,
            runtimeInputHasher,
            transformEngine,
            destinationProfilePort,
            outboundCandidatePayloadBuilder,
            outboundGuardChain,
            responseGuardPort,
            Clock.fixed(java.time.Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
        RuntimeRequestContext request = new RuntimeRequestContext(
            "req_test",
            "trace_test",
            "idem_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer:customer-100"
        );
        AuthPrincipal principal = new AuthPrincipal(
            "principal_test",
            PrincipalType.SERVICE,
            "Runtime Service",
            "institution_local",
            false,
            Set.of("customer_summary"),
            Set.of(AdpRole.RUNTIME_EXECUTOR)
        );
        RuntimePolicyContext policyContext = policyContext();
        RuntimeDecision decision = mock(RuntimeDecision.class);
        when(authorizationService.authorize(any())).thenReturn(AuthorizationDecision.allow());
        when(destinationProfilePort.load(any(), any())).thenReturn(destinationProfile());
        when(runtimeInputHasher.hash(any())).thenReturn("input_digest");
        when(subjectRefHasher.hash(any())).thenReturn("subject_digest");
        when(retrievalService.retrieve(any())).thenReturn(retrieval());
        when(contextBuilder.build(any())).thenReturn(canonicalContext());
        when(runtimePolicyContextFactory.from(any(), any(), any(), any())).thenReturn(policyContext);
        when(policySnapshotPort.load(any(PolicySelectionContext.class))).thenReturn(snapshot());
        when(applicabilityEvaluator.evaluate(any(), any())).thenReturn(ApplicabilityResult.APPLICABLE);
        when(decisionService.decide(any(), any(), any(), any())).thenReturn(decision);
        when(transformEngine.transform(any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("vault unavailable"));

        assertThatThrownBy(() -> service.execute(
            request,
            principal,
            "institution_local",
            ProjectProvisionalApprovalScopeAdapter.APPROVAL_REFERENCE,
            "dest_internal_provider_project_provisional",
            List.of("AI_USE"),
            Map.of("prompt", "safe question")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("vault unavailable");

        verify(persistence).updateStatus(any(), org.mockito.ArgumentMatchers.eq(RuntimeExecutionStatus.FAILED));
        verify(connector, never()).execute(any(), any(), any(), any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void reviewDecisionDoesNotCallOutboundOrConnector() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        RetrievalService retrievalService = mock(RetrievalService.class);
        CanonicalContextBuilder contextBuilder = mock(CanonicalContextBuilder.class);
        RuntimePolicyContextFactory runtimePolicyContextFactory = mock(RuntimePolicyContextFactory.class);
        PolicySnapshotPort policySnapshotPort = mock(PolicySnapshotPort.class);
        PolicyApplicabilityEvaluator applicabilityEvaluator = mock(PolicyApplicabilityEvaluator.class);
        RuntimeDecisionService decisionService = mock(RuntimeDecisionService.class);
        RuntimeConnectorPort connector = mock(RuntimeConnectorPort.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        RuntimeExecutionPersistence persistence = mock(RuntimeExecutionPersistence.class);
        SubjectRefHasher subjectRefHasher = mock(SubjectRefHasher.class);
        RuntimeInputHasher runtimeInputHasher = mock(RuntimeInputHasher.class);
        TransformEngine transformEngine = mock(TransformEngine.class);
        DestinationProfilePort destinationProfilePort = mock(DestinationProfilePort.class);
        OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder = mock(OutboundCandidatePayloadBuilder.class);
        OutboundGuardChain outboundGuardChain = mock(OutboundGuardChain.class);
        ResponseGuardPort responseGuardPort = mock(ResponseGuardPort.class);
        RuntimeExecutionService service = service(
            authorizationService,
            retrievalService,
            contextBuilder,
            runtimePolicyContextFactory,
            policySnapshotPort,
            applicabilityEvaluator,
            decisionService,
            connector,
            auditRecorder,
            persistence,
            subjectRefHasher,
            runtimeInputHasher,
            transformEngine,
            destinationProfilePort,
            outboundCandidatePayloadBuilder,
            outboundGuardChain,
            responseGuardPort,
            Clock.fixed(java.time.Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
        RuntimeDecision decision = mock(RuntimeDecision.class);
        when(decision.finalAction()).thenReturn(FinalAction.REVIEW);
        when(authorizationService.authorize(any())).thenReturn(AuthorizationDecision.allow());
        when(destinationProfilePort.load(any(), any())).thenReturn(destinationProfile());
        when(runtimeInputHasher.hash(any())).thenReturn("input_digest");
        when(subjectRefHasher.hash(any())).thenReturn("subject_digest");
        when(retrievalService.retrieve(any())).thenReturn(retrieval());
        when(contextBuilder.build(any())).thenReturn(canonicalContext());
        when(runtimePolicyContextFactory.from(any(), any(), any(), any())).thenReturn(policyContext());
        when(policySnapshotPort.load(any(PolicySelectionContext.class))).thenReturn(snapshot());
        when(applicabilityEvaluator.evaluate(any(), any())).thenReturn(ApplicabilityResult.APPLICABLE);
        when(decisionService.decide(any(), any(), any(), any())).thenReturn(decision);
        when(transformEngine.transform(any(), any(), any(), any()))
            .thenReturn(com.adp.gateway.transform.domain.TransformResult.skipped("trn_skipped"));
        when(auditRecorder.record(any(), any(), any(), any())).thenReturn(auditContext());

        service.execute(
            request(),
            principal(),
            "institution_local",
            ProjectProvisionalApprovalScopeAdapter.APPROVAL_REFERENCE,
            "dest_internal_provider_project_provisional",
            List.of("AI_USE"),
            Map.of("prompt", "safe question")
        );

        verify(outboundCandidatePayloadBuilder, never()).build(any(), any(), any(), any());
        verify(outboundGuardChain, never()).guard(any(), any(), any(), any(), any(), any());
        verify(connector, never()).execute(any(), any(), any(), any());
    }

    @Test
    void deniedAuthorizationDoesNotLoadDestinationProfileOrReadData() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        RetrievalService retrievalService = mock(RetrievalService.class);
        CanonicalContextBuilder contextBuilder = mock(CanonicalContextBuilder.class);
        RuntimePolicyContextFactory runtimePolicyContextFactory = mock(RuntimePolicyContextFactory.class);
        PolicySnapshotPort policySnapshotPort = mock(PolicySnapshotPort.class);
        PolicyApplicabilityEvaluator applicabilityEvaluator = mock(PolicyApplicabilityEvaluator.class);
        RuntimeDecisionService decisionService = mock(RuntimeDecisionService.class);
        RuntimeConnectorPort connector = mock(RuntimeConnectorPort.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        RuntimeExecutionPersistence persistence = mock(RuntimeExecutionPersistence.class);
        SubjectRefHasher subjectRefHasher = mock(SubjectRefHasher.class);
        RuntimeInputHasher runtimeInputHasher = mock(RuntimeInputHasher.class);
        TransformEngine transformEngine = mock(TransformEngine.class);
        DestinationProfilePort destinationProfilePort = mock(DestinationProfilePort.class);
        OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder = mock(OutboundCandidatePayloadBuilder.class);
        OutboundGuardChain outboundGuardChain = mock(OutboundGuardChain.class);
        ResponseGuardPort responseGuardPort = mock(ResponseGuardPort.class);
        RuntimeExecutionService service = service(
            authorizationService,
            retrievalService,
            contextBuilder,
            runtimePolicyContextFactory,
            policySnapshotPort,
            applicabilityEvaluator,
            decisionService,
            connector,
            auditRecorder,
            persistence,
            subjectRefHasher,
            runtimeInputHasher,
            transformEngine,
            destinationProfilePort,
            outboundCandidatePayloadBuilder,
            outboundGuardChain,
            responseGuardPort,
            Clock.fixed(java.time.Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
        when(runtimeInputHasher.hash(any())).thenReturn("input_digest");
        when(subjectRefHasher.hash(any())).thenReturn("subject_digest");
        when(authorizationService.authorize(any())).thenReturn(AuthorizationDecision.deny());

        assertThatThrownBy(() -> service.execute(
            request(),
            principal(),
            "institution_local",
            ProjectProvisionalApprovalScopeAdapter.APPROVAL_REFERENCE,
            "dest_internal_provider_project_provisional",
            List.of("AI_USE"),
            Map.of("prompt", "safe question")
        )).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(persistence, never()).recordReceived(any(), any(), any());
        verify(destinationProfilePort, never()).load(any(), any());
        verify(retrievalService, never()).retrieve(any());
        verify(outboundCandidatePayloadBuilder, never()).build(any(), any(), any(), any());
        verify(connector, never()).execute(any(), any(), any(), any());
    }

    @Test
    void connectorFailureMarksRuntimeFailedInsteadOfBlocked() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        RetrievalService retrievalService = mock(RetrievalService.class);
        CanonicalContextBuilder contextBuilder = mock(CanonicalContextBuilder.class);
        RuntimePolicyContextFactory runtimePolicyContextFactory = mock(RuntimePolicyContextFactory.class);
        PolicySnapshotPort policySnapshotPort = mock(PolicySnapshotPort.class);
        PolicyApplicabilityEvaluator applicabilityEvaluator = mock(PolicyApplicabilityEvaluator.class);
        RuntimeDecisionService decisionService = mock(RuntimeDecisionService.class);
        RuntimeConnectorPort connector = mock(RuntimeConnectorPort.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        RuntimeExecutionPersistence persistence = mock(RuntimeExecutionPersistence.class);
        SubjectRefHasher subjectRefHasher = mock(SubjectRefHasher.class);
        RuntimeInputHasher runtimeInputHasher = mock(RuntimeInputHasher.class);
        TransformEngine transformEngine = mock(TransformEngine.class);
        DestinationProfilePort destinationProfilePort = mock(DestinationProfilePort.class);
        OutboundCandidatePayloadBuilder outboundCandidatePayloadBuilder = mock(OutboundCandidatePayloadBuilder.class);
        OutboundGuardChain outboundGuardChain = mock(OutboundGuardChain.class);
        ResponseGuardPort responseGuardPort = mock(ResponseGuardPort.class);
        RuntimeExecutionService service = service(
            authorizationService,
            retrievalService,
            contextBuilder,
            runtimePolicyContextFactory,
            policySnapshotPort,
            applicabilityEvaluator,
            decisionService,
            connector,
            auditRecorder,
            persistence,
            subjectRefHasher,
            runtimeInputHasher,
            transformEngine,
            destinationProfilePort,
            outboundCandidatePayloadBuilder,
            outboundGuardChain,
            responseGuardPort,
            Clock.fixed(java.time.Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
        RuntimeDecision decision = mock(RuntimeDecision.class);
        OutboundCandidatePayload payload = outboundPayload();
        ConnectorResult connectorResult = new ConnectorResult(
            "con_failed",
            "test-connector",
            ConnectorStatus.FAILED,
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            null,
            null
        );
        when(decision.finalAction()).thenReturn(FinalAction.TRANSFORM);
        when(authorizationService.authorize(any())).thenReturn(AuthorizationDecision.allow());
        when(destinationProfilePort.load(any(), any())).thenReturn(destinationProfile());
        when(runtimeInputHasher.hash(any())).thenReturn("input_digest");
        when(subjectRefHasher.hash(any())).thenReturn("subject_digest");
        when(retrievalService.retrieve(any())).thenReturn(retrieval());
        when(contextBuilder.build(any())).thenReturn(canonicalContext());
        when(runtimePolicyContextFactory.from(any(), any(), any(), any())).thenReturn(policyContext());
        when(policySnapshotPort.load(any(PolicySelectionContext.class))).thenReturn(snapshot());
        when(applicabilityEvaluator.evaluate(any(), any())).thenReturn(ApplicabilityResult.APPLICABLE);
        when(decisionService.decide(any(), any(), any(), any())).thenReturn(decision);
        when(transformEngine.transform(any(), any(), any(), any()))
            .thenReturn(new com.adp.gateway.transform.domain.TransformResult(
                "trn_applied",
                true,
                "APPLIED",
                "transform_output_digest",
                List.of()
            ));
        when(outboundCandidatePayloadBuilder.build(any(), any(), any(), any())).thenReturn(payload);
        when(outboundGuardChain.guard(any(), any(), any(), any(), any(), any()))
            .thenReturn(OutboundGuardResult.passed());
        when(connector.execute(any(), any(), any(), any())).thenReturn(connectorResult);
        when(responseGuardPort.guard(any(), any())).thenReturn(ResponseGuardResult.notEvaluated(List.of("CONNECTOR_NOT_EXECUTED")));
        when(auditRecorder.record(any(), any(), any(), any())).thenReturn(auditContext());

        RuntimeExecutionResult result = service.execute(
            request(),
            principal(),
            "institution_local",
            ProjectProvisionalApprovalScopeAdapter.APPROVAL_REFERENCE,
            "dest_internal_provider_project_provisional",
            List.of("AI_USE"),
            Map.of("prompt", "safe question")
        );

        org.assertj.core.api.Assertions.assertThat(result.status()).isEqualTo(RuntimeExecutionStatus.FAILED);
        org.assertj.core.api.Assertions.assertThat(result.connectorResult().status()).isEqualTo(ConnectorStatus.FAILED);
        org.assertj.core.api.Assertions.assertThat(result.responseGuardStatus()).isEqualTo("NOT_EVALUATED");
        verify(persistence).recordConnector(any(), org.mockito.ArgumentMatchers.eq(connectorResult));
        verify(persistence).recordResponseGuard(
            any(),
            org.mockito.ArgumentMatchers.eq(connectorResult),
            org.mockito.ArgumentMatchers.any(ResponseGuardResult.class)
        );
        verify(persistence).updateStatus(any(), org.mockito.ArgumentMatchers.eq(RuntimeExecutionStatus.FAILED));
    }

    private RetrievalResult retrieval() {
        return new RetrievalResult(
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "customer-100",
            "profile_test",
            1,
            List.of(),
            List.of(),
            List.of()
        );
    }

    private RuntimeRequestContext request() {
        return new RuntimeRequestContext(
            "req_test",
            "trace_test",
            "idem_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer:customer-100"
        );
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
            "principal_test",
            PrincipalType.SERVICE,
            "Runtime Service",
            "institution_local",
            false,
            Set.of("customer_summary"),
            Set.of(AdpRole.RUNTIME_EXECUTOR)
        );
    }

    private DestinationProfile destinationProfile() {
        return new DestinationProfile(
            "dest_internal_provider_project_provisional",
            "0.0.0",
            "local-fixture-destination-profile",
            "be-egress-contract/0.0.0",
            "internal-provider",
            ExecutionPackType.AI,
            "project-provisional-egress-schema-v1",
            "ACTIVE",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
            List.of()
        );
    }

    private OutboundCandidatePayload outboundPayload() {
        return new OutboundCandidatePayload(
            "out_test",
            "dest_internal_provider_project_provisional",
            "0.0.0",
            "local-fixture-destination-profile",
            ExecutionPackType.AI,
            "project-provisional-egress-schema-v1",
            "candidate_digest",
            List.of()
        );
    }

    private com.adp.gateway.audit.domain.AuditContext auditContext() {
        return new com.adp.gateway.audit.domain.AuditContext(
            "aud_test",
            "req_test",
            "trace_test",
            "idem_test",
            "customer_summary",
            "decision_test",
            "artifact",
            "v1",
            "sha256",
            "digest",
            "REVIEW",
            "REVIEW",
            "ALLOWED",
            "APPLICABLE",
            "runtime_digest",
            "",
            "",
            "",
            "policy-v1",
            "snapshot_digest",
            "",
            "NOT_SENT",
            OffsetDateTime.parse("2026-09-01T00:00:00Z")
        );
    }

    private CanonicalContext canonicalContext() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_test",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(new CanonicalContextField(
                "customer.customer_id",
                "customer",
                "customer_id",
                DataClass.CUSTOMER_IDENTIFIER,
                "customer-100",
                "value_digest"
            )),
            "canonical_digest"
        );
    }

    private RuntimePolicyContext policyContext() {
        return new RuntimePolicyContext(
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            "canonical_digest",
            List.of(DataClass.CUSTOMER_IDENTIFIER),
            List.of("AI_USE"),
            "internal-provider",
            "input_digest",
            "runtime_digest"
        );
    }

    private PolicySnapshot snapshot() {
        return new PolicySnapshot(
            "be-runtime-policy/0.0.0",
            "be-snapshot-local-fixture:customer-summary:customer-support:internal-provider",
            OffsetDateTime.parse("2026-09-01T00:00:00Z"),
            PolicyLifecycleStage.ACTIVE,
            new SourcePolicyEvaluationArtifactRef("artifact", "v1", new ArtifactDigest("sha256", "digest")),
            new PolicyEvaluation(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                PolicyAction.TRANSFORM,
                List.of(),
                List.of(),
                new PolicyApplicabilitySpec(
                    com.adp.gateway.policy.domain.AnalysisStatus.VALIDATED,
                    com.adp.gateway.policy.domain.AnalysisStatus.VALIDATED,
                    "scope",
                    List.of(),
                    List.of("AI_USE"),
                    List.of("CUSTOMER_IDENTIFIER"),
                    new RuntimeBinding(
                        "MAPPED",
                        "CUSTOMER_IDENTIFIER",
                        "customer_summary",
                        "CUSTOMER_SUPPORT",
                        "binding"
                    )
                )
            )
        );
    }
}
