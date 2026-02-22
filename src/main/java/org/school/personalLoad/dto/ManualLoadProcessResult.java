package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ManualLoadProcessResult {
    private String status;
    private int processed;
    private List<ManualLoadPlanFactSummary> summaries;
}

