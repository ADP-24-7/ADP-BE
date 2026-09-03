package com.adp.gateway.recovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExternalStatusQueryResolverTests {

    @Test
    void connectorSpecificAdapterWinsOverFallback() {
        ExternalStatusQueryPort specific = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryPort fallback = mock(ExternalStatusQueryPort.class);
        when(specific.supports("ai-http")).thenReturn(true);
        when(fallback.fallback()).thenReturn(true);

        ExternalStatusQueryPort resolved = new ExternalStatusQueryResolver(List.of(fallback, specific))
            .resolve("ai-http");

        assertThat(resolved).isSameAs(specific);
    }
}
