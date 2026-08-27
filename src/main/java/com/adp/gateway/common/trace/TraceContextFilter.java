package com.adp.gateway.common.trace;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = valueOrNew(request.getHeader(TraceHeaders.REQUEST_ID));
        String traceId = valueOrNew(request.getHeader(TraceHeaders.TRACE_ID));

        response.setHeader(TraceHeaders.REQUEST_ID, requestId);
        response.setHeader(TraceHeaders.TRACE_ID, traceId);
        request.setAttribute(TraceHeaders.REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(TraceHeaders.TRACE_ID_ATTRIBUTE, traceId);

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
}
