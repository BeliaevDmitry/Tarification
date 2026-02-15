package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ApiErrorResponse {
    private String status;
    private String message;
    private LocalDateTime timestamp;
}
