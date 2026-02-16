package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.school.personalLoad.model.EducationLevel;

@Data
@AllArgsConstructor
public class ManualLoadPlanFactSummary {
    private String className;
    private String subjectName;
    private EducationLevel educationLevel;
    private int plannedHours;
    private int actualHours;
    private int remainingHours;
}
