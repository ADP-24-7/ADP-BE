package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.digitalasset.domain.TransferExecutionEvidence;

public interface TokenTransferResolver {
    TransferExecutionEvidence resolveTokenTransfer(ExternalExecutionResult result);
}
