package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class ClassroomLeadershipEntryRequest {
    private Long id;
    private String academicYear;
    private String numberSchoolBuilding;
    private Long schoolBuildingId;
    private String className;
    private String classDirection;
    private Long teacherId;
    private String fioTeacher;
    private String campusAddress;
    private String classType;
}
