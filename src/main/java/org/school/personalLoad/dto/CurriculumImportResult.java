package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CurriculumImportResult {
    private int created;
    private int updated;
    private int deprecated;
    private int classesCreated;
    private int orphanedLoads;
    private int subjectsImported;
    private List<SumMismatch> sumMismatches;

    @Data
    @AllArgsConstructor
    public static class SumMismatch {
        private String classKey;
        private String sumLabel;
        private String expected;
        private String actual;
    }
}
