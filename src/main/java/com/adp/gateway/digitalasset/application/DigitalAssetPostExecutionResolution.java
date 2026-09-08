package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionEvidence;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationAssessment;

public record DigitalAssetPostExecutionResolution(
    DigitalAssetPostExecutionEvidence evidence,
    DigitalAssetReconciliationAssessment assessment
) {
}
