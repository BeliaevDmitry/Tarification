package org.school.personalLoad.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TeacherDismissRequest {
    private LocalDate dismissalDate;
}
