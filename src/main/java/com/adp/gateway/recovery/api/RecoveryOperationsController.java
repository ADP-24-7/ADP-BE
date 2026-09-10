package com.adp.gateway.recovery.api;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.recovery.application.RecoveryOperationsService;
import com.adp.gateway.recovery.domain.RecoveryCommandResult;
import com.adp.gateway.recovery.domain.RecoveryIncidentDetail;
import com.adp.gateway.recovery.domain.RecoveryIncidentPage;
import com.adp.gateway.recovery.domain.RecoveryOperationType;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/recovery/incidents")
public class RecoveryOperationsController {

    private final RecoveryOperationsService service;

    public RecoveryOperationsController(RecoveryOperationsService service) {
        this.service = service;
    }

    @GetMapping
    RecoveryIncidentPage search(
        @Parameter(description = "생략 시 전체 허용 Workload의 모든 Pack을 조회합니다.")
        @RequestParam(required = false) ExecutionPackType executionPack,
        @RequestParam(required = false) RecoveryStatus status,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return service.search(principal(authentication), executionPack, status, page, size);
    }

    @GetMapping("/{recoveryId}")
    RecoveryIncidentDetail detail(
        @PathVariable @Size(max = 80) String recoveryId,
        @Parameter(description = "지정 시 Incident의 server-owned Pack과 일치해야 합니다.")
        @RequestParam(required = false) ExecutionPackType executionPack,
        Authentication authentication
    ) {
        return service.load(principal(authentication), recoveryId, executionPack);
    }

    @PostMapping("/{recoveryId}/reconcile")
    RecoveryCommandResult reconcile(
        @PathVariable @Size(max = 80) String recoveryId,
        @RequestParam(required = false) ExecutionPackType executionPack,
        @Valid @RequestBody RecoveryCommandRequest request,
        Authentication authentication
    ) {
        return service.command(
            principal(authentication), recoveryId, executionPack,
            request.operationId(), RecoveryOperationType.RECONCILE
        );
    }

    @PostMapping("/{recoveryId}/retry")
    RecoveryCommandResult retry(
        @PathVariable @Size(max = 80) String recoveryId,
        @RequestParam(required = false) ExecutionPackType executionPack,
        @Valid @RequestBody RecoveryCommandRequest request,
        Authentication authentication
    ) {
        return service.command(
            principal(authentication), recoveryId, executionPack,
            request.operationId(), RecoveryOperationType.RETRY
        );
    }

    @PostMapping("/{recoveryId}/review")
    RecoveryCommandResult markReview(
        @PathVariable @Size(max = 80) String recoveryId,
        @RequestParam(required = false) ExecutionPackType executionPack,
        @Valid @RequestBody RecoveryCommandRequest request,
        Authentication authentication
    ) {
        return service.command(
            principal(authentication), recoveryId, executionPack,
            request.operationId(), RecoveryOperationType.MARK_REVIEW
        );
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    public record RecoveryCommandRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[A-Za-z0-9:_-]+")
        String operationId
    ) {
    }
}
