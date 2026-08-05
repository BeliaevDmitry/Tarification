package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class TeacherUpdateRequest {
    private String fioTeacher;
    private String fioTeacherGenitive;
    private String fioTeacherDative;
    private String fioTeacherAccusative;
    private String fioTeacherInstrumental;
    private String fioTeacherPrepositional;
    private String initials;
    private String initialsGenitive;
    private String initialsDative;
    private String initialsAccusative;
    private String initialsInstrumental;
    private String initialsPrepositional;
    private String phone;
    private String email;
    private String additionalDuties;
    private String numberSchoolBuilding;
    private String primaryPosition;
    private String employmentType;
    private java.time.LocalDate employmentDate;
}
