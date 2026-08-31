package com.adp.gateway.transform.application;

import javax.crypto.SecretKey;

public record PseudonymizationKey(
    String keyVersion,
    SecretKey secretKey
) {
}
