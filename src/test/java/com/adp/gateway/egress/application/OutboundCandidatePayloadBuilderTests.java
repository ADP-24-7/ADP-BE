package com.adp.gateway.egress.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationFieldContract;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformResult;
import org.junit.jupiter.api.Test;

class OutboundCandidatePayloadBuilderTests {

    private final OutboundCandidatePayloadBuilder builder = new OutboundCandidatePayloadBuilder(
        providerProfileId -> new DestinationProfile(
            "dest_test",
            "v1",
            "profile_digest",
            "contract-v1",
            providerProfileId,
            ExecutionPackType.AI,
            "schema-v1",
            "ACTIVE",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
            List.of(new DestinationFieldContract(
                "customer.segment",
                DataClass.BUSINESS_METADATA,
                FieldObligation.CONDITIONAL_EXACT,
                true,
                true
            ))
        ),
        new CanonicalValueHasher()
    );

    @Test
    void allowDecisionBuildsCandidateFromCanonicalContextWhenTransformSkipped() {
        var payload = builder.build(
            request(),
            "internal-provider",
            canonicalContext(),
            decision(FinalAction.ALLOW),
            TransformResult.skipped("trn_skipped")
        );

        assertThat(payload.destinationProfileVersion()).isEqualTo("v1");
        assertThat(payload.destinationProfileDigest()).isEqualTo("profile_digest");
        assertThat(payload.candidatePayloadDigest()).isNotBlank();
        assertThat(payload.fields()).hasSize(1);
        assertThat(payload.fields().getFirst().treatment()).isEqualTo(FieldTreatment.KEEP_EXACT_PROTECTED);
        assertThat(payload.fields().getFirst().obligation()).isEqualTo(FieldObligation.CONDITIONAL_EXACT);
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
                "$.records[0].customer.segment",
                "customer",
                "segment",
                DataClass.BUSINESS_METADATA,
                "preferred",
                "value_digest"
            )),
            "canonical_digest"
        );
    }

    private RuntimeDecision decision(FinalAction finalAction) {
        return new RuntimeDecision(
            "decision_test",
            PolicyAction.ALLOW,
            finalAction,
            List.of(com.adp.gateway.common.error.ReasonCode.POLICY_ALLOW),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE,
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
}
