package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DigitalAssetArtifactLoaderServiceTests {
    @Mock
    private DigitalAssetArtifactContentStore store;
    @Mock
    private DigitalAssetArtifactBundleValidator validator;
    @Mock
    private DigitalAssetArtifactIngestionPersistence persistence;
    @Mock
    private PolicyLifecycleService lifecycleService;

    private DigitalAssetArtifactLoaderService service;

    @BeforeEach
    void setUp() {
        service = new DigitalAssetArtifactLoaderService(
            store, validator, persistence, lifecycleService,
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsUnauthorizedPrincipalBeforeArtifactStorageAccess() {
        AuthPrincipal auditor = principal("institution-local", Set.of("tokenized_asset_purchase"), AdpRole.AUDITOR);

        assertThatThrownBy(() -> service.ingest(auditor, "manifest.json", "sha256:abc"))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_INGEST_FORBIDDEN");

        verifyNoInteractions(store, validator, persistence, lifecycleService);
    }

    @Test
    void rejectsCrossInstitutionAndCrossWorkloadBindingBeforeLifecycleCreation() {
        AuthPrincipal operator = principal("institution-local", Set.of("tokenized_asset_purchase"), AdpRole.OPERATOR);
        when(validator.validate(any(), any(), any())).thenReturn(bundle("other-institution", "other-workload", "a".repeat(64)));

        assertThatThrownBy(() -> service.ingest(operator, "manifest.json", "sha256:" + "a".repeat(64)))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_BINDING_INVALID");

        verifyNoInteractions(persistence, lifecycleService);
    }

    @Test
    void rejectsSameArtifactVersionWithDifferentDigest() {
        AuthPrincipal operator = principal("institution-local", Set.of("tokenized_asset_purchase"), AdpRole.OPERATOR);
        ValidatedDigitalAssetArtifactBundle candidate = bundle(
            "institution-local", "tokenized_asset_purchase", "b".repeat(64)
        );
        when(validator.validate(any(), any(), any())).thenReturn(candidate);
        when(persistence.find(any(), any(), any(), any())).thenReturn(Optional.of(new DigitalAssetArtifactIngestion(
            "institution-local", candidate.artifactId(), candidate.artifactVersion(), "a".repeat(64),
            candidate.manifestSchemaVersion(), candidate.manifestReference(), candidate.canonicalContractVersion(),
            candidate.canonicalContractDigest(), candidate.workloadId(), candidate.purposeCode(),
            candidate.destinationProfileId(), 5, com.adp.gateway.policy.domain.PolicyLifecycleStage.CANDIDATE,
            "previous-maker", java.time.OffsetDateTime.parse("2026-09-07T00:00:00Z")
        )));

        assertThatThrownBy(() -> service.ingest(operator, "manifest.json", "sha256:" + "b".repeat(64)))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_CONFLICT");

        verify(lifecycleService, never()).create(any(), any(), any(), any(), any(), any(), any(), any());
        verify(persistence).lockIdentity(
            "institution-local", candidate.artifactId(), candidate.artifactVersion()
        );
    }

    private ValidatedDigitalAssetArtifactBundle bundle(String institutionId, String workloadId, String digest) {
        return new ValidatedDigitalAssetArtifactBundle(
            "DA-DIGITAL-ASSET-RUNTIME-CANDIDATE-001", "1.0.0", digest,
            DigitalAssetArtifactBundleValidator.MANIFEST_SCHEMA_VERSION, "manifest.json",
            institutionId, workloadId, "DIGITAL_ASSET_PURCHASE", "dest_mock_asset_platform_v1",
            "1.0.0", "sha256:" + "c".repeat(64), List.of()
        );
    }

    private AuthPrincipal principal(String institutionId, Set<String> workloads, AdpRole role) {
        return new AuthPrincipal(
            "operator-1", PrincipalType.USER, "Operator", institutionId, false, workloads, Set.of(role)
        );
    }
}
