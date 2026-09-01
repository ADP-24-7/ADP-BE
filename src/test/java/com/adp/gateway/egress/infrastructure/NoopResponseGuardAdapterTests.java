package com.adp.gateway.egress.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.connector.domain.ConnectorResult;
import org.junit.jupiter.api.Test;

class NoopResponseGuardAdapterTests {

    private final NoopResponseGuardAdapter responseGuard = new NoopResponseGuardAdapter();

    @Test
    void returnsNotEvaluatedWhenResponseMetadataIsMissing() {
        var result = responseGuard.guard(
            null,
            new ConnectorResult("con_test", "fake", "EXECUTED", "out_test", "payload_digest", null, null)
        );

        assertThat(result.status()).isEqualTo("NOT_EVALUATED");
        assertThat(result.reasonCodes()).contains("RESPONSE_METADATA_MISSING");
    }

    @Test
    void passesOnlyWhenResponseMetadataExists() {
        var result = responseGuard.guard(
            null,
            new ConnectorResult(
                "con_test",
                "fake",
                "EXECUTED",
                "out_test",
                "payload_digest",
                "response_digest",
                "response-schema-v1"
            )
        );

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.reasonCodes()).isEmpty();
    }
}
