package org.school.personalLoad.masterfot;

import org.school.personalLoad.controller.api.ApiExceptionHandler;
import org.school.personalLoad.dto.ApiErrorResponse;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/** Reuse the application's readable API errors for this feature's separate package. */
@RestControllerAdvice(assignableTypes = FotController.class)
public class FotExceptionHandler extends ApiExceptionHandler {
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiErrorResponse> concurrentUpdate(ConcurrencyFailureException error, HttpServletRequest request) {
        return ResponseEntity.status(409).body(new ApiErrorResponse("error",
                "Данные изменились во время сверки. Обновите список и повторите действие.", request.getRequestURI(), LocalDateTime.now()));
    }
}
