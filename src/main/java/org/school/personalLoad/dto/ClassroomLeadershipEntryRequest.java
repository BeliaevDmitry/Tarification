package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class ClassroomLeadershipEntryRequest {
    private Long id;
    private String academicYear;
    private String numberSchoolBuilding;
    private String className;
    private String classDirection;
    private String fioTeacher;
    private String campusAddress;
    private String classType;
}
