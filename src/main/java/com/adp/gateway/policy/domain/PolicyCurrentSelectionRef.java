package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record PolicyCurrentSelectionRef(
    PolicyLayer policyLayer,
    ExecutionPackType executionPack,
    long artifactRevision,
    long selectionRevision,
    OffsetDateTime selectedAt
) {
}
