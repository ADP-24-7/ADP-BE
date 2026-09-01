package com.adp.gateway.egress.domain;

public record OutboundSensitiveFinding(
    String findingType,
    String detectorVersion,
    String evidenceDigest
) {
}
