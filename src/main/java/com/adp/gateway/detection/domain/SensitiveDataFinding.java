package com.adp.gateway.detection.domain;

public record SensitiveDataFinding(
    SensitiveDataType type,
    String contextPath,
    int startOffset,
    int endOffset,
    String detectorVersion,
    String evidenceDigest
) {
}
