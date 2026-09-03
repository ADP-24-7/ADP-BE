package com.adp.gateway.ai.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
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

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;
    private final MeterRegistry meterRegistry;

    public HttpAiConnector(
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper,
        CanonicalValueHasher hasher,
        MeterRegistry meterRegistry,
        @Value("${adp.ai-connector.base-url:http://localhost:8090}") String baseUrl,
        @Value("${adp.ai-connector.connect-timeout:2s}") Duration connectTimeout,
        @Value("${adp.ai-connector.read-timeout:5s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.meterRegistry = meterRegistry;
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
        try {
            var response = restClient.post()
                .uri("/v1/chat/completions")
                .body(providerRequest.payload())
                .retrieve()
                .toEntity(Map.class);
            Object responsePayload = response.getBody();
            String responseDigest = hasher.hash(canonicalJson(responsePayload));
            String schemaVersion = response.getHeaders().getFirst("X-ADP-Response-Schema-Version");
            ConnectorStatus status = ConnectorStatus.ACKNOWLEDGED;
            record(status);
            return new ConnectorResult(
                connectorExecutionId,
                "ai-http-connector",
                status,
                payload.outboundPayloadId(),
                payload.candidatePayloadDigest(),
                responseDigest,
                schemaVersion == null ? DEFAULT_RESPONSE_SCHEMA : schemaVersion,
                responsePayload
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
                null
            );
        } catch (RestClientResponseException exception) {
            log.warn("AI provider returned an unsuccessful HTTP status: {}", exception.getStatusCode().value());
            record(ConnectorStatus.FAILED);
            return new ConnectorResult(
                connectorExecutionId,
                "ai-http-connector",
                ConnectorStatus.FAILED,
                payload.outboundPayloadId(),
                payload.candidatePayloadDigest(),
                null,
                null,
                null
            );
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
