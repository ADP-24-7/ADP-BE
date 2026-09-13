package com.adp.gateway.ai.domain;

import java.util.Set;

public final class AiOverviewDataProtectionPolicy {
    private static final Set<String> PROTECTED_DATA_CLASSES = Set.of(
        "CUSTOMER_IDENTIFIER",
        "ACCOUNT_IDENTIFIER",
        "TRANSACTION_IDENTIFIER",
        "FINANCIAL_AMOUNT",
        "FINANCIAL_METADATA",
        "UNKNOWN"
    );

    private AiOverviewDataProtectionPolicy() { }

    public static boolean requiresProtection(String dataClass) {
        return dataClass == null || PROTECTED_DATA_CLASSES.contains(dataClass);
    }
}
