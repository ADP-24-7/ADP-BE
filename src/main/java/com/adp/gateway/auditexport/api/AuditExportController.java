package com.adp.gateway.auditexport.api;

import com.adp.gateway.auditexport.application.AuditExportService;
import com.adp.gateway.auditexport.domain.AuditExportDetail;
import com.adp.gateway.auditexport.domain.AuditExportJob;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.common.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/audit-exports")
public class AuditExportController {
    private final AuditExportService service;

    public AuditExportController(AuditExportService service) {
        this.service = service;
    }

    @PostMapping
    AuditExportJob request(
        @Valid @RequestBody AuditExportRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        return service.request(principal(authentication), body.executionId(), body.reportType(), body.format(),
            body.reason(), body.idempotencyKey(), requestId(request), traceId(request));
    }

    @GetMapping("/{exportId}")
    AuditExportDetail get(
        @PathVariable @Size(max = 80) String exportId,
        Authentication authentication
    ) {
        return service.get(principal(authentication), exportId);
    }

    @PostMapping("/{exportId}/approval")
    AuditExportJob approval(
        @PathVariable @Size(max = 80) String exportId,
        @Valid @RequestBody AuditExportApprovalRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        return switch (body.action()) {
            case APPROVE -> service.approve(principal(authentication), exportId, body.reason(),
                requestId(request), traceId(request));
            case REJECT -> service.reject(principal(authentication), exportId, body.reason(),
                requestId(request), traceId(request));
            case REVOKE -> service.revoke(principal(authentication), exportId, body.reason(),
                requestId(request), traceId(request));
        };
    }

    @GetMapping("/{exportId}/download")
    ResponseEntity<byte[]> download(
        @PathVariable @Size(max = 80) String exportId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        var download = service.download(principal(authentication), exportId, requestId(request), traceId(request));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(download.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(download.fileName()).build().toString())
            .header("X-Content-SHA256", download.contentDigest())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(download.content());
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    private String requestId(HttpServletRequest request) {
        return attribute(request, TraceHeaders.REQUEST_ID_ATTRIBUTE);
    }

    private String traceId(HttpServletRequest request) {
        return attribute(request, TraceHeaders.TRACE_ID_ATTRIBUTE);
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
