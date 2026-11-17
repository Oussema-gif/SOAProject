package com.soaproject.maintenance.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex,
                                                 HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", req, details);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> onDuplicate(DuplicateResourceException ex,
                                                HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(ResourceNotFoundException ex,
                                               HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req, null);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> onInvalidRequest(InvalidRequestException ex,
                                                      HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), req, null);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiError> onInvalidState(InvalidStateTransitionException ex,
                                                   HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", ex.getMessage(), req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> onBadCredentials(BadCredentialsException ex,
                                                     HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Invalid username or password", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> onAuthentication(AuthenticationException ex,
                                                     HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException ex,
                                                   HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient permission", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onAny(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected server error", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req, List<String> details) {
        ApiError err = new ApiError(Instant.now(), status.value(), code,
                message, req.getRequestURI(), details);
        return ResponseEntity.status(status).body(err);
    }
}