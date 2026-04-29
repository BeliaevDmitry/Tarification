package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ManualLoadStatsResponse {
    private int subjects;
    private int totalPlanned;
    private int totalAssigned;
    private int totalUnassigned;
    private List<SubjectStat> rows;

    @Data
    @AllArgsConstructor
    public static class SubjectStat {
        private String subjectArea;
        private String subjectName;
        private int planned;
        private int assigned;
        private int unassigned;
    }
}
