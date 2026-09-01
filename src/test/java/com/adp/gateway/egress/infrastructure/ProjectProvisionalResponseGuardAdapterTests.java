package com.adp.gateway.egress.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ProjectProvisionalResponseGuardAdapterTests {

    private final ProjectProvisionalResponseGuardAdapter responseGuard =
        new ProjectProvisionalResponseGuardAdapter(new SimpleMeterRegistry());

    @Test
    void passesOnlyExpectedFixtureResponseMetadata() {
        OutboundCandidatePayload payload = payload();

        var result = responseGuard.guard(payload, new ConnectorResult(
            "con_test",
            "fake",
            "EXECUTED",
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            "fake-response-digest:" + payload.candidatePayloadDigest(),
            "fake-response-schema-v1"
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
            "EXECUTED",
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            "fake-response-digest:" + payload.candidatePayloadDigest(),
            "wrong-schema"
        ));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reasonCodes()).contains("RESPONSE_SCHEMA_VERSION_MISMATCH");
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
