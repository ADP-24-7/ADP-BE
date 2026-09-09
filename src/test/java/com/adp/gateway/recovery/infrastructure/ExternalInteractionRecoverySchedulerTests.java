package com.adp.gateway.recovery.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adp.gateway.recovery.application.ExternalInteractionRecoveryService;
import org.junit.jupiter.api.Test;

class ExternalInteractionRecoverySchedulerTests {

    @Test
    void processesAtMostConfiguredBatchSize() {
        ExternalInteractionRecoveryService service = mock(ExternalInteractionRecoveryService.class);
        when(service.processNext(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        var scheduler = new ExternalInteractionRecoveryScheduler(service, 3);

        scheduler.processDueIncidents();

        verify(service, times(3)).processNext(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsUnboundedBatchConfiguration() {
        ExternalInteractionRecoveryService service = mock(ExternalInteractionRecoveryService.class);

        assertThatThrownBy(() -> new ExternalInteractionRecoveryScheduler(service, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalInteractionRecoveryScheduler(service, 101))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
