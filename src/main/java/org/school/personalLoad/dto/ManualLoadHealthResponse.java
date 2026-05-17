package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ManualLoadHealthResponse {
    private int unassignedHours;
    private int errorCount;
}

