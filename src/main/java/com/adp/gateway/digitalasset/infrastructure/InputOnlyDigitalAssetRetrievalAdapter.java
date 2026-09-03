package com.adp.gateway.digitalasset.infrastructure;

import java.util.List;

import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.digitalasset.application.DigitalAssetCanonicalContextBuilder;
import com.adp.gateway.retrieval.application.PredefinedRetrievalAdapter;
import com.adp.gateway.retrieval.domain.RetrievalProfile;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import org.springframework.stereotype.Component;

@Component
public class InputOnlyDigitalAssetRetrievalAdapter implements PredefinedRetrievalAdapter {
    @Override
    public boolean supports(String workloadId) {
        return DigitalAssetCanonicalContextBuilder.WORKLOAD_ID.equals(workloadId);
    }

    @Override
    public void validateProfile(RetrievalProfile profile) {
        if (profile.datasetScopes().stream().anyMatch(scope -> !"request".equals(scope.datasetName()))
            || profile.fields().stream().anyMatch(field -> !"request".equals(field.datasetName()))) {
            throw new IllegalArgumentException("Digital asset profile may declare request fields only");
        }
    }

    @Override
    public RetrievalResult retrieve(DataAccessRequest request, RetrievalProfile profile) {
        return new RetrievalResult(null, request.workloadId(), request.purpose(), request.subject().subjectType(),
            request.subject().subjectId(), profile.profileId(), 0,
            profile.datasetScopes(), profile.fields(), List.of());
    }
}
