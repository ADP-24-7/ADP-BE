package com.adp.gateway.operations.api;

import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.application.SecurityFindingReadService;
import com.adp.gateway.operations.domain.SecurityFindingDetail;
import com.adp.gateway.operations.domain.SecurityFindingPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/security-findings")
public class SecurityFindingController {
    private final SecurityFindingReadService service;

    public SecurityFindingController(SecurityFindingReadService service) {
        this.service = service;
    }

    @GetMapping
    SecurityFindingPage search(
        @RequestParam(required = false) ExecutionPackType executionPack,
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(required = false) @Size(max = 120) String findingType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return service.search(
            principal(authentication), executionPack, blankToNull(workloadId), blankToNull(findingType),
            from, to, page, size
        );
    }

    @GetMapping("/{findingId}")
    SecurityFindingDetail detail(
        @PathVariable @Min(1) long findingId,
        Authentication authentication
    ) {
        return service.load(principal(authentication), findingId);
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
