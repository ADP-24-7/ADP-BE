package com.adp.gateway.digitalasset.application;

import java.util.List;

import com.adp.gateway.digitalasset.domain.DigitalAssetComplianceContext;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetComplianceContextResolver {

    private final List<DigitalAssetComplianceContextPort> ports;

    public DigitalAssetComplianceContextResolver(List<DigitalAssetComplianceContextPort> ports) {
        this.ports = List.copyOf(ports);
    }

    public DigitalAssetComplianceContext load(String customerId, String accountId, String walletAddress) {
        if (ports.isEmpty()) {
            throw new DigitalAssetComplianceContextUnavailableException();
        }
        if (ports.size() > 1) {
            throw new IllegalStateException("Multiple digital asset compliance context providers are configured");
        }
        return ports.getFirst().load(customerId, accountId, walletAddress);
    }
}
