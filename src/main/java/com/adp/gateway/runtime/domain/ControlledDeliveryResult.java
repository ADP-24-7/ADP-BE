package com.adp.gateway.runtime.domain;

public record ControlledDeliveryResult(
    String deliveryStatus,
    String content,
    String responseDigest
) {

    public static ControlledDeliveryResult delivered(String content, String responseDigest) {
        return new ControlledDeliveryResult("DELIVERED", content, responseDigest);
    }

    public static ControlledDeliveryResult withheld(String responseDigest) {
        return new ControlledDeliveryResult("WITHHELD", null, responseDigest);
    }

    public boolean isDelivered() {
        return "DELIVERED".equals(deliveryStatus);
    }
}
