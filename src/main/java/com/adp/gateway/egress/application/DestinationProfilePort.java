package com.adp.gateway.egress.application;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.DestinationProfile;

public interface DestinationProfilePort {

    DestinationProfile load(String destinationProfileId, OffsetDateTime requestStartedAt);
}
