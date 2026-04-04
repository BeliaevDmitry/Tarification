package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.StudyPeriod;

import java.time.LocalDate;

@Data
public class StudyPeriodSettingRequest {
    private String settingKey;
    private String displayName;
    private StudyPeriod studyPeriod;
    private Integer parallelFrom;
    private Integer parallelTo;
    private LocalDate startDate;
    private LocalDate endDate;
}
