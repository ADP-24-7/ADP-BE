package com.adp.gateway.transform.infrastructure;

import com.adp.gateway.transform.application.PseudonymizationKey;
import com.adp.gateway.transform.application.PseudonymizationKeyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredPseudonymizationKeyAdapter implements PseudonymizationKeyPort {

    @Override
    public PseudonymizationKey load(String keyVersion) {
        throw new IllegalStateException("Pseudonymization key provider is not configured");
    }
}
