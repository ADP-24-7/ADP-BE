package com.adp.gateway.detection.domain;

import java.util.List;

public record DetectionResult(
    String detectorVersion,
    List<SensitiveDataFinding> findings
) {
}
