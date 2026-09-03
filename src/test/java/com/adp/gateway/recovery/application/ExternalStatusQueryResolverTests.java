package com.adp.gateway.recovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void duplicateSpecificAdaptersFailClosed() {
        ExternalStatusQueryPort first = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryPort second = mock(ExternalStatusQueryPort.class);
        when(first.supports("ai-http")).thenReturn(true);
        when(second.supports("ai-http")).thenReturn(true);
        ExternalStatusQueryResolver resolver = new ExternalStatusQueryResolver(List.of(first, second));

        assertThatThrownBy(() -> resolver.resolve("ai-http"))
            .isInstanceOf(AmbiguousExternalStatusQueryAdapterException.class);
    }

    @Test
    void duplicateFallbackAdaptersAreRejectedAtConstruction() {
        ExternalStatusQueryPort first = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryPort second = mock(ExternalStatusQueryPort.class);
        when(first.fallback()).thenReturn(true);
        when(second.fallback()).thenReturn(true);

        assertThatThrownBy(() -> new ExternalStatusQueryResolver(List.of(first, second)))
            .isInstanceOf(AmbiguousExternalStatusQueryAdapterException.class);
    }
}
