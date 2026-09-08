package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.digitalasset.domain.TransactionDetailEvidence;

public interface TransactionDetailResolver {
    TransactionDetailEvidence resolveTransaction(ExternalExecutionResult result);
}
