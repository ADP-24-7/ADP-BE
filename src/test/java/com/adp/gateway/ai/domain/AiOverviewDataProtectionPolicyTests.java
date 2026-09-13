package com.adp.gateway.ai.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOverviewDataProtectionPolicyTests {
    @Test
    void excludesBusinessMetadataFromProtectedFieldRate() {
        assertThat(AiOverviewDataProtectionPolicy.requiresProtection("BUSINESS_METADATA")).isFalse();
    }

    @Test
    void includesSensitiveAndUnclassifiedDataInProtectedFieldRate() {
        assertThat(AiOverviewDataProtectionPolicy.requiresProtection("CUSTOMER_IDENTIFIER")).isTrue();
        assertThat(AiOverviewDataProtectionPolicy.requiresProtection("FINANCIAL_METADATA")).isTrue();
        assertThat(AiOverviewDataProtectionPolicy.requiresProtection("UNKNOWN")).isTrue();
        assertThat(AiOverviewDataProtectionPolicy.requiresProtection(null)).isTrue();
    }
}
