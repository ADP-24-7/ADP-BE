package com.adp.gateway.common.trace;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.adp.gateway.common.error.ErrorResponse;
import com.adp.gateway.common.error.ReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestFreshnessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final Duration replayWindow;
    private final Duration futureSkew;

    public RequestFreshnessFilter(
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${adp.security.request-freshness.enabled:true}") boolean enabled,
        @Value("${adp.security.request-freshness.replay-window:5m}") Duration replayWindow,
        @Value("${adp.security.request-freshness.future-skew:30s}") Duration futureSkew
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
        this.replayWindow = replayWindow;
        this.futureSkew = futureSkew;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
            || !HttpMethod.POST.matches(request.getMethod())
            || !"/v1/runtime/executions".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String rawTimestamp = request.getHeader(TraceHeaders.REQUEST_TIMESTAMP);
        Instant now = clock.instant();
        Instant timestamp;
        try {
            timestamp = rawTimestamp == null || rawTimestamp.isBlank() ? null : Instant.parse(rawTimestamp);
        } catch (DateTimeParseException exception) {
            timestamp = null;
        }
        if (timestamp == null
            || timestamp.isBefore(now.minus(replayWindow))
            || timestamp.isAfter(now.plus(futureSkew))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                ReasonCode.REQUEST_FRESHNESS_INVALID.name(),
                "Request timestamp is outside the accepted replay window",
                attribute(request, TraceHeaders.REQUEST_ID_ATTRIBUTE),
                attribute(request, TraceHeaders.TRACE_ID_ATTRIBUTE),
                java.time.OffsetDateTime.now(clock)
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
