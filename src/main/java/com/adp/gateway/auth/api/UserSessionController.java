package com.adp.gateway.auth.api;

import com.adp.gateway.auth.application.UserSessionAuthenticationService;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UserSessionController {

    private final UserSessionAuthenticationService authenticationService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public UserSessionController(
        UserSessionAuthenticationService authenticationService,
        SecurityContextRepository securityContextRepository,
        SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) {
        this.authenticationService = authenticationService;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthContextResponse> login(
        @Valid @RequestBody LoginRequest loginRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        AuthPrincipal principal = authenticationService.authenticate(
            loginRequest.principalId().trim(),
            loginRequest.password()
        );
        AuthenticatedPrincipal authentication = new AuthenticatedPrincipal(
            principal,
            principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList()
        );
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(AuthContextResponse.from(principal));
    }

    @GetMapping("/me")
    public AuthContextResponse me(AuthenticatedPrincipal authentication) {
        return AuthContextResponse.from(authentication.principal());
    }
}
