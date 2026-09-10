package com.adp.gateway.operations.api;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.operations.application.AdminIdentityReadService;
import com.adp.gateway.operations.domain.AdminIdentityDetail;
import com.adp.gateway.operations.domain.AdminIdentityPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/identities")
public class AdminIdentityController {
    private final AdminIdentityReadService service;

    public AdminIdentityController(AdminIdentityReadService service) {
        this.service = service;
    }

    @GetMapping
    AdminIdentityPage search(
        @RequestParam(required = false) PrincipalType principalType,
        @RequestParam(required = false) AdpRole role,
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(required = false) @Size(max = 160) String query,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return service.search(
            principal(authentication), principalType, role, blankToNull(workloadId), enabled,
            blankToNull(query), page, size
        );
    }

    @GetMapping("/{principalId}")
    AdminIdentityDetail detail(
        @PathVariable @Size(max = 80) String principalId,
        Authentication authentication
    ) {
        return service.load(principal(authentication), principalId);
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
