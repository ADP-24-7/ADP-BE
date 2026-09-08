package com.adp.gateway.digitalasset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IngestDigitalAssetArtifactRequest(
    @NotBlank @Size(max = 512) String manifestReference,
    @NotBlank @Pattern(regexp = "sha256:[0-9a-f]{64}") String expectedContentDigest
) {
}
