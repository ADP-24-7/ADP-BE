package com.adp.gateway.evidence.application;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class RegulatoryRefreshService {
    private final RegulatoryRefreshGateway gateway;

    public RegulatoryRefreshService(RegulatoryRefreshGateway gateway) {
        this.gateway = gateway;
    }

    public JsonNode refresh(List<String> sourceIds) {
        JsonNode report = gateway.refresh(sourceIds == null ? List.of() : List.copyOf(sourceIds));
        if (report.path("automatic_activation").asBoolean(true)) {
            throw new ReferenceEvidenceException("REGULATORY_AUTOMATIC_ACTIVATION_FORBIDDEN");
        }
        return report;
    }
}
