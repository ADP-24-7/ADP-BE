package com.adp.gateway.policy.domain;

public record CrosswalkMapping(
    String crosswalkVersion,
    String regulatoryDataCategory,
    String runtimeDataClass,
    String mappingStatus,
    String mappingBasis,
    String version
) {
}
