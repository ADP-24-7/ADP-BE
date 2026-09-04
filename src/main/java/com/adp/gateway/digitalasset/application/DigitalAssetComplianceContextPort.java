package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.DigitalAssetComplianceContext;

public interface DigitalAssetComplianceContextPort {

    DigitalAssetComplianceContext load(String customerId, String accountId, String walletAddress);
}
