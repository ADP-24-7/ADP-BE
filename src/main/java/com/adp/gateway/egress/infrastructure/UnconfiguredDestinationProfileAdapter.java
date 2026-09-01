package com.adp.gateway.egress.infrastructure;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.application.DestinationProfileNotFoundException;
import com.adp.gateway.egress.application.DestinationProfilePort;
import com.adp.gateway.egress.domain.DestinationProfile;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredDestinationProfileAdapter implements DestinationProfilePort {

    private final MeterRegistry meterRegistry;

    public UnconfiguredDestinationProfileAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public DestinationProfile load(String destinationProfileId, OffsetDateTime requestStartedAt) {
        meterRegistry.counter("destination.profile.lookup.total", "result", "NOT_FOUND").increment();
        throw new DestinationProfileNotFoundException(destinationProfileId);
    }
}
