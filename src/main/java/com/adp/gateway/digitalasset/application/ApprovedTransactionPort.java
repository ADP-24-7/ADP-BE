package com.adp.gateway.digitalasset.application;

import java.util.Optional;

import com.adp.gateway.digitalasset.domain.ApprovedTransactionSnapshot;

public interface ApprovedTransactionPort {
    Optional<ApprovedTransactionSnapshot> find(ApprovedTransactionLookup lookup);
}
