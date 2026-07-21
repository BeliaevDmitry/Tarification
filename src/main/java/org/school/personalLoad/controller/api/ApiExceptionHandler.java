package org.school.personalLoad.controller.api;

import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthExceptions.UnauthorizedException;
import org.school.personalLoad.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "org.school.personalLoad.controller.api")
@Slf4j
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        String message = e.getReason() == null || e.getReason().isBlank() ? e.getMessage() : e.getReason();
        return ResponseEntity.status(e.getStatus()).body(
                new ApiErrorResponse("error", message, request.getRequestURI(), LocalDateTime.now())
        );
    }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiErrorResponse("error", e.getMessage(), request.getRequestURI(), LocalDateTime.now())
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ApiErrorResponse("error", e.getMessage(), request.getRequestURI(), LocalDateTime.now())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled API exception on {}: {}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiErrorResponse("error", "Internal server error", request.getRequestURI(), LocalDateTime.now())
        );
    }
}
