package com.adp.gateway.transform.infrastructure;

import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.adp.gateway.transform.application.PseudonymizationKey;
import com.adp.gateway.transform.application.PseudonymizationKeyPort;
import org.springframework.stereotype.Component;

@Component
public class LocalInMemoryPseudonymizationKeyAdapter implements PseudonymizationKeyPort {

    private final SecretKey secretKey;

    public LocalInMemoryPseudonymizationKeyAdapter() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        this.secretKey = new SecretKeySpec(key, "HmacSHA256");
    }

    @Override
    public PseudonymizationKey load(String keyVersion) {
        return new PseudonymizationKey(keyVersion, secretKey);
    }
}
