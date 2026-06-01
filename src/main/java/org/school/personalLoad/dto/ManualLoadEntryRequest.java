package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.ContinuityStatus;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.StudyPeriod;

import java.time.LocalDate;

@Data
public class ManualLoadEntryRequest {
    private String academicYear;
    private String fioTeacher;
    private String numberSchoolBuilding;
    private String subjectName;
    private String className;
    private Long classId;
    private Integer load;
    private String groupNameEducationalPlan;
    private Integer groupLoad;
    private EducationLevel educationLevel;
    private StudyPeriod studyPeriod;
    private LocalDate loadFromDate;
    private LocalDate loadToDate;
    private ContinuityStatus continuityStatus;
}
