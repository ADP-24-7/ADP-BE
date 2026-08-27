package com.adp.gateway.auth.api;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/auth")
public class AuthContextController {

    @GetMapping("/context")
    public ResponseEntity<AuthContextResponse> context(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(new AuthContextResponse(
            principal.principalId(),
            principal.principalType().name(),
            principal.roles().stream().map(AdpRole::name).collect(java.util.stream.Collectors.toSet()),
            principal.workloadIds(),
            principal.subjectAuthorizationRequired()
        ));
    }
}
