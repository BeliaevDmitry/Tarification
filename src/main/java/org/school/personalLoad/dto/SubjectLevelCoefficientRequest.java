package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.EducationStage;

import java.math.BigDecimal;

@Data
public class SubjectLevelCoefficientRequest {
    private String subjectName;
    private EducationStage educationStage;
    private BigDecimal coefficient;
}
