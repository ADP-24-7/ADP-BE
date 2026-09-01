package com.adp.gateway.egress.domain;

import java.util.List;

public record ResponseGuardResult(
    String status,
    boolean leakageDetected,
    List<String> reasonCodes
) {

    public ResponseGuardResult {
        reasonCodes = List.copyOf(reasonCodes);
    }

    public static ResponseGuardResult passed() {
        return new ResponseGuardResult("PASSED", false, List.of());
    }
}
