package com.adp.gateway.runtime.api;

import com.adp.gateway.runtime.domain.ControlledDeliveryResult;

public record ControlledDeliveryResponse(
    String deliveryStatus,
    String content,
    String responseDigest
) {

    public static ControlledDeliveryResponse from(ControlledDeliveryResult result) {
        return new ControlledDeliveryResponse(
            result.deliveryStatus(),
            result.content(),
            result.responseDigest()
        );
    }
}
