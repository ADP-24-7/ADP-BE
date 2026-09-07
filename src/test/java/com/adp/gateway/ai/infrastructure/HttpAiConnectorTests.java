package com.adp.gateway.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.ai.application.AiProviderConnectionRegistry;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpAiConnectorTests {

    @Test
    void normalizesSuccessfulProviderResponseAndCreatesCanonicalDigest() throws Exception {
        HttpServer server = server(200, """
            {"answer":"safe","usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
            """, Duration.ZERO);
        try {
            var result = connector(server, Duration.ofSeconds(1)).execute(
                context(),
                mock(RuntimeDecision.class),
                outbound(),
                providerRequest()
            );

            assertThat(result.status()).isEqualTo(ConnectorStatus.ACKNOWLEDGED);
            assertThat(result.responseDigest()).hasSize(64);
            assertThat(result.responseSchemaVersion()).isEqualTo("ai-provider-response/v1");
            assertThat(result.executionEvidence().measurementType().name()).isEqualTo("HTTP_FULL_RESPONSE");
            assertThat(result.executionEvidence().fullResponseLatencyMillis()).isNotNegative();
            assertThat(result.executionEvidence().attemptElapsedMillis()).isNull();
            assertThat(result.executionEvidence().inputTokens()).isEqualTo(11);
            assertThat(result.executionEvidence().outputTokens()).isEqualTo(7);
            assertThat(result.executionEvidence().totalTokens()).isEqualTo(18);
            assertThat(result.executionEvidence().tokenUsageStatus().name()).isEqualTo("COMPLETE");
            assertThat(result.executionEvidence().errorCategory().name()).isEqualTo("NONE");
            assertThat(result.toString()).doesNotContain("safe");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void normalizesReadTimeoutAsSentUnknown() throws Exception {
        HttpServer server = server(200, "{\"answer\":\"late\"}", Duration.ofMillis(200));
        try {
            var result = connector(server, Duration.ofMillis(20)).execute(
                context(),
                mock(RuntimeDecision.class),
                outbound(),
                providerRequest()
            );

            assertThat(result.status()).isEqualTo(ConnectorStatus.SENT_UNKNOWN);
            assertThat(result.responseDigest()).isNull();
            assertThat(result.executionEvidence().measurementType().name()).isEqualTo("HTTP_ATTEMPT_NO_RESPONSE");
            assertThat(result.executionEvidence().fullResponseLatencyMillis()).isNull();
            assertThat(result.executionEvidence().attemptElapsedMillis()).isNotNegative();
            assertThat(result.executionEvidence().errorCategory().name()).isEqualTo("TRANSPORT");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void normalizesProviderHttpErrorAsFailed() throws Exception {
        HttpServer server = server(503, "{\"error\":\"unavailable\"}", Duration.ZERO);
        try {
            var result = connector(server, Duration.ofSeconds(1)).execute(
                context(),
                mock(RuntimeDecision.class),
                outbound(),
                providerRequest()
            );

            assertThat(result.status()).isEqualTo(ConnectorStatus.FAILED);
            assertThat(result.responsePayload()).isNull();
            assertThat(result.executionEvidence().measurementType().name()).isEqualTo("HTTP_FULL_RESPONSE");
            assertThat(result.executionEvidence().errorCategory().name()).isEqualTo("PROVIDER_SERVER_ERROR");
            assertThat(result.executionEvidence().providerHttpStatus()).isEqualTo(503);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsProviderCorrelationKeyInHeader() throws Exception {
        AtomicReference<String> correlationKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            correlationKey.set(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
            byte[] payload = "{\"answer\":\"safe\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            connector(server, Duration.ofSeconds(1)).execute(
                context(), mock(RuntimeDecision.class), outbound(), providerRequest()
            );
            assertThat(correlationKey.get()).isEqualTo("preq");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsNvidiaApiKeyAsBearerCredentialWithoutExposingItInResult() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] payload = "{\"answer\":\"safe\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            String apiKey = "test-only-nvidia-key";
            var catalog = catalog();
            var connections = new AiProviderConnectionRegistry(
                catalog, "http://internal.invalid", "http://localhost:" + server.getAddress().getPort(), apiKey
            );
            assertThat(connections.resolve(catalog.profiles().getFirst().profileId()).orElseThrow().toString())
                .doesNotContain(apiKey);
            var connector = new HttpAiConnector(
                RestClient.builder(), new ObjectMapper(), new CanonicalValueHasher(), new SimpleMeterRegistry(),
                connections,
                Duration.ofSeconds(1), Duration.ofSeconds(1)
            );

            var result = connector.execute(
                context(), mock(RuntimeDecision.class), outbound(),
                providerRequest(catalog.profiles().getFirst().profileId())
            );

            assertThat(authorization.get()).isEqualTo("Bearer " + apiKey);
            assertThat(result.toString()).doesNotContain(apiKey);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void neverSendsNvidiaCredentialToInternalProvider() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] payload = "{\"answer\":\"safe\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            var connections = new AiProviderConnectionRegistry(
                catalog(), "http://localhost:" + server.getAddress().getPort(), "http://nvidia.invalid", "secret"
            );
            var connector = connector(connections, Duration.ofSeconds(1));
            connector.execute(context(), mock(RuntimeDecision.class), outbound(), providerRequest());
            assertThat(authorization.get()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsClosedBeforeNetworkWhenNvidiaCredentialIsMissing() {
        var catalog = catalog();
        var connections = new AiProviderConnectionRegistry(
            catalog, "http://internal.invalid", "http://127.0.0.1:1", ""
        );
        var result = connector(connections, Duration.ofSeconds(1)).execute(
            context(), mock(RuntimeDecision.class), outbound(),
            providerRequest(catalog.profiles().getFirst().profileId())
        );
        assertThat(result.status()).isEqualTo(ConnectorStatus.FAILED);
        assertThat(result.executionEvidence().measurementType().name()).isEqualTo("NOT_ATTEMPTED");
        assertThat(result.executionEvidence().fullResponseLatencyMillis()).isNull();
        assertThat(result.executionEvidence().attemptElapsedMillis()).isNull();
        assertThat(result.executionEvidence().providerHttpStatus()).isNull();
    }

    @Test
    void keepsSuccessfulResponseWhenProviderTokenUsageIsInconsistent() throws Exception {
        HttpServer server = server(200, """
            {"answer":"safe","usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":16}}
            """, Duration.ZERO);
        try {
            var result = connector(server, Duration.ofSeconds(1)).execute(
                context(), mock(RuntimeDecision.class), outbound(), providerRequest()
            );

            assertThat(result.status()).isEqualTo(ConnectorStatus.ACKNOWLEDGED);
            assertThat(result.responseDigest()).hasSize(64);
            assertThat(result.responsePayload()).isNotNull();
            assertThat(result.executionEvidence().tokenUsageStatus().name()).isEqualTo("INVALID");
            assertThat(result.executionEvidence().inputTokens()).isNull();
            assertThat(result.executionEvidence().outputTokens()).isNull();
            assertThat(result.executionEvidence().totalTokens()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void normalizesMalformedSuccessfulResponseAsParseFailureEvidence() throws Exception {
        HttpServer server = server(200, "{\"choices\":[", Duration.ZERO);
        try {
            var result = connector(server, Duration.ofSeconds(1)).execute(
                context(), mock(RuntimeDecision.class), outbound(), providerRequest()
            );

            assertThat(result.status()).isEqualTo(ConnectorStatus.FAILED);
            assertThat(result.responsePayload()).isNull();
            assertThat(result.executionEvidence().measurementType().name()).isEqualTo("HTTP_FULL_RESPONSE");
            assertThat(result.executionEvidence().fullResponseLatencyMillis()).isNotNegative();
            assertThat(result.executionEvidence().attemptElapsedMillis()).isNull();
            assertThat(result.executionEvidence().errorCategory().name()).isEqualTo("RESPONSE_PARSE_ERROR");
            assertThat(result.executionEvidence().tokenUsageStatus().name()).isEqualTo("NOT_PROVIDED");
            assertThat(result.executionEvidence().providerHttpStatus()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsClosedBeforeNetworkForUnregisteredProviderProfile() {
        var connections = new AiProviderConnectionRegistry(
            catalog(), "http://127.0.0.1:1", "http://127.0.0.1:1", "secret"
        );
        var result = connector(connections, Duration.ofSeconds(1)).execute(
            context(), mock(RuntimeDecision.class), outbound(), providerRequest("unregistered-provider")
        );
        assertThat(result.status()).isEqualTo(ConnectorStatus.FAILED);
    }

    private HttpAiConnector connector(HttpServer server, Duration readTimeout) {
        return connector(new AiProviderConnectionRegistry(
            catalog(), "http://localhost:" + server.getAddress().getPort(), "http://nvidia.invalid", ""
        ), readTimeout);
    }

    private HttpAiConnector connector(AiProviderConnectionRegistry connections, Duration readTimeout) {
        return new HttpAiConnector(
            RestClient.builder(),
            new ObjectMapper(),
            new CanonicalValueHasher(),
            new SimpleMeterRegistry(),
            connections,
            Duration.ofSeconds(1),
            readTimeout
        );
    }

    private HttpServer server(int status, String body, Duration delay) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(delay.toMillis());
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("X-ADP-Response-Schema-Version", "ai-provider-response/v1");
                exchange.sendResponseHeaders(status, payload.length);
                exchange.getResponseBody().write(payload);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private RuntimeRequestContext context() {
        return new RuntimeRequestContext(
            "req",
            "trace",
            "idem",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer:customer-100"
        );
    }

    private OutboundCandidatePayload outbound() {
        return new OutboundCandidatePayload(
            "out",
            "dest",
            "v1",
            "destination-digest",
            ExecutionPackType.AI,
            "schema-v1",
            "candidate-digest",
            List.of()
        );
    }

    private ProviderRequestPayload providerRequest() {
        return providerRequest("internal-provider");
    }

    private ProviderRequestPayload providerRequest(String providerProfileId) {
        return new ProviderRequestPayload(
            "preq",
            "out",
            providerProfileId,
            "schema-v1",
            "provider-request-digest",
            1,
            Map.of("context", Map.of("request.prompt", "safe question"))
        );
    }

    private AiModelProfileCatalog catalog() {
        return new AiModelProfileCatalog(new ObjectMapper(), new CanonicalValueHasher());
    }
}
