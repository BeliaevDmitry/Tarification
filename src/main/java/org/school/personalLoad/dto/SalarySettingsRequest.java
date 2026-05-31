package org.school.personalLoad.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalarySettingsRequest {
    private BigDecimal studentHourRate;
}
