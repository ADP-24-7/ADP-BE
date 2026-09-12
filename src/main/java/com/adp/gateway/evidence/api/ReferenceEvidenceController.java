package com.adp.gateway.evidence.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.evidence.application.ReferenceEvidenceService;
import com.adp.gateway.evidence.application.ReferenceEvidenceLineageService;
import com.adp.gateway.evidence.domain.ReferenceEvidence;
import com.adp.gateway.evidence.domain.ReferenceEvidenceBundleReceipt;
import com.adp.gateway.evidence.domain.ReferenceEvidencePage;
import com.adp.gateway.evidence.domain.ReferenceEvidenceType;
import com.adp.gateway.evidence.domain.ReferenceEvidencePolicyLineage;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/admin/reference-evidence")
public class ReferenceEvidenceController {
    private final ReferenceEvidenceService service;
    private final ReferenceEvidenceLineageService lineageService;

    public ReferenceEvidenceController(
        ReferenceEvidenceService service,
        ReferenceEvidenceLineageService lineageService
    ) {
        this.service = service;
        this.lineageService = lineageService;
    }

    @PostMapping("/bundles")
    @ResponseStatus(HttpStatus.CREATED)
    ReferenceEvidenceBundleReceipt ingest(
        @RequestBody JsonNode bundle,
        Authentication authentication
    ) {
        return service.ingest(principal(authentication), bundle);
    }

    @GetMapping
    ReferenceEvidencePage search(
        @RequestParam(required = false) ReferenceEvidenceType evidenceType,
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(required = false) @Size(max = 120) String query,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        Authentication authentication
    ) {
        return service.search(
            principal(authentication), evidenceType, workloadId, query, limit, offset
        );
    }

    @GetMapping("/{evidenceId}/versions/{evidenceVersion}")
    ReferenceEvidence load(
        @PathVariable @Size(max = 80) String evidenceId,
        @PathVariable @Size(max = 40) String evidenceVersion,
        Authentication authentication
    ) {
        return service.load(principal(authentication), evidenceId, evidenceVersion);
    }

    @PostMapping("/{evidenceId}/versions/{evidenceVersion}/policy-bindings")
    List<ReferenceEvidencePolicyLineage> bindPolicyArtifact(
        @PathVariable @Size(max = 80) String evidenceId,
        @PathVariable @Size(max = 40) String evidenceVersion,
        @Valid @RequestBody BindReferenceEvidencePolicyRequest request,
        Authentication authentication
    ) {
        return lineageService.bind(
            principal(authentication), evidenceId, evidenceVersion,
            request.artifactId(), request.artifactVersion(), request.sourceDigest(),
            request.requirementRefs(), request.controlRefs()
        );
    }

    @GetMapping("/policy-artifacts/{artifactId}/versions/{artifactVersion}")
    List<ReferenceEvidencePolicyLineage> loadPolicyArtifactLineage(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        Authentication authentication
    ) {
        return lineageService.load(
            principal(authentication), artifactId, artifactVersion
        );
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
