package com.adp.gateway.evidence.api;

import java.util.List;

import jakarta.validation.constraints.Size;

public record RegulatoryRefreshRequest(
    @Size(max = 100) List<@Size(max = 120) String> sourceIds
) {
}
