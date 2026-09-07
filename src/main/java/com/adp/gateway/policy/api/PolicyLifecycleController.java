package com.adp.gateway.policy.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/policy-lifecycle")
public class PolicyLifecycleController {
    private final PolicyLifecycleService service;

    public PolicyLifecycleController(PolicyLifecycleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PolicyLifecycleRecord create(
        @Valid @RequestBody CreatePolicyLifecycleRequest request,
        Authentication authentication
    ) {
        return service.create(
            principal(authentication), request.artifactId(), request.artifactVersion(), request.artifactDigest(),
            request.policyLayer(), request.executionPack(), request.workloadId(), request.purposeCode()
        );
    }

    @GetMapping("/{artifactId}/versions/{artifactVersion}")
    PolicyLifecycleRecord load(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        Authentication authentication
    ) {
        return service.load(principal(authentication), artifactId, artifactVersion);
    }

    @PostMapping("/{artifactId}/versions/{artifactVersion}/transitions")
    PolicyLifecycleRecord transition(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        @Valid @RequestBody TransitionPolicyLifecycleRequest request,
        Authentication authentication
    ) {
        return service.transition(
            principal(authentication), artifactId, artifactVersion, request.targetStage(), request.reasonCode()
        );
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
