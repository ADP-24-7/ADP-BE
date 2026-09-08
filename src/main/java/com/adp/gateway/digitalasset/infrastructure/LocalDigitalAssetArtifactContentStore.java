package com.adp.gateway.digitalasset.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactContentStore;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.digital-asset.artifact-store.type", havingValue = "local")
public class LocalDigitalAssetArtifactContentStore implements DigitalAssetArtifactContentStore {
    private final Path root;

    public LocalDigitalAssetArtifactContentStore(
        @Value("${adp.digital-asset.artifact-store.local-root:.}") String root
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String load(String reference, long maxBytes) {
        if (reference == null || reference.isBlank() || reference.length() > 512
            || reference.contains("\\") || !reference.endsWith(".json")) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
        }
        Path relative = Path.of(reference);
        if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
        }
        try {
            Path resolvedRoot = root.toRealPath();
            Path resolved = root.resolve(relative).toRealPath();
            if (!resolved.startsWith(resolvedRoot) || !Files.isRegularFile(resolved)
                || Files.size(resolved) > maxBytes) {
                throw rejected("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
            }
            return Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (DigitalAssetArtifactIngestionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DigitalAssetArtifactIngestionException(
                "DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID", exception
            );
        }
    }

    private DigitalAssetArtifactIngestionException rejected(String reasonCode) {
        return new DigitalAssetArtifactIngestionException(reasonCode);
    }
}
