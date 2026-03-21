package org.school.personalLoad.controller.api;

import org.school.personalLoad.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "org.school.personalLoad.controller.api")
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse("error", e.getMessage(), request.getRequestURI(), LocalDateTime.now())
        );
    }


    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiErrorResponse("error", e.getMessage(), request.getRequestURI(), LocalDateTime.now())
        );
    }


    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(BadCredentialsException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiErrorResponse("error", "Invalid username or password", request.getRequestURI(), LocalDateTime.now())
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ApiErrorResponse("error", "Access denied", request.getRequestURI(), LocalDateTime.now())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiErrorResponse("error", "Internal server error", request.getRequestURI(), LocalDateTime.now())
        );
    }
}
