package com.adp.gateway.egress.domain;

import java.util.List;

public record OutboundGuardResult(
    String status,
    List<String> reasonCodes
) {

    public OutboundGuardResult {
        reasonCodes = List.copyOf(reasonCodes);
    }

    public static OutboundGuardResult passed() {
        return new OutboundGuardResult("PASSED", List.of());
    }
}
