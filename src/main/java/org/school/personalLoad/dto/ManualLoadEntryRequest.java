package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class ManualLoadEntryRequest {
    private String fioTeacher;
    private String numberSchoolBuilding;
    private String subjectName;
    private String className;
    private Integer load;
    private String groupNameEducationalPlan;
    private Integer groupLoad;
}
