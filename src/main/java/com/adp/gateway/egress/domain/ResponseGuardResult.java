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

    public static ResponseGuardResult rejected(List<String> reasonCodes) {
        return new ResponseGuardResult("REJECTED", true, reasonCodes);
    }

    public static ResponseGuardResult notEvaluated(List<String> reasonCodes) {
        return new ResponseGuardResult("NOT_EVALUATED", false, reasonCodes);
    }

    public boolean isPassed() {
        return "PASSED".equals(status);
    }
}
