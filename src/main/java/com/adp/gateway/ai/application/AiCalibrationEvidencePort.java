package com.adp.gateway.ai.application;

import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.domain.AiCalibrationFindingSource;
import com.adp.gateway.ai.domain.AiCalibrationGuardSource;

public interface AiCalibrationEvidencePort {
    List<AiCalibrationGuardSource> loadGuards(
        Set<String> executionIds,
        String institutionId,
        Set<String> allowedWorkloads
    );

    List<AiCalibrationFindingSource> loadFindings(
        Set<String> executionIds,
        String institutionId,
        Set<String> allowedWorkloads
    );
}
