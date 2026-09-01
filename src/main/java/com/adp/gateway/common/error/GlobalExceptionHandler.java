package com.adp.gateway.common.error;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.common.trace.TraceHeaders;
import com.adp.gateway.dataaccess.application.DataAccessDeniedException;
import com.adp.gateway.egress.application.DestinationProfileNotFoundException;
import com.adp.gateway.egress.application.OutboundGuardException;
import com.adp.gateway.runtime.application.DuplicateRuntimeExecutionException;
import com.adp.gateway.runtime.application.RuntimeExecutionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidationError(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ReasonCode.VALIDATION_ERROR,
            "Request validation failed",
            HttpStatus.BAD_REQUEST,
            request
        );
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        InvalidRuntimeHeaderException.class
    })
    ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return errorResponse(
            ReasonCode.MALFORMED_REQUEST,
            "Malformed request",
            HttpStatus.BAD_REQUEST,
            request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedMethod(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ReasonCode.MALFORMED_REQUEST,
            "Malformed request",
            HttpStatus.METHOD_NOT_ALLOWED,
            request
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException exception, HttpServletRequest request) {
        return errorResponse(
            ReasonCode.MALFORMED_REQUEST,
            "Malformed request",
            HttpStatus.NOT_FOUND,
            request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return errorResponse(
            ReasonCode.AUTHORIZATION_DENIED,
            "Authorization denied",
            HttpStatus.FORBIDDEN,
            request
        );
    }

    @ExceptionHandler(DataAccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleDataAccessDenied(
        DataAccessDeniedException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ReasonCode.DATA_ACCESS_DENIED,
            "Data access denied",
            HttpStatus.FORBIDDEN,
            request
        );
    }

    @ExceptionHandler(DuplicateRuntimeExecutionException.class)
    ResponseEntity<ErrorResponse> handleDuplicateRuntimeExecution(
        DuplicateRuntimeExecutionException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ReasonCode.IDEMPOTENCY_KEY_REUSED,
            "Idempotency key already used for workload",
            HttpStatus.CONFLICT,
            request
        );
    }

    @ExceptionHandler(RuntimeExecutionNotFoundException.class)
    ResponseEntity<ErrorResponse> handleRuntimeExecutionNotFound(
        RuntimeExecutionNotFoundException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ReasonCode.RUNTIME_EXECUTION_NOT_FOUND,
            "Runtime execution not found",
            HttpStatus.NOT_FOUND,
            request
        );
    }

    @ExceptionHandler(DestinationProfileNotFoundException.class)
    ResponseEntity<ErrorResponse> handleDestinationProfileNotFound(
        DestinationProfileNotFoundException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ReasonCode.DESTINATION_PROFILE_NOT_FOUND,
            "Destination profile not found",
            HttpStatus.UNPROCESSABLE_ENTITY,
            request
        );
    }

    @ExceptionHandler(OutboundGuardException.class)
    ResponseEntity<ErrorResponse> handleOutboundGuardRejected(
        OutboundGuardException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ReasonCode.OUTBOUND_GUARD_REJECTED,
            "Outbound guard rejected payload",
            HttpStatus.UNPROCESSABLE_ENTITY,
            request
        );
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> handleDataAccessError(DataAccessException exception, HttpServletRequest request) {
        log.error("Database operation failed", exception);

        return errorResponse(
            ReasonCode.INTERNAL_ERROR,
            "Internal server error",
            HttpStatus.INTERNAL_SERVER_ERROR,
            request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpectedError(Exception exception, HttpServletRequest request) {
        log.error("Unhandled runtime exception", exception);

        return errorResponse(
            ReasonCode.INTERNAL_ERROR,
            "Internal server error",
            HttpStatus.INTERNAL_SERVER_ERROR,
            request
        );
    }

    private ResponseEntity<ErrorResponse> errorResponse(
        ReasonCode reasonCode,
        String message,
        HttpStatus status,
        HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
            reasonCode.name(),
            message,
            attribute(request, TraceHeaders.REQUEST_ID_ATTRIBUTE),
            attribute(request, TraceHeaders.TRACE_ID_ATTRIBUTE),
            OffsetDateTime.now(clock)
        );

        return ResponseEntity.status(status).body(response);
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
