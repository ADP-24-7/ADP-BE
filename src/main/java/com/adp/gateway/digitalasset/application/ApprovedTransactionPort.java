package com.adp.gateway.digitalasset.application;

import java.util.Optional;

import com.adp.gateway.digitalasset.domain.ApprovedTransaction;

public interface ApprovedTransactionPort {
    Optional<ApprovedTransaction> find(ApprovedTransactionLookup lookup);
}
