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
import com.adp.gateway.connector.domain.ConnectorErrorCategory;
import com.adp.gateway.connector.domain.ConnectorMeasurementType;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.connector.domain.TokenUsageStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.application.DestinationEndpointPolicy;
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
import org.springframework.web.client.RestClientException;
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
    private final DestinationEndpointPolicy destinationEndpointPolicy;

    public HttpAiConnector(
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper,
        CanonicalValueHasher hasher,
        MeterRegistry meterRegistry,
        AiProviderConnectionRegistry connections,
        DestinationEndpointPolicy destinationEndpointPolicy,
        @Value("${adp.ai-connector.connect-timeout:2s}") Duration connectTimeout,
        @Value("${adp.ai-connector.read-timeout:5s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClientBuilder = restClientBuilder.requestFactory(requestFactory);
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.meterRegistry = meterRegistry;
        this.connections = connections;
        this.destinationEndpointPolicy = destinationEndpointPolicy;
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
        if (connection.isEmpty() || credentialMissing(connection.get())
            || !destinationEndpointPolicy.allows(connection.get().baseUrl())) {
            log.warn("AI provider connection resolution failed for an approved provider profile");
            record(ConnectorStatus.FAILED);
            return failedNotAttempted(connectorExecutionId, payload);
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
            TokenUsage usage = tokenUsage(responsePayload);
            return new ConnectorResult(
                connectorExecutionId,
                "ai-http-connector",
                status,
                payload.outboundPayloadId(),
                payload.candidatePayloadDigest(),
                responseDigest,
                schemaVersion == null ? DEFAULT_RESPONSE_SCHEMA : schemaVersion,
                responsePayload,
                fullResponseEvidence(
                    providerLatencyMillis,
                    usage,
                    ConnectorErrorCategory.NONE,
                    response.getStatusCode().value()
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
                attemptEvidence(elapsedMillis(startedAt), ConnectorErrorCategory.TRANSPORT)
            );
        } catch (RestClientResponseException exception) {
            log.warn("AI provider returned an unsuccessful HTTP status: {}", exception.getStatusCode().value());
            record(ConnectorStatus.FAILED);
            ConnectorErrorCategory errorCategory = exception.getStatusCode().is4xxClientError()
                ? ConnectorErrorCategory.PROVIDER_CLIENT_ERROR
                : ConnectorErrorCategory.PROVIDER_SERVER_ERROR;
            return new ConnectorResult(
                connectorExecutionId,
                "ai-http-connector",
                ConnectorStatus.FAILED,
                payload.outboundPayloadId(),
                payload.candidatePayloadDigest(),
                null,
                null,
                null,
                fullResponseEvidence(
                    elapsedMillis(startedAt), TokenUsage.notProvided(), errorCategory,
                    exception.getStatusCode().value()
                )
            );
        } catch (RestClientException exception) {
            log.warn("AI provider response body could not be decoded: {}", exception.getClass().getSimpleName());
            record(ConnectorStatus.FAILED);
            return failedAfterResponse(connectorExecutionId, payload, startedAt);
        } catch (IllegalStateException exception) {
            log.warn("AI provider response could not be normalized: {}", exception.getClass().getSimpleName());
            record(ConnectorStatus.FAILED);
            return failedAfterResponse(connectorExecutionId, payload, startedAt);
        }
    }

    private boolean credentialMissing(AiProviderConnection connection) {
        return connection.baseUrl() == null || connection.baseUrl().isBlank()
            || (connection.credentialType() == AiProviderConnection.CredentialType.NVIDIA_API_KEY
                && connection.credential().isBlank());
    }

    private ConnectorResult failedNotAttempted(
        String connectorExecutionId,
        OutboundCandidatePayload payload
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
            new ConnectorExecutionEvidence(
                ConnectorMeasurementType.NOT_ATTEMPTED, null, null, null, null, null,
                TokenUsageStatus.NOT_PROVIDED, ConnectorErrorCategory.CONNECTION_CONFIGURATION, null
            )
        );
    }

    private ConnectorResult failedAfterResponse(
        String connectorExecutionId,
        OutboundCandidatePayload payload,
        long startedAt
    ) {
        return new ConnectorResult(
            connectorExecutionId, "ai-http-connector", ConnectorStatus.FAILED,
            payload.outboundPayloadId(), payload.candidatePayloadDigest(), null, null, null,
            fullResponseEvidence(
                elapsedMillis(startedAt), TokenUsage.notProvided(), ConnectorErrorCategory.RESPONSE_PARSE_ERROR, null
            )
        );
    }

    private ConnectorExecutionEvidence fullResponseEvidence(
        long latencyMillis,
        TokenUsage usage,
        ConnectorErrorCategory errorCategory,
        Integer providerHttpStatus
    ) {
        return new ConnectorExecutionEvidence(
            ConnectorMeasurementType.HTTP_FULL_RESPONSE, latencyMillis, null,
            usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), usage.status(), errorCategory,
            providerHttpStatus
        );
    }

    private ConnectorExecutionEvidence attemptEvidence(long elapsedMillis, ConnectorErrorCategory errorCategory) {
        return new ConnectorExecutionEvidence(
            ConnectorMeasurementType.HTTP_ATTEMPT_NO_RESPONSE, null, elapsedMillis, null, null, null,
            TokenUsageStatus.NOT_PROVIDED, errorCategory, null
        );
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private TokenUsage tokenUsage(Object responsePayload) {
        if (!(responsePayload instanceof Map<?, ?> response) || !response.containsKey("usage")) {
            return TokenUsage.notProvided();
        }
        if (!(response.get("usage") instanceof Map<?, ?> usage)) {
            return TokenUsage.invalid();
        }
        if (!usage.containsKey("prompt_tokens") || !usage.containsKey("completion_tokens")
            || !usage.containsKey("total_tokens")) {
            return new TokenUsage(null, null, null, TokenUsageStatus.INCOMPLETE);
        }
        Long input = nonNegativeLong(usage.get("prompt_tokens"));
        Long output = nonNegativeLong(usage.get("completion_tokens"));
        Long total = nonNegativeLong(usage.get("total_tokens"));
        if (input == null || output == null || total == null) {
            return TokenUsage.invalid();
        }
        if (input > Integer.MAX_VALUE || output > Integer.MAX_VALUE || total > Integer.MAX_VALUE
            || input + output != total) {
            return TokenUsage.invalid();
        }
        return new TokenUsage(input.intValue(), output.intValue(), total.intValue(), TokenUsageStatus.COMPLETE);
    }

    private Long nonNegativeLong(Object value) {
        return value instanceof Number number && number.longValue() >= 0 ? number.longValue() : null;
    }

    private record TokenUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        TokenUsageStatus status
    ) {
        private static TokenUsage notProvided() {
            return new TokenUsage(null, null, null, TokenUsageStatus.NOT_PROVIDED);
        }

        private static TokenUsage invalid() {
            return new TokenUsage(null, null, null, TokenUsageStatus.INVALID);
        }
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
