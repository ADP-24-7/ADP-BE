package com.adp.gateway.common.trace;

import java.util.UUID;
import java.util.regex.Pattern;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.error.InvalidRuntimeHeaderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RuntimeContextFactory {

    private static final Pattern SAFE_IDEMPOTENCY_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");

    public RuntimeRequestContext create(
        HttpServletRequest httpRequest,
        String workloadId,
        String purpose,
        String subject
    ) {
        return create(httpRequest, workloadId, purpose, subject, httpRequest.getHeader(TraceHeaders.IDEMPOTENCY_KEY));
    }

    public RuntimeRequestContext create(
        HttpServletRequest httpRequest,
        String workloadId,
        String purpose,
        String subject,
        String idempotencyKeyValue
    ) {
        String idempotencyKey = valueOrNew(idempotencyKeyValue);
        if (!SAFE_IDEMPOTENCY_VALUE.matcher(idempotencyKey).matches()) {
            throw new InvalidRuntimeHeaderException(TraceHeaders.IDEMPOTENCY_KEY);
        }

        return new RuntimeRequestContext(
            valueOrNew(attribute(httpRequest, TraceHeaders.REQUEST_ID_ATTRIBUTE)),
            valueOrNew(attribute(httpRequest, TraceHeaders.TRACE_ID_ATTRIBUTE)),
            idempotencyKey,
            workloadId,
            purpose,
            subject
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
