package com.adp.gateway.egress.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationFieldContract;
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

    private final OutboundGuardChain guardChain = new OutboundGuardChain();
    private final DestinationProfile profile = new DestinationProfile(
        "dest_test",
        "v1",
        "profile_digest",
        "contract-v1",
        "internal-provider",
        ExecutionPackType.AI,
        "schema-v1",
        "ACTIVE",
        java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        null,
        List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
        List.of(
            new DestinationFieldContract("customer.customer_id", DataClass.CUSTOMER_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, false, false),
            new DestinationFieldContract("customer.segment", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("credential.api_key", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, false, true)
        )
    );

    @Test
    void rejectsReviewDecisionBeforeConnectorBoundary() {
        var result = guardChain.guard(profile, "customer_summary", "CUSTOMER_SUPPORT", requestTime(), decision(FinalAction.REVIEW), payload(metadataField()));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("FINAL_ACTION_NOT_EGRESSIBLE");
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

        var result = guardChain.guard(profile, "customer_summary", "CUSTOMER_SUPPORT", requestTime(), decision(FinalAction.TRANSFORM), payload(rawSensitive));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("UNAPPROVED_RAW_FIELD_PRESENT");
    }

    @Test
    void rejectsSchemaPackAndRequiredFieldFailures() {
        OutboundCandidatePayload invalidPayload = new OutboundCandidatePayload(
            "out_test",
            "dest_test",
            "v1",
            "profile_digest",
            ExecutionPackType.DIGITAL_ASSET,
            "schema-v2",
            "payload_digest",
            List.of()
        );

        var result = guardChain.guard(profile, "customer_summary", "CUSTOMER_SUPPORT", requestTime(), decision(FinalAction.TRANSFORM), invalidPayload);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("PACK_TYPE_MISMATCH", "SCHEMA_VERSION_MISMATCH", "REQUIRED_FIELD_MISSING");
    }

    @Test
    void rejectsSecretExactPayload() {
        OutboundCandidateField secret = new OutboundCandidateField(
            "$.records[0].prompt.text",
            DataClass.BUSINESS_METADATA,
            TransformStrategy.KEEP,
            FieldObligation.CONDITIONAL_EXACT,
            FieldTreatment.KEEP_EXACT_PROTECTED,
            "digest",
            "sk-proj-1234567890abcdef"
        );

        var result = guardChain.guard(profile, "customer_summary", "CUSTOMER_SUPPORT", requestTime(), decision(FinalAction.TRANSFORM), payload(secret));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("DESTINATION_FIELD_CONTRACT_NOT_FOUND", "SECRET_FIELD_PRESENT");
    }

    @Test
    void rejectsExpiredDestinationProfile() {
        DestinationProfile expiredProfile = new DestinationProfile(
            "dest_test",
            "v1",
            "profile_digest",
            "contract-v1",
            "internal-provider",
            ExecutionPackType.AI,
            "schema-v1",
            "ACTIVE",
            java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            java.time.OffsetDateTime.parse("2026-02-01T00:00:00Z"),
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
            List.of(new DestinationFieldContract("customer.segment", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true))
        );

        var result = guardChain.guard(expiredProfile, "customer_summary", "CUSTOMER_SUPPORT", requestTime(), decision(FinalAction.TRANSFORM), payload(metadataField()));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("DESTINATION_PROFILE_NOT_EFFECTIVE", "DESTINATION_PROFILE_NOT_ALLOWED");
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

    private java.time.OffsetDateTime requestTime() {
        return java.time.OffsetDateTime.parse("2026-09-01T00:00:00Z");
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
            "v1",
            "profile_digest",
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
