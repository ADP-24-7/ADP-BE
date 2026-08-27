package com.adp.gateway.operations.api;

import com.adp.gateway.auth.application.AuthorizationRequest;
import com.adp.gateway.auth.application.AuthorizationService;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.common.trace.TraceHeaders;
import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.retrieval.application.RetrievalService;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime/data-access")
@ConditionalOnProperty(name = "adp.data-access-preview.enabled", havingValue = "true")
public class DataAccessPreviewController {

    private final AuthorizationService authorizationService;
    private final RetrievalService retrievalService;

    public DataAccessPreviewController(
        AuthorizationService authorizationService,
        RetrievalService retrievalService
    ) {
        this.authorizationService = authorizationService;
        this.retrievalService = retrievalService;
    }

    @PostMapping("/preview")
    public ResponseEntity<DataAccessPreviewResponse> preview(
        @Valid @RequestBody DataAccessPreviewRequest request,
        HttpServletRequest httpRequest,
        Authentication authentication
    ) {
        SubjectRef subject = SubjectRef.from(request.subject());
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (!authorizationService.authorize(new AuthorizationRequest(
            principal,
            request.workloadId(),
            RuntimeAction.RUNTIME_EXECUTE,
            request.purpose(),
            subject
        )).allowed()) {
            throw new AccessDeniedException("Runtime execution is not allowed");
        }

        RetrievalResult result = retrievalService.retrieve(new DataAccessRequest(
            attribute(httpRequest, TraceHeaders.REQUEST_ID_ATTRIBUTE),
            attribute(httpRequest, TraceHeaders.TRACE_ID_ATTRIBUTE),
            request.workloadId(),
            request.purpose(),
            subject
        ));
        return ResponseEntity.ok(DataAccessPreviewResponse.from(result));
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
