package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class TeacherCreateRequest {
    private String fioTeacher;
    private String fioTeacherDative;
    private String initials;
    private String initialsDative;
    private String phone;
    private String email;
    private String additionalDuties;
    private String numberSchoolBuilding;
    private String primaryPosition;
    private String employmentType;
    private java.time.LocalDate employmentDate;
}
