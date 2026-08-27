package com.adp.gateway.retrieval.domain;

import java.util.Optional;

public interface RetrievalProfilePort {

    Optional<RetrievalProfile> findEnabled(String workloadId, String purpose, String subjectType);
}
