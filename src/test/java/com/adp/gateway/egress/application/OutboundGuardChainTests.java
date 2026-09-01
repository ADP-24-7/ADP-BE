package com.adp.gateway.egress.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformStrategy;
import org.junit.jupiter.api.Test;

class OutboundGuardChainTests {

    private final OutboundGuardChain guardChain = new OutboundGuardChain(providerProfileId -> new DestinationProfile(
        "dest_test",
        providerProfileId,
        ExecutionPackType.AI,
        "schema-v1",
        true,
        Set.of("customer_summary"),
        Set.of("CUSTOMER_SUPPORT")
    ));

    @Test
    void rejectsReviewDecisionBeforeConnectorBoundary() {
        assertThatThrownBy(() -> guardChain.guard(request(), "internal-provider", decision(FinalAction.REVIEW), payload(metadataField())))
            .isInstanceOf(OutboundGuardException.class)
            .hasMessageContaining("Outbound guard rejected payload")
            .extracting("reasonCodes")
            .asList()
            .contains("FINAL_ACTION_NOT_EGRESSIBLE");
    }

    @Test
    void rejectsUnapprovedRawSensitiveField() {
        OutboundCandidateField rawSensitive = new OutboundCandidateField(
            "customer.customer_id",
            DataClass.CUSTOMER_IDENTIFIER,
            TransformStrategy.KEEP,
            FieldObligation.REQUIRED_EXACT,
            FieldTreatment.KEEP_EXACT_PROTECTED,
            "digest",
            "customer-100"
        );

        assertThatThrownBy(() -> guardChain.guard(request(), "internal-provider", decision(FinalAction.TRANSFORM), payload(rawSensitive)))
            .isInstanceOf(OutboundGuardException.class)
            .extracting("reasonCodes")
            .asList()
            .contains("UNAPPROVED_RAW_FIELD_PRESENT");
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

    private RuntimeDecision decision(FinalAction finalAction) {
        return new RuntimeDecision(
            "decision_test",
            PolicyAction.TRANSFORM,
            finalAction,
            List.of(com.adp.gateway.common.error.ReasonCode.POLICY_INCOMPLETE),
            com.adp.gateway.decision.domain.RuntimeAuthorizationResult.ALLOWED,
            com.adp.gateway.policy.domain.ApplicabilityResult.APPLICABLE,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "policy-v1",
            "snapshot_digest",
            "runtime_digest",
            new SourcePolicyEvaluationArtifactRef("artifact", "v1", new ArtifactDigest("sha256", "digest"))
        );
    }

    private OutboundCandidatePayload payload(OutboundCandidateField field) {
        return new OutboundCandidatePayload(
            "out_test",
            "dest_test",
            ExecutionPackType.AI,
            "schema-v1",
            "payload_digest",
            List.of(field)
        );
    }

    private OutboundCandidateField metadataField() {
        return new OutboundCandidateField(
            "customer.segment",
            DataClass.BUSINESS_METADATA,
            TransformStrategy.KEEP,
            FieldObligation.CONDITIONAL_EXACT,
            FieldTreatment.KEEP_EXACT_PROTECTED,
            "digest",
            "preferred"
        );
    }
}
