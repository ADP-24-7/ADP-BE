package com.adp.gateway.auth.application;

import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;

public interface SubjectAuthorizationPort {

    boolean canAccess(
        String principalId,
        String workloadId,
        RuntimeAction action,
        String purpose,
        SubjectRef subject
    );
}
