package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.StudyPeriod;

import java.time.LocalDate;

@Data
public class StudyPeriodSettingRequest {
    private StudyPeriod studyPeriod;
    private LocalDate startDate;
    private LocalDate endDate;
}
