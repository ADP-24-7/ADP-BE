package com.adp.gateway.policyharness.infrastructure;

import java.time.OffsetDateTime;

import com.adp.gateway.policyharness.application.ApprovalScopeNotFoundException;
import com.adp.gateway.policyharness.application.ApprovalScopePort;
import com.adp.gateway.policyharness.domain.ApprovalScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ApprovalScopePort.class)
public class UnconfiguredApprovalScopeAdapter implements ApprovalScopePort {

    @Override
    public ApprovalScope load(String approvalReference, OffsetDateTime requestStartedAt) {
        throw new ApprovalScopeNotFoundException(approvalReference);
    }
}
