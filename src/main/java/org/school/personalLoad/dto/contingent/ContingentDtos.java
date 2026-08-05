package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.ClassSizeSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ContingentDtos {

    private ContingentDtos() {
    }

    @Data
    public static class SnapshotListItem {
        private Long id;
        private LocalDate snapshotDate;
        private LocalDateTime importedAt;
        private String sourceFileName;
        private Integer totalStudents;
    }

    @Data
    public static class ImportProblem {
        private String className;
        private String description;
        private Integer studentsCount;
    }

    @Data
    public static class ImportResponse {
        private Long snapshotId;
        private LocalDate snapshotDate;
        private int importedStudents;
        private int skippedRows;
        private int linkedStudents;
        private int createdStudentProfiles;
        private int ambiguousStudents;
        private List<ImportProblem> problems;
    }

    @Data
    public static class ParallelTotal {
        private Integer parallel;
        private Integer totalStudents;
        private Integer totalClasses;
    }

    @Data
    public static class ClassTotal {
        private Integer parallel;
        private String className;
        private Integer students;
    }

    @Data
    public static class AddressColumn {
        private String address;
        private List<ClassTotal> classes;
        private Integer totalStudents;
    }

    @Data
    public static class BuildingColumn {
        private String buildingCode;
        private String buildingName;
        private List<AddressColumn> addresses;
        private Integer totalStudents;
    }

    @Data
    public static class StatsResponse {
        private Long snapshotId;
        private LocalDate snapshotDate;
        private Integer totalStudents;
        private Integer totalClassesNoo;
        private Integer totalClassesOoo;
        private Integer totalClassesSoo;
        private List<Integer> parallels;
        private List<BuildingColumn> columns;
        private List<ParallelTotal> parallelTotals;
    }

    @Data
    public static class ManualClassSizeRow {
        private String className;
        private Integer aisStudents;
        private Integer manualStudents;
        private Boolean matches;
    }

    @Data
    public static class ManualClassSizeResponse {
        private ClassSizeSource source;
        private List<ManualClassSizeRow> rows;
    }

    @Data
    public static class ManualClassSizeUpdate {
        private String className;
        private Integer manualStudents;
    }

    @Data
    public static class ManualClassSizeSaveRequest {
        private List<ManualClassSizeUpdate> rows;
    }

    @Data
    public static class ClassSizeSourceRequest {
        private ClassSizeSource source;
    }
}
