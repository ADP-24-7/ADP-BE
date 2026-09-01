package com.adp.gateway.transform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocalInMemoryPseudonymizationKeyAdapterTests {

    @Test
    void returnsStableVersionedFixtureKey() {
        LocalInMemoryPseudonymizationKeyAdapter adapter = new LocalInMemoryPseudonymizationKeyAdapter();

        var first = adapter.load("project-provisional-key-v1");
        var second = adapter.load("project-provisional-key-v1");

        assertThat(second.keyVersion()).isEqualTo(first.keyVersion());
        assertThat(second.secretKey().getEncoded()).isEqualTo(first.secretKey().getEncoded());
    }

    @Test
    void rejectsUnknownKeyVersion() {
        LocalInMemoryPseudonymizationKeyAdapter adapter = new LocalInMemoryPseudonymizationKeyAdapter();

        assertThatThrownBy(() -> adapter.load("unknown-key-v1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown-key-v1");
    }
}
