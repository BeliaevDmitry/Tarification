package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.StudyPeriod;

@Data
@AllArgsConstructor
public class CurriculumImportRow {
    private String academicYear;
    private CurriculumStage stage;
    private String className;
    private String classDirection;
    private String subjectAreaName;
    private String subjectName;
    private java.math.BigDecimal plannedHours;
    private StudyPeriod studyPeriod;
    private CurriculumPart curriculumPart;
    private boolean subgroupRequired;
    private boolean metaGroup;

    public CurriculumImportRow(String academicYear,
                               CurriculumStage stage,
                               String className,
                               String classDirection,
                               String subjectAreaName,
                               String subjectName,
                               java.math.BigDecimal plannedHours,
                               StudyPeriod studyPeriod,
                               CurriculumPart curriculumPart) {
        this(academicYear, stage, className, classDirection, subjectAreaName, subjectName, plannedHours, studyPeriod, curriculumPart, false, false);
    }
}
