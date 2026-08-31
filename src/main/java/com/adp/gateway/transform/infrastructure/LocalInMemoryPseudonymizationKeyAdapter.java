package com.adp.gateway.transform.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.adp.gateway.transform.application.PseudonymizationKey;
import com.adp.gateway.transform.application.PseudonymizationKeyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class LocalInMemoryPseudonymizationKeyAdapter implements PseudonymizationKeyPort {

    private final Map<String, SecretKey> keys = Map.of(
        "project-provisional-key-v1",
        new SecretKeySpec("project-provisional-hmac-key-v1-32b".getBytes(StandardCharsets.UTF_8), "HmacSHA256")
    );

    public LocalInMemoryPseudonymizationKeyAdapter() {
    }

    @Override
    public PseudonymizationKey load(String keyVersion) {
        SecretKey secretKey = keys.get(keyVersion);
        if (secretKey == null) {
            throw new IllegalStateException("Pseudonymization key is not configured for version: " + keyVersion);
        }
        return new PseudonymizationKey(keyVersion, secretKey);
    }
}
