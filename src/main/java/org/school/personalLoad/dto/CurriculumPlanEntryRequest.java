package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.EducationLevel;

@Data
public class CurriculumPlanEntryRequest {
    private String className;
    private String subjectName;
    private Integer plannedHours;
    private boolean subgroupRequired;
    private Integer subgroupCount;
    private EducationLevel educationLevel;
}
