package com.adp.gateway.retrieval.application;

import java.util.List;

import com.adp.gateway.dataaccess.application.DataAccessAuditRecorder;
import com.adp.gateway.dataaccess.application.DataAccessDeniedException;
import com.adp.gateway.dataaccess.application.DataAccessGuard;
import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.retrieval.domain.RetrievalProfile;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private final DataAccessGuard dataAccessGuard;
    private final DataAccessAuditRecorder auditRecorder;
    private final List<PredefinedRetrievalAdapter> adapters;

    public RetrievalService(
        DataAccessGuard dataAccessGuard,
        DataAccessAuditRecorder auditRecorder,
        List<PredefinedRetrievalAdapter> adapters
    ) {
        this.dataAccessGuard = dataAccessGuard;
        this.auditRecorder = auditRecorder;
        this.adapters = adapters;
    }

    public RetrievalResult retrieve(DataAccessRequest request) {
        RetrievalProfile profile = dataAccessGuard.authorize(request);
        PredefinedRetrievalAdapter adapter = adapters.stream()
            .filter(candidate -> candidate.supports(request.workloadId()))
            .findFirst()
            .orElseThrow(() -> new DataAccessDeniedException("Predefined retrieval adapter is not registered"));

        RetrievalResult result = adapter.retrieve(request, profile);
        String dataAccessId = auditRecorder.record(request, result);
        return new RetrievalResult(
            dataAccessId,
            result.workloadId(),
            result.purpose(),
            result.subjectType(),
            result.subjectId(),
            result.profileId(),
            result.rowLimit(),
            result.rowCount(),
            result.selectedFields(),
            result.records()
        );
    }
}
