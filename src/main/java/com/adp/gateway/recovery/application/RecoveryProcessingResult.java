package com.adp.gateway.recovery.application;

import com.adp.gateway.recovery.domain.RecoveryStatus;

public record RecoveryProcessingResult(
    RecoveryStatus recoveryStatus,
    String reasonCode,
    String evidenceDigest
) {
}
