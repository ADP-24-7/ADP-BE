package com.adp.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.application.RuntimeConnectorResolver;
import com.adp.gateway.connector.application.FakeConnector;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.application.ExecutionPackContextBuilder;
import com.adp.gateway.context.application.ExecutionPackContextBuilderResolver;
import com.adp.gateway.egress.application.ExternalSchemaMapper;
import com.adp.gateway.egress.application.ExternalSchemaMapperResolver;
import com.adp.gateway.egress.application.PackRuntimeAdapterNotFoundException;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.application.ResponseGuardResolver;
import com.adp.gateway.egress.domain.ExecutionPackType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PackRuntimeResolverTests {

    @Test
    void resolvesPackSpecificAdapters() {
        ExecutionPackContextBuilder contextBuilder = contextBuilder(ExecutionPackType.AI);
        ExternalSchemaMapper schemaMapper = schemaMapper(ExecutionPackType.AI);
        RuntimeConnectorPort connector = connector(ExecutionPackType.AI);
        ResponseGuardPort responseGuard = responseGuard(ExecutionPackType.AI);

        assertThat(new ExecutionPackContextBuilderResolver(List.of(contextBuilder)).resolve(ExecutionPackType.AI))
            .isSameAs(contextBuilder);
        assertThat(new ExternalSchemaMapperResolver(List.of(schemaMapper)).resolve(ExecutionPackType.AI))
            .isSameAs(schemaMapper);
        assertThat(new RuntimeConnectorResolver(List.of(connector)).resolve(ExecutionPackType.AI))
            .isSameAs(connector);
        assertThat(new ResponseGuardResolver(List.of(responseGuard)).resolve(ExecutionPackType.AI))
            .isSameAs(responseGuard);
    }

    @Test
    void packSpecificConnectorAndGuardTakePriorityOverCommonFallback() {
        RuntimeConnectorPort commonConnector = connector(ExecutionPackType.COMMON);
        RuntimeConnectorPort aiConnector = connector(ExecutionPackType.AI);
        ResponseGuardPort commonGuard = responseGuard(ExecutionPackType.COMMON);
        ResponseGuardPort aiGuard = responseGuard(ExecutionPackType.AI);

        assertThat(new RuntimeConnectorResolver(List.of(commonConnector, aiConnector)).resolve(ExecutionPackType.AI))
            .isSameAs(aiConnector);
        assertThat(new ResponseGuardResolver(List.of(commonGuard, aiGuard)).resolve(ExecutionPackType.AI))
            .isSameAs(aiGuard);
    }

    @Test
    void commonConnectorAndGuardAreUsedOnlyAsFallback() {
        RuntimeConnectorPort commonConnector = connector(ExecutionPackType.COMMON);
        ResponseGuardPort commonGuard = responseGuard(ExecutionPackType.COMMON);

        assertThat(new RuntimeConnectorResolver(List.of(commonConnector)).resolve(ExecutionPackType.DIGITAL_ASSET))
            .isSameAs(commonConnector);
        assertThat(new ResponseGuardResolver(List.of(commonGuard)).resolve(ExecutionPackType.DIGITAL_ASSET))
            .isSameAs(commonGuard);
    }

    @Test
    void missingRequiredPackAdapterFailsClosed() {
        assertThatThrownBy(() -> new ExecutionPackContextBuilderResolver(List.of()).resolve(ExecutionPackType.DIGITAL_ASSET))
            .isInstanceOf(PackRuntimeAdapterNotFoundException.class)
            .hasMessageContaining("DIGITAL_ASSET");
        assertThatThrownBy(() -> new ExternalSchemaMapperResolver(List.of()).resolve(ExecutionPackType.DIGITAL_ASSET))
            .isInstanceOf(PackRuntimeAdapterNotFoundException.class)
            .hasMessageContaining("DIGITAL_ASSET");
    }

    @Test
    void duplicatePackRegistrationIsRejected() {
        assertThatThrownBy(() -> new RuntimeConnectorResolver(List.of(
            connector(ExecutionPackType.AI),
            connector(ExecutionPackType.AI)
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate connector for pack AI");

        assertThatThrownBy(() -> new ExecutionPackContextBuilderResolver(List.of(
            contextBuilder(ExecutionPackType.AI),
            contextBuilder(ExecutionPackType.AI)
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate context builder for pack AI");

        assertThatThrownBy(() -> new ExternalSchemaMapperResolver(List.of(
            schemaMapper(ExecutionPackType.AI),
            schemaMapper(ExecutionPackType.AI)
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate schema mapper for pack AI");

        assertThatThrownBy(() -> new ResponseGuardResolver(List.of(
            responseGuard(ExecutionPackType.AI),
            responseGuard(ExecutionPackType.AI)
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate response guard for pack AI");
    }

    @Test
    void fakeAiConnectorCannotActAsDigitalAssetFallback() {
        FakeConnector fakeAiConnector = new FakeConnector(new SimpleMeterRegistry(), new CanonicalValueHasher());
        RuntimeConnectorResolver resolver = new RuntimeConnectorResolver(List.of(fakeAiConnector));

        assertThat(fakeAiConnector.supportedPack()).isEqualTo(ExecutionPackType.AI);
        assertThatThrownBy(() -> resolver.resolve(ExecutionPackType.DIGITAL_ASSET))
            .isInstanceOf(PackRuntimeAdapterNotFoundException.class)
            .hasMessageContaining("DIGITAL_ASSET");
    }

    private ExecutionPackContextBuilder contextBuilder(ExecutionPackType packType) {
        ExecutionPackContextBuilder adapter = mock(ExecutionPackContextBuilder.class);
        when(adapter.supportedPack()).thenReturn(packType);
        return adapter;
    }

    private ExternalSchemaMapper schemaMapper(ExecutionPackType packType) {
        ExternalSchemaMapper adapter = mock(ExternalSchemaMapper.class);
        when(adapter.supportedPack()).thenReturn(packType);
        return adapter;
    }

    private RuntimeConnectorPort connector(ExecutionPackType packType) {
        RuntimeConnectorPort adapter = mock(RuntimeConnectorPort.class);
        when(adapter.supportedPack()).thenReturn(packType);
        return adapter;
    }

    private ResponseGuardPort responseGuard(ExecutionPackType packType) {
        ResponseGuardPort adapter = mock(ResponseGuardPort.class);
        when(adapter.supportedPack()).thenReturn(packType);
        return adapter;
    }
}
