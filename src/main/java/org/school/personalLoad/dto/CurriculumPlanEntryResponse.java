package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumModule;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CurriculumPlanEntryResponse {
    private Long id;
    private String numberSchoolBuilding;
    private String academicYear;
    private CurriculumStage stage;
    private StudyPeriod studyPeriod;
    private Long studyPeriodSettingId;
    private boolean deprecated;
    private boolean excludedFromManualLoad;
    private String className;
    private Long classId;
    private Long metaGroupId;
    private Long schoolBuildingId;
    private Long subjectId;
    private String subjectName;
    private BigDecimal plannedHours;
    private boolean subgroupRequired;
    private Integer subgroupCount;
    private EducationLevel educationLevel;
    private Integer subgroup1Hours;
    private EducationLevel subgroup1EducationLevel;
    private Integer subgroup2Hours;
    private EducationLevel subgroup2EducationLevel;
    private CurriculumPart curriculumPart;
    private LocalDateTime createdAt;
    private boolean metaGroup;
    private boolean modularSystem;
    private List<CurriculumModule> modules;

    public static CurriculumPlanEntryResponse from(CurriculumPlanEntry entry, Long schoolBuildingId) {
        CurriculumPlanEntryResponse response = new CurriculumPlanEntryResponse();
        response.setId(entry.getId());
        response.setNumberSchoolBuilding(entry.getNumberSchoolBuilding());
        response.setAcademicYear(entry.getAcademicYear());
        response.setStage(entry.getStage());
        response.setStudyPeriod(entry.getStudyPeriod());
        response.setStudyPeriodSettingId(entry.getStudyPeriodSettingId());
        response.setDeprecated(entry.isDeprecated());
        response.setExcludedFromManualLoad(entry.isExcludedFromManualLoad());
        response.setClassName(entry.getClassName());
        response.setClassId(entry.getClassId());
        response.setMetaGroupId(entry.getMetaGroupId());
        response.setSchoolBuildingId(schoolBuildingId);
        response.setSubjectId(entry.getSubjectId());
        response.setSubjectName(entry.getSubjectName());
        response.setPlannedHours(entry.getPlannedHours());
        response.setSubgroupRequired(entry.isSubgroupRequired());
        response.setSubgroupCount(entry.getSubgroupCount());
        response.setEducationLevel(entry.getEducationLevel());
        response.setSubgroup1Hours(entry.getSubgroup1Hours());
        response.setSubgroup1EducationLevel(entry.getSubgroup1EducationLevel());
        response.setSubgroup2Hours(entry.getSubgroup2Hours());
        response.setSubgroup2EducationLevel(entry.getSubgroup2EducationLevel());
        response.setCurriculumPart(entry.getCurriculumPart());
        response.setCreatedAt(entry.getCreatedAt());
        response.setMetaGroup(entry.isMetaGroup());
        response.setModularSystem(entry.isModularSystem());
        response.setModules(entry.getModules());
        return response;
    }
}
