package com.adp.gateway.evidence.application;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public interface RegulatoryRefreshGateway {
    JsonNode refresh(List<String> sourceIds);
}
