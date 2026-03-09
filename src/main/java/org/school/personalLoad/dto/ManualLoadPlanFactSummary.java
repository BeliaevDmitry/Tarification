package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.school.personalLoad.model.EducationLevel;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ManualLoadPlanFactSummary {
    private String className;
    private String subjectName;
    private EducationLevel educationLevel;
    private BigDecimal plannedHours;
    private BigDecimal actualHours;
    private BigDecimal remainingHours;
}
