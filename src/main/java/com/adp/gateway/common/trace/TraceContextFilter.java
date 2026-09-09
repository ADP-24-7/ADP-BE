package com.adp.gateway.common.trace;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import com.adp.gateway.common.error.ErrorResponse;
import com.adp.gateway.common.error.ReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.adp.gateway.context.application.CanonicalValueHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_TRACE_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{1,80}$");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CanonicalValueHasher hasher;
    private final boolean acceptCallerTraceId;

    public TraceContextFilter(
        ObjectMapper objectMapper,
        Clock clock,
        CanonicalValueHasher hasher,
        @Value("${adp.security.trace.accept-caller-trace-id:false}") boolean acceptCallerTraceId
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.hasher = hasher;
        this.acceptCallerTraceId = acceptCallerTraceId;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = valueOrNew(request.getHeader(TraceHeaders.REQUEST_ID));
        String clientTraceId = request.getHeader(TraceHeaders.TRACE_ID);
        String traceId = acceptCallerTraceId ? valueOrNew(clientTraceId) : UUID.randomUUID().toString();

        if (!isSafeTraceValue(requestId) || !isOptionalSafeTraceValue(clientTraceId)) {
            writeMalformedHeaderResponse(response, requestId, traceId);
            return;
        }

        response.setHeader(TraceHeaders.REQUEST_ID, requestId);
        response.setHeader(TraceHeaders.TRACE_ID, traceId);
        request.setAttribute(TraceHeaders.REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(TraceHeaders.TRACE_ID_ATTRIBUTE, traceId);
        if (clientTraceId != null && !clientTraceId.isBlank()) {
            request.setAttribute(TraceHeaders.CLIENT_TRACE_ID_DIGEST_ATTRIBUTE, hasher.hash(clientTraceId));
        }

        MDC.put("request_id", requestId);
        MDC.put("trace_id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
            MDC.remove("trace_id");
        }
    }

    private String valueOrNew(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private boolean isSafeTraceValue(String value) {
        return SAFE_TRACE_VALUE.matcher(value).matches();
    }

    private boolean isOptionalSafeTraceValue(String value) {
        return value == null || value.isBlank() || isSafeTraceValue(value);
    }

    private void writeMalformedHeaderResponse(
        HttpServletResponse response,
        String requestId,
        String traceId
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");

        ErrorResponse errorResponse = new ErrorResponse(
            ReasonCode.MALFORMED_REQUEST.name(),
            "Malformed request",
            isSafeTraceValue(requestId) ? requestId : null,
            isSafeTraceValue(traceId) ? traceId : null,
            OffsetDateTime.now(clock)
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
