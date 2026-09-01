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

    public static OutboundGuardResult rejected(List<String> reasonCodes) {
        return new OutboundGuardResult("REJECTED", reasonCodes);
    }

    public boolean isPassed() {
        return "PASSED".equals(status);
    }
}
