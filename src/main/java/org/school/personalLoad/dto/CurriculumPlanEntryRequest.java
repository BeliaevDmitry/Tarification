package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;

import java.util.ArrayList;
import java.util.List;

@Data
public class CurriculumPlanEntryRequest {
    private String academicYear;
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
    private boolean excludedFromManualLoad;
    private boolean modularSystem;
    private List<ModuleRequest> modules = new ArrayList<>();

    @Data
    public static class ModuleRequest {
        private Long id;
        private Integer moduleOrder;
        private String moduleName;
        private java.math.BigDecimal plannedHours;
        private boolean subgroupRequired;
        private EducationLevel educationLevel;
        private Integer subgroup1Hours;
        private EducationLevel subgroup1EducationLevel;
        private Integer subgroup2Hours;
        private EducationLevel subgroup2EducationLevel;
    }
}
