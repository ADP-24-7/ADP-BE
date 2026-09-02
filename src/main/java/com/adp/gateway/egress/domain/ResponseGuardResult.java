package com.adp.gateway.egress.domain;

import java.util.List;

public record ResponseGuardResult(
    String status,
    boolean leakageDetected,
    List<String> reasonCodes,
    String detectorVersion,
    List<ResponseSensitiveFinding> findings
) {

    public ResponseGuardResult {
        reasonCodes = List.copyOf(reasonCodes);
        findings = List.copyOf(findings);
    }

    public ResponseGuardResult(String status, boolean leakageDetected, List<String> reasonCodes) {
        this(status, leakageDetected, reasonCodes, null, List.of());
    }

    public static ResponseGuardResult passed() {
        return new ResponseGuardResult("PASSED", false, List.of(), null, List.of());
    }

    public static ResponseGuardResult passed(String detectorVersion) {
        return new ResponseGuardResult("PASSED", false, List.of(), detectorVersion, List.of());
    }

    public static ResponseGuardResult rejected(List<String> reasonCodes) {
        return new ResponseGuardResult("REJECTED", true, reasonCodes, null, List.of());
    }

    public static ResponseGuardResult rejected(
        List<String> reasonCodes,
        String detectorVersion,
        List<ResponseSensitiveFinding> findings
    ) {
        return new ResponseGuardResult("REJECTED", true, reasonCodes, detectorVersion, findings);
    }

    public static ResponseGuardResult notEvaluated(List<String> reasonCodes) {
        return new ResponseGuardResult("NOT_EVALUATED", false, reasonCodes, null, List.of());
    }

    public boolean isPassed() {
        return "PASSED".equals(status);
    }
}
