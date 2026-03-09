package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.StudyPeriod;

@Data
@AllArgsConstructor
public class CurriculumImportRow {
    private String academicYear;
    private CurriculumStage stage;
    private String className;
    private String classDirection;
    private String subjectName;
    private Integer plannedHours;
    private StudyPeriod studyPeriod;
}
