package org.school.personalLoad.dto.contingent;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

public final class StudentDataExchangeDtos {

    private StudentDataExchangeDtos() {
    }

    @Data
    public static class SheetImportResult {
        private String sheetName;
        private int imported;
        private int deleted;
        private int skipped;
    }

    @Data
    public static class ImportError {
        private String sheetName;
        private int rowNumber;
        private String message;
    }

    @Data
    public static class ImportResult {
        private List<SheetImportResult> sheets;
        private List<ImportError> errors;
        private int imported;
        private int deleted;
        private int skipped;
    }

    @Data
    public static class ReadinessResponse {
        private String calculationMode;
        private Long snapshotId;
        private LocalDate snapshotDate;
        private int totalStudents;
        private int linkedStudents;
        private int unlinkedStudents;
        private int nosologies;
        private int activeIups;
        private int expectedGroupAssignments;
        private int completedGroupAssignments;
        private int missingGroupAssignments;
        private int duplicateGroupAssignments;
        private int explicitMeshNameMappings;
        private boolean readyForStudentCountCutover;
        private List<String> blockers;
        private List<String> notes;
        private List<GroupProjectionRow> groupProjection;
    }

    @Data
    public static class GroupProjectionRow {
        private Long curriculumEntryId;
        private String classOrMetaGroup;
        private String subjectName;
        private String groupName;
        private int students;
    }
}
