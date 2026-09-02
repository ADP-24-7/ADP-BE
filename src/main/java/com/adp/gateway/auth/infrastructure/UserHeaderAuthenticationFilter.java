package com.adp.gateway.auth.infrastructure;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.AuthenticatedPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

public class UserHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-ADP-User-Id";
    public static final String USER_ROLES_HEADER = "X-ADP-User-Roles";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Set<AdpRole> roles = roles(request.getHeader(USER_ROLES_HEADER));
        if (roles == null) {
            filterChain.doFilter(request, response);
            return;
        }
        AuthPrincipal principal = new AuthPrincipal(
            userId.trim(),
            PrincipalType.USER,
            userId.trim(),
            "institution_local",
            false,
            Set.of("*"),
            roles
        );

        AuthenticatedPrincipal authentication = new AuthenticatedPrincipal(
            principal,
            roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList()
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private Set<AdpRole> roles(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(AdpRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
