package com.adp.gateway.policy.domain;

import java.util.Optional;

public interface RuntimeDataClassCrosswalkPort {

    Optional<CrosswalkMapping> resolve(String regulatoryDataCategory);
}
