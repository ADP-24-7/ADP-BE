package com.adp.gateway.dataaccess.application;

import com.adp.gateway.retrieval.domain.RetrievalProfile;
import com.adp.gateway.retrieval.domain.RetrievalProfilePort;
import com.adp.gateway.workload.domain.WorkloadRegistryPort;
import org.springframework.stereotype.Service;

@Service
public class DataAccessGuard {

    private final WorkloadRegistryPort workloadRegistryPort;
    private final RetrievalProfilePort retrievalProfilePort;

    public DataAccessGuard(
        WorkloadRegistryPort workloadRegistryPort,
        RetrievalProfilePort retrievalProfilePort
    ) {
        this.workloadRegistryPort = workloadRegistryPort;
        this.retrievalProfilePort = retrievalProfilePort;
    }

    public RetrievalProfile authorize(DataAccessRequest request) {
        if (request.subject() == null) {
            throw new DataAccessDeniedException("Subject is required");
        }
        workloadRegistryPort.findEnabled(request.workloadId())
            .orElseThrow(() -> new DataAccessDeniedException("Workload is not registered"));

        RetrievalProfile profile = retrievalProfilePort
            .findEnabled(request.workloadId(), request.purpose(), request.subject().subjectType())
            .orElseThrow(() -> new DataAccessDeniedException("Retrieval profile is not registered"));

        if (profile.datasetScopes().isEmpty() || profile.fields().isEmpty()) {
            throw new DataAccessDeniedException("Retrieval profile is invalid");
        }
        boolean invalidDatasetScope = profile.datasetScopes().stream()
            .anyMatch(scope -> scope.rowLimit() < 1);
        if (invalidDatasetScope) {
            throw new DataAccessDeniedException("Retrieval profile is invalid");
        }
        boolean fieldWithoutDatasetScope = profile.fields().stream()
            .anyMatch(field -> profile.scopeFor(field.datasetName()).isEmpty());
        if (fieldWithoutDatasetScope) {
            throw new DataAccessDeniedException("Retrieval profile is invalid");
        }
        return profile;
    }
}
