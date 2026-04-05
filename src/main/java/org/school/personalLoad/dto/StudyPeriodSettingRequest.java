package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.StudyPeriod;

import java.time.LocalDate;

@Data
public class StudyPeriodSettingRequest {
    private Long id;
    private String code;
    private String displayName;
    private StudyPeriod studyPeriod;
    private Integer parallelFrom;
    private Integer parallelTo;
    private Boolean defaultRule;
    private LocalDate startDate;
    private LocalDate endDate;
}
