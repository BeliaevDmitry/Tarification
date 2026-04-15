package org.school.personalLoad.dto.contingent;

import lombok.Data;

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
        private List<ImportProblem> problems;
    }

    @Data
    public static class ParallelTotal {
        private Integer parallel;
        private Integer totalStudents;
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
        private List<Integer> parallels;
        private List<BuildingColumn> columns;
        private List<ParallelTotal> parallelTotals;
    }
}
