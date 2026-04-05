package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;

@Data
public class CurriculumPlanEntryRequest {
    private String numberSchoolBuilding;
    private String className;
    private String subjectName;
    private java.math.BigDecimal plannedHours;
    private boolean subgroupRequired;
    private Integer subgroupCount;
    private EducationLevel educationLevel;
    private Integer subgroup1Hours;
    private EducationLevel subgroup1EducationLevel;
    private Integer subgroup2Hours;
    private EducationLevel subgroup2EducationLevel;
    private CurriculumPart curriculumPart;
    private StudyPeriod studyPeriod;
    private Long studyPeriodSettingId;
    private boolean metaGroup;
}
