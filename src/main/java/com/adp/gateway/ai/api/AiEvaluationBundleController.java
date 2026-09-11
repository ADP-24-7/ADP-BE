package com.adp.gateway.ai.api;

import com.adp.gateway.ai.application.AiEvaluationBundleService;
import com.adp.gateway.ai.application.AiCalibrationEvidenceService;
import com.adp.gateway.ai.domain.AiCalibrationEvidence;
import com.adp.gateway.ai.domain.AiEvaluationBundle;
import com.adp.gateway.ai.domain.AiEvaluationRunReadiness;
import com.adp.gateway.auth.domain.AuthPrincipal;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/ai/evaluation-runs")
public class AiEvaluationBundleController {
    private final AiEvaluationBundleService bundleService;
    private final AiCalibrationEvidenceService calibrationEvidenceService;

    public AiEvaluationBundleController(
        AiEvaluationBundleService bundleService,
        AiCalibrationEvidenceService calibrationEvidenceService
    ) {
        this.bundleService = bundleService;
        this.calibrationEvidenceService = calibrationEvidenceService;
    }

    @GetMapping("/{evaluationRunId}/bundle")
    AiEvaluationBundle export(
        @PathVariable @Size(max = 120) String evaluationRunId,
        Authentication authentication
    ) {
        return bundleService.export((AuthPrincipal) authentication.getPrincipal(), evaluationRunId);
    }

    @GetMapping("/{evaluationRunId}/readiness")
    AiEvaluationRunReadiness readiness(
        @PathVariable @Size(max = 120) String evaluationRunId,
        Authentication authentication
    ) {
        return bundleService.readiness((AuthPrincipal) authentication.getPrincipal(), evaluationRunId);
    }

    @GetMapping("/{evaluationRunId}/calibration-evidence")
    AiCalibrationEvidence calibrationEvidence(
        @PathVariable @Size(max = 120) String evaluationRunId,
        Authentication authentication
    ) {
        return calibrationEvidenceService.export(
            (AuthPrincipal) authentication.getPrincipal(), evaluationRunId
        );
    }
}
