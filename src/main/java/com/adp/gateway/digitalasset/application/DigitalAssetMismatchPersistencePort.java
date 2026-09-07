package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationAssessment;

public interface DigitalAssetMismatchPersistencePort {
    void open(String executionId, DigitalAssetReconciliationAssessment assessment);
}
