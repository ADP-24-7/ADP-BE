package com.adp.gateway.auth.application;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;

public record AuthorizationRequest(
    AuthPrincipal principal,
    String workloadId,
    RuntimeAction action,
    String purpose,
    SubjectRef subject
) {
}
