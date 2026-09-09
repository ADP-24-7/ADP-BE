package com.adp.gateway.policy.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyOperationsReadService;
import com.adp.gateway.policy.domain.PolicyArtifactHistory;
import com.adp.gateway.policy.domain.PolicyArtifactPage;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/policy-lifecycle")
public class PolicyOperationsReadController {
    private final PolicyOperationsReadService service;

    public PolicyOperationsReadController(PolicyOperationsReadService service) {
        this.service = service;
    }

    @GetMapping
    PolicyArtifactPage search(
        @RequestParam(required = false) ExecutionPackType executionPack,
        @RequestParam(required = false) PolicyLifecycleStage lifecycleStage,
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(required = false) @Size(max = 120) String query,
        @RequestParam(defaultValue = "false") boolean attentionRequired,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        Authentication authentication
    ) {
        return service.search(
            principal(authentication), executionPack, lifecycleStage, workloadId, query,
            attentionRequired, limit, offset
        );
    }

    @GetMapping("/{artifactId}/versions/{artifactVersion}/history")
    PolicyArtifactHistory history(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        Authentication authentication
    ) {
        return service.history(principal(authentication), artifactId, artifactVersion);
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
