package com.adp.gateway.egress.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class NoopResponseGuardAdapterTests {

    private final NoopResponseGuardAdapter responseGuard = new NoopResponseGuardAdapter(new SimpleMeterRegistry());

    @Test
    void returnsNotEvaluatedBecauseGuardIsNotConfigured() {
        var result = responseGuard.guard(
            null,
            new ConnectorResult("con_test", "fake", ConnectorStatus.ACKNOWLEDGED, "out_test", "payload_digest", null, null)
        );

        assertThat(result.status()).isEqualTo("NOT_EVALUATED");
        assertThat(result.reasonCodes()).contains("RESPONSE_GUARD_NOT_CONFIGURED");
    }

    @Test
    void doesNotPassEvenWhenResponseMetadataExists() {
        var result = responseGuard.guard(
            null,
            new ConnectorResult(
                "con_test",
                "fake",
                ConnectorStatus.ACKNOWLEDGED,
                "out_test",
                "payload_digest",
                "response_digest",
                "response-schema-v1"
            )
        );

        assertThat(result.status()).isEqualTo("NOT_EVALUATED");
        assertThat(result.reasonCodes()).contains("RESPONSE_GUARD_NOT_CONFIGURED");
    }
}
