package com.adp.gateway.retrieval.application;

import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.retrieval.domain.RetrievalProfile;
import com.adp.gateway.retrieval.domain.RetrievalResult;

public interface PredefinedRetrievalAdapter {

    boolean supports(String workloadId);

    void validateProfile(RetrievalProfile profile);

    RetrievalResult retrieve(DataAccessRequest request, RetrievalProfile profile);
}
