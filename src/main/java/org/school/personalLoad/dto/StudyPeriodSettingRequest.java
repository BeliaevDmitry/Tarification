package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.StudyPeriodSettingKey;

import java.time.LocalDate;

@Data
public class StudyPeriodSettingRequest {
    private StudyPeriodSettingKey settingKey;
    private LocalDate startDate;
    private LocalDate endDate;
}
