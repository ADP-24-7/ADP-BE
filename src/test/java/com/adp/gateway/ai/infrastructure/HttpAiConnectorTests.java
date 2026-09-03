package com.adp.gateway.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
        HttpServer server = server(200, "{\"answer\":\"safe\"}", Duration.ZERO);
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

    private HttpAiConnector connector(HttpServer server, Duration readTimeout) {
        return new HttpAiConnector(
            RestClient.builder(),
            new ObjectMapper(),
            new CanonicalValueHasher(),
            new SimpleMeterRegistry(),
            "http://localhost:" + server.getAddress().getPort(),
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
        return new ProviderRequestPayload(
            "preq",
            "out",
            "provider",
            "schema-v1",
            "provider-request-digest",
            1,
            Map.of("context", Map.of("request.prompt", "safe question"))
        );
    }
}
