package com.adp.gateway.policy.application;

import java.util.List;

public record HandoffApplicability(
    String status,
    String scope,
    List<String> limitations
) {
}
