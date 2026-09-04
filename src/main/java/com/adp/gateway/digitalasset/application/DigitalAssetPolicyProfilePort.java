package com.adp.gateway.digitalasset.application;

import java.time.OffsetDateTime;

import com.adp.gateway.digitalasset.domain.DigitalAssetPolicyProfile;

public interface DigitalAssetPolicyProfilePort {

    DigitalAssetPolicyProfile load(String destinationProfileId, OffsetDateTime requestStartedAt);
}
