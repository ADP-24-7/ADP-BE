package com.adp.gateway.runtime.domain;

public record ControlledDeliveryResult(
    String deliveryStatus,
    String content,
    String responseDigest,
    String reasonCode
) {

    public static ControlledDeliveryResult delivered(String content, String responseDigest) {
        return new ControlledDeliveryResult("DELIVERED", content, responseDigest, null);
    }

    public static ControlledDeliveryResult withheld(String responseDigest, String reasonCode) {
        return new ControlledDeliveryResult("WITHHELD", null, responseDigest, reasonCode);
    }

    public boolean isDelivered() {
        return "DELIVERED".equals(deliveryStatus);
    }
}
