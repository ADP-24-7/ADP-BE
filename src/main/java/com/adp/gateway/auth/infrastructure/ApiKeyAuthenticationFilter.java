package com.adp.gateway.auth.infrastructure;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.application.ApiKeyHasher;
import com.adp.gateway.auth.application.AuthPrincipalLookup;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.AuthenticatedPrincipal;
import com.adp.gateway.common.error.ErrorResponse;
import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.common.trace.TraceHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-ADP-API-Key";

    private final ApiKeyHasher apiKeyHasher;
    private final AuthPrincipalLookup authPrincipalLookup;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiKeyAuthenticationFilter(
        ApiKeyHasher apiKeyHasher,
        AuthPrincipalLookup authPrincipalLookup,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.apiKeyHasher = apiKeyHasher;
        this.authPrincipalLookup = authPrincipalLookup;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthPrincipal principal = authPrincipalLookup.findByApiKeyHash(apiKeyHasher.hash(apiKey))
            .orElse(null);
        if (principal == null || principal.principalType() != com.adp.gateway.auth.domain.PrincipalType.SERVICE) {
            writeUnauthorized(response, request);
            return;
        }

        AuthenticatedPrincipal authentication = new AuthenticatedPrincipal(
            principal,
            principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ErrorResponse errorResponse = new ErrorResponse(
            ReasonCode.AUTHENTICATION_FAILED.name(),
            "Authentication required",
            attribute(request, TraceHeaders.REQUEST_ID_ATTRIBUTE),
            attribute(request, TraceHeaders.TRACE_ID_ATTRIBUTE),
            OffsetDateTime.now(clock)
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
