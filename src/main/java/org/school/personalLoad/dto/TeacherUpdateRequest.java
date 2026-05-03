package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class TeacherUpdateRequest {
    private String fioTeacher;
    private String fioTeacherDative;
    private String initials;
    private String phone;
    private String email;
    private String additionalDuties;
}
