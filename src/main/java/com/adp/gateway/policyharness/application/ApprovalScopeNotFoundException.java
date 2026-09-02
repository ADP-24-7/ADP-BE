package com.adp.gateway.policyharness.application;

public class ApprovalScopeNotFoundException extends RuntimeException {

    public ApprovalScopeNotFoundException(String approvalReference) {
        super("Approval scope was not found: " + approvalReference);
    }
}
