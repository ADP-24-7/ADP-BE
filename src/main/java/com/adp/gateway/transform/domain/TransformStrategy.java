package com.adp.gateway.transform.domain;

public enum TransformStrategy {
    MASK,
    HMAC_PSEUDO,
    VAULT_TOKEN,
    REMOVE,
    KEEP,
    GENERALIZE,
    FIELD_SEPARATION
}
