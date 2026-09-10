package com.adp.gateway.digitalasset.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactLoaderService;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactActivationService;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactCurrentStateReadService;
import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStateDetail;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStatePage;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/admin/digital-assets/artifacts")
public class DigitalAssetArtifactController {
    private final DigitalAssetArtifactLoaderService service;
    private final DigitalAssetArtifactActivationService activationService;
    private final DigitalAssetArtifactCurrentStateReadService currentStateReadService;

    public DigitalAssetArtifactController(
        DigitalAssetArtifactLoaderService service,
        DigitalAssetArtifactActivationService activationService,
        DigitalAssetArtifactCurrentStateReadService currentStateReadService
    ) {
        this.service = service;
        this.activationService = activationService;
        this.currentStateReadService = currentStateReadService;
    }

    @GetMapping
    DigitalAssetArtifactCurrentStatePage search(
        @RequestParam(required = false) PolicyLifecycleStage lifecycleStage,
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(required = false) @Size(max = 120) String query,
        @RequestParam(defaultValue = "false") boolean currentOnly,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return currentStateReadService.search(
            principal(authentication), lifecycleStage, workloadId, query, currentOnly, page, size
        );
    }

    @PostMapping("/{artifactId}/versions/{artifactVersion}/activate")
    DigitalAssetActiveArtifact activate(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        Authentication authentication
    ) {
        return activationService.activate(principal(authentication), artifactId, artifactVersion);
    }

    @PostMapping("/ingestions")
    @ResponseStatus(HttpStatus.CREATED)
    DigitalAssetArtifactIngestion ingest(
        @Valid @RequestBody IngestDigitalAssetArtifactRequest request,
        Authentication authentication
    ) {
        return service.ingest(
            principal(authentication), request.manifestReference(), request.expectedContentDigest()
        );
    }

    @GetMapping("/{artifactId}/versions/{artifactVersion}")
    DigitalAssetArtifactIngestion load(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        Authentication authentication
    ) {
        return service.load(principal(authentication), artifactId, artifactVersion);
    }

    @GetMapping("/{artifactId}/versions/{artifactVersion}/current-state")
    DigitalAssetArtifactCurrentStateDetail currentState(
        @PathVariable @Size(max = 120) String artifactId,
        @PathVariable @Size(max = 120) String artifactVersion,
        Authentication authentication
    ) {
        return currentStateReadService.load(
            principal(authentication), artifactId, artifactVersion
        );
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
