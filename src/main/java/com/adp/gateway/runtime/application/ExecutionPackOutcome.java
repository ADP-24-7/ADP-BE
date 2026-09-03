package com.adp.gateway.runtime.application;

import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;

public record ExecutionPackOutcome(
    RuntimeExecutionStatus runtimeStatus,
    ControlledDeliveryResult controlledDelivery
) {
}
