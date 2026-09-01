package com.adp.gateway.egress.application;

import com.adp.gateway.egress.domain.DestinationProfile;

public interface DestinationProfilePort {

    DestinationProfile load(String providerProfileId);
}
