package com.adp.gateway.egress.infrastructure;

import com.adp.gateway.egress.application.DestinationProfileNotFoundException;
import com.adp.gateway.egress.application.DestinationProfilePort;
import com.adp.gateway.egress.domain.DestinationProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredDestinationProfileAdapter implements DestinationProfilePort {

    @Override
    public DestinationProfile load(String providerProfileId) {
        throw new DestinationProfileNotFoundException(providerProfileId);
    }
}
