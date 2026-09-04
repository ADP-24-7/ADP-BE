package com.adp.gateway.digitalasset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.recovery.application.ExternalStatusQueryPermanentException;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import com.adp.gateway.recovery.domain.RetryDisposition;
import org.junit.jupiter.api.Test;

class FakeDigitalAssetStatusQueryAdapterTests {

    private final FakeDigitalAssetPlatformStateStore stateStore = new FakeDigitalAssetPlatformStateStore();
    private final FakeDigitalAssetStatusQueryAdapter adapter =
        new FakeDigitalAssetStatusQueryAdapter(new CanonicalValueHasher(), stateStore);

    @Test
    void evidenceDigestIsStableAndBoundToCorrelationAndStatus() {
        stateStore.record("provider-request-1", ConnectorStatus.ACKNOWLEDGED);
        stateStore.record("provider-request-2", ConnectorStatus.ACKNOWLEDGED);

        var first = adapter.query(recovery("provider-request-1"));
        var repeated = adapter.query(recovery("provider-request-1"));
        var differentCorrelation = adapter.query(recovery("provider-request-2"));

        stateStore.record("provider-request-1", ConnectorStatus.SENT_UNKNOWN);
        var differentStatus = adapter.query(recovery("provider-request-1"));

        assertThat(first.status()).isEqualTo(ConnectorStatus.ACKNOWLEDGED);
        assertThat(first.evidenceDigest()).matches("[0-9a-f]{64}");
        assertThat(repeated.evidenceDigest()).isEqualTo(first.evidenceDigest());
        assertThat(differentCorrelation.evidenceDigest()).isNotEqualTo(first.evidenceDigest());
        assertThat(differentStatus.evidenceDigest()).isNotEqualTo(first.evidenceDigest());
    }

    @Test
    void rejectsMissingProviderCorrelationKey() {
        assertThatThrownBy(() -> adapter.query(recovery(" ")))
            .isInstanceOf(ExternalStatusQueryPermanentException.class);
    }

    @Test
    void rejectsCorrelationKeyThatProviderDidNotRecord() {
        assertThatThrownBy(() -> adapter.query(recovery("provider-request-not-found")))
            .isInstanceOf(ExternalStatusQueryPermanentException.class)
            .hasMessageContaining("not found");
    }

    private ExternalInteractionRecovery recovery(String correlationKey) {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-01T00:00:00Z");
        return new ExternalInteractionRecovery(
            "recovery-1", "execution-1", "connector-execution-1",
            FakeDigitalAssetStatusQueryAdapter.CONNECTOR_ID, correlationKey,
            ConnectorStatus.SENT_UNKNOWN, null, RecoveryStatus.CLAIMED,
            RetryDisposition.RECONCILE_FIRST, 1, 5, now, "worker-1",
            now.plusMinutes(1), null, null, null
        );
    }
}
