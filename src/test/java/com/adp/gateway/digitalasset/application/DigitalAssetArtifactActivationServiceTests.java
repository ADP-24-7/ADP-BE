package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DigitalAssetArtifactActivationServiceTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-08T00:00:00Z");

    @Mock
    private DigitalAssetArtifactIngestionPersistence ingestionPersistence;
    @Mock
    private DigitalAssetRuntimeSnapshotPersistence snapshotPersistence;
    @Mock
    private PolicyLifecycleService lifecycleService;

    @Test
    void transitionsApprovedArtifactAndCreatesAuthoritativeActiveSelectionInOrder() {
        var service = service();
        var principal = principal();
        when(ingestionPersistence.find(any(), any(), any(), any())).thenReturn(Optional.of(ingestion()));
        when(snapshotPersistence.loadActive("institution-local", "workload")).thenReturn(Optional.empty());
        when(lifecycleService.load(principal, "artifact", "1.0.0")).thenReturn(lifecycle(PolicyLifecycleStage.APPROVED));

        var active = service.activate(principal, "artifact", "1.0.0");

        assertThat(active.activatedBy()).isEqualTo("checker");
        InOrder order = inOrder(lifecycleService, snapshotPersistence);
        order.verify(snapshotPersistence).lockActiveScope("institution-local", "workload");
        order.verify(snapshotPersistence).loadActive("institution-local", "workload");
        order.verify(lifecycleService).transitionForRuntimeSelection(
            principal, "artifact", "1.0.0", PolicyLifecycleStage.ACTIVE,
            PolicyLifecycleTransitionReason.ACTIVATION_APPROVED
        );
        order.verify(snapshotPersistence).replaceActive(active);
    }

    @Test
    void rejectsCandidateWithoutCreatingActiveSelection() {
        var service = service();
        var principal = principal();
        when(ingestionPersistence.find(any(), any(), any(), any())).thenReturn(Optional.of(ingestion()));
        when(snapshotPersistence.loadActive("institution-local", "workload")).thenReturn(Optional.empty());
        when(lifecycleService.load(principal, "artifact", "1.0.0")).thenReturn(lifecycle(PolicyLifecycleStage.CANDIDATE));

        assertThatThrownBy(() -> service.activate(principal, "artifact", "1.0.0"))
            .isInstanceOf(DigitalAssetRuntimeSnapshotException.class)
            .extracting(exception -> ((DigitalAssetRuntimeSnapshotException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ACTIVE_ARTIFACT_INVALID");
        verify(snapshotPersistence, never()).replaceActive(any());
    }

    @Test
    void supersedesPreviousActiveBeforeReplacingSelection() {
        var service = service();
        var principal = principal();
        DigitalAssetArtifactIngestion replacement = ingestion();
        var previous = new com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact(
            "institution-local", "workload", "PURPOSE", "artifact", "0.9.0", "e".repeat(64),
            "destination", "0.9.0", "sha256:" + "f".repeat(64), "0.9.0",
            "sha256:" + "1".repeat(64), "previous-checker", NOW.minusDays(1)
        );
        when(ingestionPersistence.find(any(), any(), any(), any())).thenReturn(Optional.of(replacement));
        when(snapshotPersistence.loadActive("institution-local", "workload")).thenReturn(Optional.of(previous));
        when(lifecycleService.load(principal, "artifact", "1.0.0"))
            .thenReturn(lifecycle(PolicyLifecycleStage.APPROVED));
        when(lifecycleService.load(principal, "artifact", "0.9.0"))
            .thenReturn(new PolicyLifecycleRecord(
                "artifact", "0.9.0", "e".repeat(64), "institution-local", PolicyLayer.WORKLOAD,
                ExecutionPackType.DIGITAL_ASSET, "workload", "PURPOSE", PolicyLifecycleStage.ACTIVE,
                "old-maker", 6, NOW.minusDays(1), NOW.minusDays(1)
            ));

        var active = service.activate(principal, "artifact", "1.0.0");

        InOrder order = inOrder(lifecycleService, snapshotPersistence);
        order.verify(snapshotPersistence).lockActiveScope("institution-local", "workload");
        order.verify(snapshotPersistence).loadActive("institution-local", "workload");
        order.verify(lifecycleService).transitionForRuntimeSelection(
            principal, "artifact", "0.9.0", PolicyLifecycleStage.SUPERSEDED,
            PolicyLifecycleTransitionReason.ACTIVE_VERSION_SUPERSEDED
        );
        order.verify(lifecycleService).transitionForRuntimeSelection(
            principal, "artifact", "1.0.0", PolicyLifecycleStage.ACTIVE,
            PolicyLifecycleTransitionReason.ACTIVATION_APPROVED
        );
        order.verify(snapshotPersistence).replaceActive(active);
    }

    private DigitalAssetArtifactActivationService service() {
        return new DigitalAssetArtifactActivationService(
            ingestionPersistence, snapshotPersistence, lifecycleService,
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
            "checker", PrincipalType.USER, "Checker", "institution-local", false,
            Set.of("workload"), Set.of(AdpRole.PRIVILEGED_OPERATOR)
        );
    }

    private DigitalAssetArtifactIngestion ingestion() {
        return new DigitalAssetArtifactIngestion(
            "institution-local", "artifact", "1.0.0", "a".repeat(64), "manifest/v1", "manifest.json",
            "1.0.0", "sha256:" + "d".repeat(64), "workload", "PURPOSE", "destination",
            "1.0.0", "sha256:" + "b".repeat(64), "1.0.0", "sha256:" + "c".repeat(64),
            5, PolicyLifecycleStage.CANDIDATE, "maker", NOW
        );
    }

    private PolicyLifecycleRecord lifecycle(PolicyLifecycleStage stage) {
        return new PolicyLifecycleRecord(
            "artifact", "1.0.0", "a".repeat(64), "institution-local", PolicyLayer.WORKLOAD,
            ExecutionPackType.DIGITAL_ASSET, "workload", "PURPOSE", stage, "maker", 5, NOW, NOW
        );
    }
}
