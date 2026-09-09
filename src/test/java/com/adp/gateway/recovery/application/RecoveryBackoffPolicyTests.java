package com.adp.gateway.recovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RecoveryBackoffPolicyTests {

    @Test
    void appliesExponentialDelayWithConfiguredMaximum() {
        var policy = new RecoveryBackoffPolicy(Duration.ofMinutes(1), Duration.ofMinutes(5));

        assertThat(policy.delayFor(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayFor(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.delayFor(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(policy.delayFor(4)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.delayFor(20)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RecoveryBackoffPolicy(Duration.ZERO, Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecoveryBackoffPolicy(Duration.ofMinutes(2), Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
