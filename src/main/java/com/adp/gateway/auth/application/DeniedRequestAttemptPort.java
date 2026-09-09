package com.adp.gateway.auth.application;

import com.adp.gateway.auth.domain.DeniedRequestAttempt;

public interface DeniedRequestAttemptPort {

    void record(DeniedRequestAttempt attempt);
}
