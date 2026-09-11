package com.adp.gateway.ai.api;

import com.adp.gateway.ai.application.AiTransformGovernanceProfileService;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/evaluation-runs")
public class AiTransformGovernanceProfileController {
    private final AiTransformGovernanceProfileService profiles;

    public AiTransformGovernanceProfileController(AiTransformGovernanceProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{runId}/transform-governance-profile")
    AiTransformGovernanceProfileResponse read(@PathVariable String runId, Authentication authentication) {
        return profiles.read((AuthPrincipal) authentication.getPrincipal(), runId);
    }
}
