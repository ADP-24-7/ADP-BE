package com.adp.gateway.ai.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.adp.gateway.ai.application.AiProviderConnectionRegistry;
import com.adp.gateway.ai.domain.AiProviderConnection;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorExecutionEvidence;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "adp.ai-connector.enabled", havingValue = "true")
public class HttpAiConnector implements RuntimeConnectorPort {

    private static final String DEFAULT_RESPONSE_SCHEMA = "ai-provider-response/v1";
    private static final Logger log = LoggerFactory.getLogger(HttpAiConnector.class);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;
    private final MeterRegistry meterRegistry;
    private final AiProviderConnectionRegistry connections;

    public HttpAiConnector(
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper,
        CanonicalValueHasher hasher,
        MeterRegistry meterRegistry,
        AiProviderConnectionRegistry connections,
        @Value("${adp.ai-connector.connect-timeout:2s}") Duration connectTimeout,
        @Value("${adp.ai-connector.read-timeout:5s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClientBuilder = restClientBuilder.requestFactory(requestFactory);
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.meterRegistry = meterRegistry;
        this.connections = connections;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.AI;
    }

    @Override
    public ConnectorResult execute(
        RuntimeRequestContext context,
        RuntimeDecision decision,
        OutboundCandidatePayload payload,
        ProviderRequestPayload providerRequest
    ) {
        String connectorExecutionId = "con_" + UUID.randomUUID();
        long startedAt = System.nanoTime();
        var connection = connections.resolve(providerRequest.providerProfileId());
        if (connection.isEmpty() || credentialMissing(connection.get())) {
            log.warn("AI provider connection resolution failed for an approved provider profile");
            record(ConnectorStatus.FAILED);
            return failed(connectorExecutionId, payload, "CONNECTION_CONFIGURATION", startedAt);
        }
        try {
            AiProviderConnection resolved = connection.get();
            var request = restClientBuilder.clone().baseUrl(resolved.baseUrl()).build().post()
                .uri("/v1/chat/completions")
                .header("X-Idempotency-Key", providerRequest.providerCorrelationKey());
            if (resolved.credentialType() == AiProviderConnection.CredentialType.NVIDIA_API_KEY) {
                request = request.header("Authorization", "Bearer " + resolved.credential());
            }
            var response = request
                .body(providerRequest.payload())
                .retrieve()
                .toEntity(Map.class);
            Object responsePayload = response.getBody();
            String responseDigest = hasher.hash(canonicalJson(responsePayload));
            String schemaVersion = response.getHeaders().getFirst("X-ADP-Response-Schema-Version");
            ConnectorStatus status = ConnectorStatus.ACKNOWLEDGED;
            record(status);
            long providerLatencyMillis = elapsedMillis(startedAt);
            Map<String, Object> usage = nestedMap(responsePayload, "usage");
            return new ConnectorResult(
                connectorExecutionId,
                "ai-http-connector",
                status,
                payload.outboundPayloadId(),
                payload.candidatePayloadDigest(),
                responseDigest,
                schemaVersion == null ? DEFAULT_RESPONSE_SCHEMA : schemaVersion,
                responsePayload,
                evidence(
                    providerLatencyMillis,
                    integer(usage, "prompt_tokens"),
                    integer(usage, "completion_tokens"),
                    integer(usage, "total_tokens"),
                    "NONE"
                )
            );
        } catch (ResourceAccessException exception) {
            log.warn("AI provider request result is unknown due to transport failure: {}", exception.getClass().getSimpleName());
            record(ConnectorStatus.SENT_UNKNOWN);
            return new ConnectorResult(
                connectorExecutionId,
                "ai-http-connector",
                ConnectorStatus.SENT_UNKNOWN,
                payload.outboundPayloadId(),
                payload.candidatePayloadDigest(),
                null,
                null,
                null,
                evidence(elapsedMillis(startedAt), null, null, null, "TRANSPORT")
            );
        } catch (RestClientResponseException exception) {
            log.warn("AI provider returned an unsuccessful HTTP status: {}", exception.getStatusCode().value());
            record(ConnectorStatus.FAILED);
            String errorCategory = exception.getStatusCode().is4xxClientError()
                ? "PROVIDER_CLIENT_ERROR"
                : "PROVIDER_SERVER_ERROR";
            return new ConnectorResult(
                connectorExecutionId,
                "ai-http-connector",
                ConnectorStatus.FAILED,
                payload.outboundPayloadId(),
                payload.candidatePayloadDigest(),
                null,
                null,
                null,
                evidence(elapsedMillis(startedAt), null, null, null, errorCategory)
            );
        } catch (RuntimeException exception) {
            log.warn("AI provider response could not be normalized: {}", exception.getClass().getSimpleName());
            record(ConnectorStatus.FAILED);
            return failed(connectorExecutionId, payload, "RESPONSE_PARSE_ERROR", startedAt);
        }
    }

    private boolean credentialMissing(AiProviderConnection connection) {
        return connection.baseUrl() == null || connection.baseUrl().isBlank()
            || (connection.credentialType() == AiProviderConnection.CredentialType.NVIDIA_API_KEY
                && connection.credential().isBlank());
    }

    private ConnectorResult failed(
        String connectorExecutionId,
        OutboundCandidatePayload payload,
        String errorCategory,
        long startedAt
    ) {
        return new ConnectorResult(
            connectorExecutionId,
            "ai-http-connector",
            ConnectorStatus.FAILED,
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            null,
            null,
            null,
            evidence(elapsedMillis(startedAt), null, null, null, errorCategory)
        );
    }

    private ConnectorExecutionEvidence evidence(
        long providerLatencyMillis,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        String errorCategory
    ) {
        boolean completeUsage = inputTokens != null && outputTokens != null && totalTokens != null;
        return new ConnectorExecutionEvidence(
            "HTTP_RESPONSE",
            providerLatencyMillis,
            providerLatencyMillis,
            completeUsage ? inputTokens : null,
            completeUsage ? outputTokens : null,
            completeUsage ? totalTokens : null,
            errorCategory
        );
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value, String key) {
        if (value instanceof Map<?, ?> map && map.get(key) instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return Map.of();
    }

    private Integer integer(Map<String, Object> values, String key) {
        return values.get(key) instanceof Number value ? value.intValue() : null;
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalValue(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI provider response could not be canonicalized", exception);
        }
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> canonical = new TreeMap<>();
            map.forEach((key, item) -> canonical.put(String.valueOf(key), canonicalValue(item)));
            return canonical;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalValue).toList();
        }
        return value;
    }

    private void record(ConnectorStatus status) {
        meterRegistry.counter("connector.execution.total", "status", status.name()).increment();
    }
}
