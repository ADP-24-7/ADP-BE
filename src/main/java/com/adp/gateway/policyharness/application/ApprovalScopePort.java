package com.adp.gateway.policyharness.application;

import java.time.OffsetDateTime;

import com.adp.gateway.policyharness.domain.ApprovalScope;

public interface ApprovalScopePort {

    ApprovalScope load(String approvalReference, OffsetDateTime requestStartedAt);
}
