package com.adp.gateway.policy.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.application.PolicyShadowService;
import com.adp.gateway.policy.domain.PolicyShadowEvidence;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyCurrentSelection;
import com.adp.gateway.egress.domain.ExecutionPackType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/policy-lifecycle")
public class PolicyLifecycleController {
    private final PolicyLifecycleService service;
    private final PolicyShadowService shadowService;

    public PolicyLifecycleController(PolicyLifecycleService service, PolicyShadowService shadowService) {
        this.service = service;
        this.shadowService = shadowService;
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

    @PostMapping("/{artifactId}/versions/{artifactVersion}/approvals")
    PolicyLifecycleRecord approve(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        @Valid @RequestBody ApprovePolicyLifecycleRequest request,
        Authentication authentication
    ) {
        return service.approve(
            principal(authentication), artifactId, artifactVersion, request.shadowEvaluationId()
        );
    }

    @PostMapping("/{artifactId}/versions/{artifactVersion}/activations")
    PolicyCurrentSelection activate(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        @Valid @RequestBody ActivatePolicyRequest request,
        Authentication authentication
    ) {
        return service.activate(
            principal(authentication), artifactId, artifactVersion,
            request.expectedArtifactRevision(), request.expectedSelectionRevision()
        );
    }

    @PostMapping("/{artifactId}/versions/{artifactVersion}/rollbacks")
    PolicyCurrentSelection rollback(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        @Valid @RequestBody RollbackPolicyRequest request,
        Authentication authentication
    ) {
        return service.rollback(
            principal(authentication), artifactId, artifactVersion,
            request.expectedTargetRevision(), request.expectedSelectionRevision()
        );
    }

    @GetMapping("/current-selection")
    PolicyCurrentSelection currentSelection(
        @RequestParam ExecutionPackType executionPack,
        @RequestParam @Size(max = 120) String workloadId,
        @RequestParam @Size(max = 120) String purposeCode,
        Authentication authentication
    ) {
        return service.loadCurrentSelection(
            principal(authentication), executionPack, workloadId, purposeCode
        );
    }

    @PostMapping("/{artifactId}/versions/{artifactVersion}/shadow-evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    PolicyShadowEvidence evaluateShadow(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        @Valid @RequestBody RunPolicyShadowEvaluationRequest request,
        Authentication authentication
    ) {
        return shadowService.evaluate(
            principal(authentication), artifactId, artifactVersion, request.evaluationCaseId()
        );
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
