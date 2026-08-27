package com.adp.gateway.detection.application;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.detection.domain.DetectionResult;

public interface SensitiveDataDetector {

    String detectorVersion();

    DetectionResult detect(CanonicalContext context);
}
