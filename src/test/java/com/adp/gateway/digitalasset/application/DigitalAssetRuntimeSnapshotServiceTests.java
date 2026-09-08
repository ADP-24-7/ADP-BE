package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DigitalAssetRuntimeSnapshotServiceTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-08T00:00:00Z");

    @Mock
    private DigitalAssetRuntimeSnapshotPersistence persistence;

    @Test
    void pinsAllVersionedIdentitiesIntoOneImmutableSnapshot() {
        var service = new DigitalAssetRuntimeSnapshotService(persistence, new DigitalAssetCanonicalJson());
        when(persistence.loadActive("institution-local", "workload"))
            .thenReturn(Optional.of(active()));

        var result = service.pinIfRequired(
            "exec-1", "institution-local", "workload", "PURPOSE", destination(), policy(PolicyLifecycleStage.ACTIVE), NOW
        );

        assertThat(result).isPresent();
        assertThat(result.get().snapshotDigest()).matches("sha256:[0-9a-f]{64}");
        assertThat(result.get().artifactVersion()).isEqualTo("1.0.0");
        assertThat(result.get().runtimeControlDigest()).isEqualTo("sha256:" + "b".repeat(64));
        assertThat(result.get().crosswalkDigest()).isEqualTo("sha256:" + "c".repeat(64));
        ArgumentCaptor<com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot> captor =
            ArgumentCaptor.forClass(com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot.class);
        verify(persistence).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(result.get());
    }

    @Test
    void rejectsProvisionalPolicyBeforeSnapshotPersistence() {
        var service = new DigitalAssetRuntimeSnapshotService(persistence, new DigitalAssetCanonicalJson());
        when(persistence.loadActive(any(), any())).thenReturn(Optional.of(active()));

        assertThatThrownBy(() -> service.pinIfRequired(
            "exec-1", "institution-local", "workload", "PURPOSE", destination(),
            policy(PolicyLifecycleStage.PROJECT_PROVISIONAL), NOW
        )).isInstanceOf(DigitalAssetRuntimeSnapshotException.class)
            .extracting(exception -> ((DigitalAssetRuntimeSnapshotException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_RUNTIME_SNAPSHOT_INVALID");
        verify(persistence, never()).save(any());
    }

    @Test
    void rejectsMissingActiveSelectionBeforeSnapshotPersistence() {
        var service = new DigitalAssetRuntimeSnapshotService(persistence, new DigitalAssetCanonicalJson());
        when(persistence.loadActive(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pinIfRequired(
            "exec-1", "institution-local", "workload", "PURPOSE", destination(),
            policy(PolicyLifecycleStage.ACTIVE), NOW
        )).isInstanceOf(DigitalAssetRuntimeSnapshotException.class)
            .extracting(exception -> ((DigitalAssetRuntimeSnapshotException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ACTIVE_ARTIFACT_NOT_FOUND");
        verify(persistence, never()).save(any());
    }

    @Test
    void detectsActiveArtifactReplacementDuringPreExecutionRevalidation() {
        var service = new DigitalAssetRuntimeSnapshotService(persistence, new DigitalAssetCanonicalJson());
        DigitalAssetActiveArtifact replacement = new DigitalAssetActiveArtifact(
            "institution-local", "workload", "PURPOSE", "artifact", "2.0.0", "d".repeat(64),
            "destination", "2.0.0", "sha256:" + "e".repeat(64), "2.0.0",
            "sha256:" + "f".repeat(64), "checker", NOW.plusMinutes(1)
        );
        when(persistence.loadActive("institution-local", "workload"))
            .thenReturn(Optional.of(active()), Optional.of(replacement));

        var pinned = service.pinIfRequired(
            "exec-1", "institution-local", "workload", "PURPOSE", destination(),
            policy(PolicyLifecycleStage.ACTIVE), NOW
        );

        assertThat(service.isPinnedCurrent(pinned, destination(), policy(PolicyLifecycleStage.ACTIVE))).isFalse();
    }

    private DigitalAssetActiveArtifact active() {
        return new DigitalAssetActiveArtifact(
            "institution-local", "workload", "PURPOSE", "artifact", "1.0.0", "a".repeat(64),
            "destination", "1.0.0", "sha256:" + "b".repeat(64), "1.0.0",
            "sha256:" + "c".repeat(64), "checker", NOW
        );
    }

    private DestinationProfile destination() {
        return new DestinationProfile(
            "destination", "2.0.0", "destination-digest", "contract", "provider",
            ExecutionPackType.DIGITAL_ASSET, "schema", "tenant", "KR", "NO_RETENTION", false,
            "ACTIVE", NOW.minusDays(1), null, List.of(new DestinationBinding("workload", "PURPOSE")), List.of()
        );
    }

    private PolicySnapshot policy(PolicyLifecycleStage stage) {
        return new PolicySnapshot(
            "3.0.0", "policy-digest", NOW.minusDays(1), stage,
            new SourcePolicyEvaluationArtifactRef("policy", "3.0.0", new ArtifactDigest("sha256", "digest")),
            new PolicyEvaluation(
                List.of(), List.of(), List.of(), List.of(), PolicyAction.ALLOW, List.of(), List.of(),
                new PolicyApplicabilitySpec(
                    AnalysisStatus.VALIDATED, AnalysisStatus.VALIDATED, "validated", List.of(), List.of(),
                    List.of(), new RuntimeBinding("mapped", "CUSTOMER", "workload", "PURPOSE", "binding")
                )
            )
        );
    }
}
