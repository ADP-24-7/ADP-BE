package com.adp.gateway.runtime.api;

import java.util.List;
import com.adp.gateway.ai.domain.AiEvaluationReference;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.trace.RuntimeContextFactory;
import com.adp.gateway.runtime.application.RuntimeExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
    @Operation(
        summary = "Execute an approved runtime request",
        parameters = @Parameter(
            name = "X-ADP-Request-Timestamp",
            in = ParameterIn.HEADER,
            required = true,
            description = "UTC ISO-8601 request time used for replay-window validation",
            example = "2026-09-09T01:00:00Z"
        ),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(
                name = "digitalAsset",
                summary = "DA-P0-3 Digital Asset execution",
                value = RuntimeOpenApiExamples.DIGITAL_ASSET_EXECUTION
            ))
        )
    )
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
        var submission = runtimeExecutionService.submit(
                context,
                (AuthPrincipal) authentication.getPrincipal(),
                request.institutionId(),
                request.approvalReference(),
                request.destinationProfileId(),
                request.processingContexts() == null ? List.of() : request.processingContexts(),
                request.input(),
                evaluationReference(request)
            );
        return ResponseEntity.ok(submission.isReplay()
            ? RuntimeExecutionResponse.from(submission.replay())
            : RuntimeExecutionResponse.from(submission.result()));
    }

    private AiEvaluationReference evaluationReference(RuntimeExecutionRequest request) {
        if (request.evaluationRunId() == null && request.evalCaseId() == null) {
            return null;
        }
        if (request.evaluationRunId() == null || request.evaluationRunId().isBlank()
            || request.evalCaseId() == null || request.evalCaseId().isBlank()) {
            throw new com.adp.gateway.ai.application.AiEvaluationRunMismatchException(
                "AI_EVALUATION_REFERENCE_INCOMPLETE"
            );
        }
        return new AiEvaluationReference(request.evaluationRunId(), request.evalCaseId(), null, null, null, null);
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<RuntimeExecutionTraceResponse> get(
        @PathVariable String executionId,
        Authentication authentication
    ) {
        var trace = runtimeExecutionService.load(executionId);
        authorizeRead(authentication, trace.workloadId(), trace.institutionId());
        return ResponseEntity.ok(RuntimeExecutionTraceResponse.from(
            trace,
            runtimeExecutionService.loadDigitalAssetSnapshot(executionId).orElse(null),
            runtimeExecutionService.loadDigitalAssetPreExecutionGuard(executionId).orElse(null),
            runtimeExecutionService.loadDigitalAssetPostExecutionEvidence(executionId).orElse(null)
        ));
    }

    @GetMapping("/{executionId}/trace")
    public ResponseEntity<RuntimeExecutionTraceEventsResponse> trace(
        @PathVariable String executionId,
        Authentication authentication
    ) {
        var trace = runtimeExecutionService.load(executionId);
        authorizeRead(authentication, trace.workloadId(), trace.institutionId());
        return ResponseEntity.ok(RuntimeExecutionTraceEventsResponse.from(
            trace,
            runtimeExecutionService.loadDigitalAssetSnapshot(executionId).orElse(null),
            runtimeExecutionService.loadDigitalAssetPreExecutionGuard(executionId).orElse(null),
            runtimeExecutionService.loadDigitalAssetPostExecutionEvidence(executionId).orElse(null)
        ));
    }

    private void authorizeRead(Authentication authentication, String workloadId, String institutionId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (!principal.canAccessWorkload(workloadId)
            || principal.institutionId() == null
            || !principal.institutionId().equals(institutionId)) {
            throw new AccessDeniedException("Runtime execution is not visible to this principal");
        }
    }
}
