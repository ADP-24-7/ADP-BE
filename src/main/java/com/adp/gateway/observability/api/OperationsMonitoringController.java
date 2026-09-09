package com.adp.gateway.observability.api;

import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.observability.application.OperationsMonitoringService;
import com.adp.gateway.observability.domain.OperationsSummary;
import com.adp.gateway.observability.domain.PolicyOperationEvent.PolicyEventCategory;
import com.adp.gateway.observability.domain.PolicyOperationEventPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/operations")
public class OperationsMonitoringController {

    private final OperationsMonitoringService service;

    public OperationsMonitoringController(OperationsMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    OperationsSummary summary(
        @RequestParam(defaultValue = "60") @Min(5) @Max(1440) int windowMinutes,
        Authentication authentication
    ) {
        return service.summary(principal(authentication), windowMinutes);
    }

    @GetMapping("/policy-events")
    PolicyOperationEventPage policyEvents(
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(required = false) PolicyEventCategory category,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime to,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return service.policyEvents(
            principal(authentication), blankToNull(workloadId), category, from, to, page, size
        );
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
