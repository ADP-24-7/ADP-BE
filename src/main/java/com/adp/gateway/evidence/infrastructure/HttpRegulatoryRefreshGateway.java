package com.adp.gateway.evidence.infrastructure;

import java.util.List;

import com.adp.gateway.evidence.application.ReferenceEvidenceException;
import com.adp.gateway.evidence.application.RegulatoryRefreshGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpRegulatoryRefreshGateway implements RegulatoryRefreshGateway {
    private final RestClient client;
    private final String internalToken;

    public HttpRegulatoryRefreshGateway(
        RestClient.Builder builder,
        @Value("${adp.regulatory-refresh.base-url:http://localhost:8010}") String baseUrl,
        @Value("${adp.regulatory-refresh.internal-token:}") String internalToken
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public JsonNode refresh(List<String> sourceIds) {
        JsonNode response = client.post()
            .uri("/internal/regulatory/refresh")
            .header("X-ADP-Internal-Token", internalToken)
            .body(new RefreshBody(sourceIds.isEmpty() ? null : sourceIds))
            .retrieve()
            .body(JsonNode.class);
        if (response == null) {
            throw new ReferenceEvidenceException("REGULATORY_REFRESH_EMPTY_RESPONSE");
        }
        return response;
    }

    private record RefreshBody(@JsonProperty("source_ids") List<String> sourceIds) {
    }
}
