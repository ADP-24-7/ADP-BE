package com.adp.gateway.digitalasset.domain;

public record ApprovedTransactionReference(String value) {
    public ApprovedTransactionReference {
        if (value == null || value.isBlank() || value.length() > 160) {
            throw new IllegalArgumentException("DIGITAL_ASSET_APPROVED_TRANSACTION_REFERENCE_INVALID");
        }
    }
}
