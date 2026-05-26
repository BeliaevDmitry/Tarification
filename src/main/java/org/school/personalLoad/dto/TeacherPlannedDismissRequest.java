package org.school.personalLoad.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TeacherPlannedDismissRequest {
    private LocalDate plannedDismissalDate;
    private String comment;
}
