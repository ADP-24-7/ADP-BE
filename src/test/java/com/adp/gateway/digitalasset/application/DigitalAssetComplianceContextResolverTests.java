package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class DigitalAssetComplianceContextResolverTests {

    @Test
    void rejectsOnlyDigitalAssetExecutionWhenProviderIsNotConfigured() {
        var resolver = new DigitalAssetComplianceContextResolver(List.of());

        assertThatThrownBy(() -> resolver.load("customer-1", "account-1", "wallet-1"))
            .isInstanceOf(DigitalAssetComplianceContextUnavailableException.class)
            .satisfies(exception -> {
                var rejected = (DigitalAssetComplianceContextUnavailableException) exception;
                org.assertj.core.api.Assertions.assertThat(rejected.reasonCode())
                    .isEqualTo("DIGITAL_ASSET_COMPLIANCE_CONTEXT_NOT_CONFIGURED");
            });
    }
}
