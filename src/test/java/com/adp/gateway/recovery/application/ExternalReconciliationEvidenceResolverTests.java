package com.adp.gateway.recovery.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.junit.jupiter.api.Test;

class ExternalReconciliationEvidenceResolverTests {
    @Test
    void allowsConnectorsWithoutDomainEvidenceAdapter() {
        var resolver = new ExternalReconciliationEvidenceResolver(List.of());

        assertThatCode(() -> resolver.reconcile(recovery(), status())).doesNotThrowAnyException();
    }

    @Test
    void invokesTheSingleMatchingDomainEvidenceAdapter() {
        ExternalReconciliationEvidencePort port = mock(ExternalReconciliationEvidencePort.class);
        when(port.supports("connector-1")).thenReturn(true);
        var resolver = new ExternalReconciliationEvidenceResolver(List.of(port));

        resolver.reconcile(recovery(), status());

        verify(port).reconcile(recovery(), status());
    }

    @Test
    void rejectsAmbiguousDomainEvidenceAdapters() {
        ExternalReconciliationEvidencePort first = mock(ExternalReconciliationEvidencePort.class);
        ExternalReconciliationEvidencePort second = mock(ExternalReconciliationEvidencePort.class);
        when(first.supports("connector-1")).thenReturn(true);
        when(second.supports("connector-1")).thenReturn(true);
        var resolver = new ExternalReconciliationEvidenceResolver(List.of(first, second));

        assertThatThrownBy(() -> resolver.reconcile(recovery(), status()))
            .isInstanceOf(AmbiguousExternalStatusQueryAdapterException.class);
    }

    private ExternalInteractionRecovery recovery() {
        return new ExternalInteractionRecovery(
            "rec-1", "exec-1", "con-1", "connector-1", "provider-request-1",
            com.adp.gateway.connector.domain.ConnectorStatus.SENT_UNKNOWN, null,
            com.adp.gateway.recovery.domain.RecoveryStatus.CLAIMED,
            com.adp.gateway.recovery.domain.RetryDisposition.RECONCILE_FIRST,
            1, 5, null, "worker-1", null, null, null, null
        );
    }

    private ExternalStatusQueryResult status() {
        return new ExternalStatusQueryResult(
            com.adp.gateway.connector.domain.ConnectorStatus.ACKNOWLEDGED, "a".repeat(64)
        );
    }
}
