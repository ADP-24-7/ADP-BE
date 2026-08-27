package com.adp.gateway.common.error;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.common.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidationError(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
            ReasonCode.VALIDATION_ERROR.name(),
            "Request validation failed",
            attribute(request, TraceHeaders.REQUEST_ID_ATTRIBUTE),
            attribute(request, TraceHeaders.TRACE_ID_ATTRIBUTE),
            OffsetDateTime.now(clock)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
