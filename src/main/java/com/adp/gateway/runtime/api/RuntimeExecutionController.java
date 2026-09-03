package com.adp.gateway.runtime.api;

import java.util.List;
import java.time.Duration;
import java.time.Instant;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.trace.RuntimeContextFactory;
import com.adp.gateway.runtime.application.RuntimeExecutionService;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.runtime.application.IdempotencyKeyConflictException;
import com.adp.gateway.runtime.application.IdempotencyRequestInProgressException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/v1/runtime/executions")
public class RuntimeExecutionController {

    private static final Logger log = LoggerFactory.getLogger(RuntimeExecutionController.class);

    private final RuntimeContextFactory runtimeContextFactory;
    private final RuntimeExecutionService runtimeExecutionService;
    private final GatewayObservability observability;

    public RuntimeExecutionController(
        RuntimeContextFactory runtimeContextFactory,
        RuntimeExecutionService runtimeExecutionService,
        GatewayObservability observability
    ) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.runtimeExecutionService = runtimeExecutionService;
        this.observability = observability;
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
        Instant startedAt = Instant.now();
        try {
            var submission = runtimeExecutionService.submit(
                context,
                (AuthPrincipal) authentication.getPrincipal(),
                request.institutionId(),
                request.approvalReference(),
                request.destinationProfileId(),
                request.processingContexts() == null ? List.of() : request.processingContexts(),
                request.input()
            );
            String outcome = submission.isReplay() ? "REPLAYED" : "CREATED";
            observability.idempotency(outcome);
            observability.runtimeSubmission(outcome, Duration.between(startedAt, Instant.now()));
            log.info("runtime_submission outcome={}", outcome);
            return ResponseEntity.ok(submission.isReplay()
                ? RuntimeExecutionResponse.from(submission.replay())
                : RuntimeExecutionResponse.from(submission.result()));
        } catch (IdempotencyKeyConflictException exception) {
            observability.idempotency("CONFLICT");
            observability.runtimeSubmission("REJECTED", Duration.between(startedAt, Instant.now()));
            log.info("runtime_submission outcome=REJECTED reason=IDEMPOTENCY_CONFLICT");
            throw exception;
        } catch (IdempotencyRequestInProgressException exception) {
            observability.idempotency("IN_PROGRESS");
            observability.runtimeSubmission("REJECTED", Duration.between(startedAt, Instant.now()));
            log.info("runtime_submission outcome=REJECTED reason=IDEMPOTENCY_IN_PROGRESS");
            throw exception;
        } catch (RuntimeException exception) {
            observability.runtimeSubmission("FAILED", Duration.between(startedAt, Instant.now()));
            log.warn("runtime_submission outcome=FAILED error_category=RUNTIME_EXCEPTION");
            throw exception;
        }
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<RuntimeExecutionTraceResponse> get(
        @PathVariable String executionId,
        Authentication authentication
    ) {
        var trace = runtimeExecutionService.load(executionId);
        authorizeRead(authentication, trace.workloadId(), trace.institutionId());
        return ResponseEntity.ok(RuntimeExecutionTraceResponse.from(trace));
    }

    @GetMapping("/{executionId}/trace")
    public ResponseEntity<RuntimeExecutionTraceEventsResponse> trace(
        @PathVariable String executionId,
        Authentication authentication
    ) {
        var trace = runtimeExecutionService.load(executionId);
        authorizeRead(authentication, trace.workloadId(), trace.institutionId());
        return ResponseEntity.ok(RuntimeExecutionTraceEventsResponse.from(trace));
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
