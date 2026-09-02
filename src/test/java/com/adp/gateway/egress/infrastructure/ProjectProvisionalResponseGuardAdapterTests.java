package com.adp.gateway.egress.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ProjectProvisionalResponseGuardAdapterTests {

    private final ProjectProvisionalResponseGuardAdapter responseGuard =
        new ProjectProvisionalResponseGuardAdapter(
            new SimpleMeterRegistry(),
            new RegexResponseLeakageDetector(new CanonicalValueHasher())
        );

    @Test
    void passesOnlyExpectedFixtureResponseMetadata() {
        OutboundCandidatePayload payload = payload();

        var result = responseGuard.guard(payload, new ConnectorResult(
            "con_test",
            "fake",
            ConnectorStatus.ACKNOWLEDGED,
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            "response-digest",
            "ai-provider-response/v1",
            Map.of("answer", "Approved context processed")
        ));

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.reasonCodes()).isEmpty();
    }

    @Test
    void rejectsUnexpectedFixtureResponseSchema() {
        OutboundCandidatePayload payload = payload();

        var result = responseGuard.guard(payload, new ConnectorResult(
            "con_test",
            "fake",
            ConnectorStatus.ACKNOWLEDGED,
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            "response-digest",
            "wrong-schema",
            Map.of("answer", "Approved context processed")
        ));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("RESPONSE_SCHEMA_VERSION_MISMATCH");
    }

    @Test
    void rejectsSensitiveDataRegeneratedByProviderResponse() {
        OutboundCandidatePayload payload = payload();

        var result = responseGuard.guard(payload, new ConnectorResult(
            "con_test",
            "fake",
            ConnectorStatus.ACKNOWLEDGED,
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            "response-digest",
            "ai-provider-response/v1",
            Map.of("answer", "Call 010-1234-5678")
        ));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("RESPONSE_SENSITIVE_DATA_DETECTED");
        assertThat(result.findings()).hasSize(1);
    }

    private OutboundCandidatePayload payload() {
        return new OutboundCandidatePayload(
            "out_test",
            "dest_test",
            "v1",
            "profile_digest",
            ExecutionPackType.AI,
            "schema-v1",
            "candidate_digest",
            List.of()
        );
    }
}
