package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class TeacherUpdateRequest {
    private String fioTeacherDative;
    private String initials;
    private String initialsDative;
    private String phone;
    private String email;
}
