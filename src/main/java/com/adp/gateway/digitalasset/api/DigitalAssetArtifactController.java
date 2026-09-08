package com.adp.gateway.digitalasset.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactLoaderService;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;
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
@RequestMapping("/api/admin/digital-assets/artifacts")
public class DigitalAssetArtifactController {
    private final DigitalAssetArtifactLoaderService service;

    public DigitalAssetArtifactController(DigitalAssetArtifactLoaderService service) {
        this.service = service;
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

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }
}
