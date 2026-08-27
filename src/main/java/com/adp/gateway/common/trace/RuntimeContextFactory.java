package com.adp.gateway.common.trace;

import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.operations.api.MockRuntimeRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RuntimeContextFactory {

    public RuntimeRequestContext create(HttpServletRequest httpRequest, MockRuntimeRequest request) {
        return new RuntimeRequestContext(
            valueOrNew(attribute(httpRequest, TraceHeaders.REQUEST_ID_ATTRIBUTE)),
            valueOrNew(attribute(httpRequest, TraceHeaders.TRACE_ID_ATTRIBUTE)),
            valueOrNew(httpRequest.getHeader(TraceHeaders.IDEMPOTENCY_KEY)),
            request.workloadId(),
            request.purpose(),
            request.subject()
        );
    }

    private String valueOrNew(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
