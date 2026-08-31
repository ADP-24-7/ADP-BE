package com.adp.gateway.transform.application;

import com.adp.gateway.retrieval.domain.DataClass;

public interface VaultTokenPort {

    String tokenFor(String scope, DataClass dataClass, String sourceValueDigest);
}
