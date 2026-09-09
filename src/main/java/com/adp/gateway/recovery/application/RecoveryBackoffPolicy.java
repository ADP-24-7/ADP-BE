package com.adp.gateway.recovery.application;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecoveryBackoffPolicy {

    private final Duration initialDelay;
    private final Duration maxDelay;

    public RecoveryBackoffPolicy(
        @Value("${adp.recovery.initial-backoff:1m}") Duration initialDelay,
        @Value("${adp.recovery.max-backoff:15m}") Duration maxDelay
    ) {
        if (initialDelay.isNegative() || initialDelay.isZero() || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Recovery backoff configuration is invalid");
        }
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    public Duration delayFor(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 20));
        Duration delay = initialDelay.multipliedBy(1L << exponent);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}
