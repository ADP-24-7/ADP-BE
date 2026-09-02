package com.adp.gateway.runtime.api;

import java.util.List;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.trace.RuntimeContextFactory;
import com.adp.gateway.runtime.application.RuntimeExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/runtime/executions")
public class RuntimeExecutionController {

    private final RuntimeContextFactory runtimeContextFactory;
    private final RuntimeExecutionService runtimeExecutionService;

    public RuntimeExecutionController(
        RuntimeContextFactory runtimeContextFactory,
        RuntimeExecutionService runtimeExecutionService
    ) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.runtimeExecutionService = runtimeExecutionService;
    }

    @PostMapping
    public ResponseEntity<RuntimeExecutionResponse> execute(
        @Valid @RequestBody RuntimeExecutionRequest request,
        HttpServletRequest httpRequest,
        Authentication authentication
    ) {
        RuntimeRequestContext context = runtimeContextFactory.create(
            httpRequest,
            request.workloadId(),
            request.purposeCode(),
            request.subjectScope(),
            request.idempotencyKey()
        );
        var result = runtimeExecutionService.execute(
            context,
            (AuthPrincipal) authentication.getPrincipal(),
            request.institutionId(),
            request.approvalReference(),
            request.destinationProfileId(),
            request.processingContexts() == null ? List.of() : request.processingContexts(),
            request.input()
        );
        return ResponseEntity.ok(RuntimeExecutionResponse.from(result));
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<RuntimeExecutionTraceResponse> get(
        @PathVariable String executionId,
        Authentication authentication
    ) {
        var trace = runtimeExecutionService.load(executionId);
        authorizeRead(authentication, trace.workloadId());
        return ResponseEntity.ok(RuntimeExecutionTraceResponse.from(trace));
    }

    @GetMapping("/{executionId}/trace")
    public ResponseEntity<RuntimeExecutionTraceEventsResponse> trace(
        @PathVariable String executionId,
        Authentication authentication
    ) {
        var trace = runtimeExecutionService.load(executionId);
        authorizeRead(authentication, trace.workloadId());
        return ResponseEntity.ok(RuntimeExecutionTraceEventsResponse.from(trace));
    }

    private void authorizeRead(Authentication authentication, String workloadId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (!principal.canAccessWorkload(workloadId)) {
            throw new AccessDeniedException("Runtime execution is not visible to this principal");
        }
    }
}
