package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.EducationLevel;

@Data
public class ManualLoadEntryRequest {
    private String fioTeacher;
    private String numberSchoolBuilding;
    private String subjectName;
    private String className;
    private Integer load;
    private String groupNameEducationalPlan;
    private Integer groupLoad;
    private EducationLevel educationLevel;
}
