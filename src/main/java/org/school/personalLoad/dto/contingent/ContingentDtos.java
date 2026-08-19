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
        private String importFormat;
        private Integer skippedRows;
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
        private String importFormat;
        private int importedStudents;
        private int schoolStudents;
        private int kindergartenStudents;
        private int unassignedStudents;
        private int skippedRows;
        private int linkedStudents;
        private int createdStudentProfiles;
        private int ambiguousStudents;
        private int mismatchCount;
        private List<ImportProblem> problems;
    }

    @Data
    public static class ImportMismatchResponse {
        private Long snapshotId;
        private LocalDate snapshotDate;
        private String sourceFileName;
        private String importFormat;
        private int total;
        private int outsideOrganization;
        private int ambiguousIdentity;
        private int skippedRows;
        private int unknownClasses;
        private List<ImportMismatchRow> rows;
        private List<StudentOption> studentOptions;
        private List<String> placementOptions;
    }

    @Data
    public static class ImportMismatchRow {
        private String key;
        private String type;
        private Long contingentStudentId;
        private Integer sourceRowNumber;
        private Long currentStudentId;
        private String fullName;
        private LocalDate birthDate;
        private String currentPlacement;
        private String message;
        private String rawPayload;
        private boolean canResolve;
        private boolean requiresStudent;
        private boolean requiresPlacement;
    }

    @Data
    public static class StudentOption {
        private Long id;
        private String fullName;
        private LocalDate birthDate;
        private String currentPlacement;
    }

    @Data
    public static class ResolveImportMismatchRequest {
        private Long contingentStudentId;
        private Long studentId;
        private String className;
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
    public static class ClassStudentView {
        private Long studentId;
        private String fullName;
        private LocalDate birthDate;
        private String className;
        private String recordNumber;
    }

    @Data
    public static class KindergartenGroupTotal {
        private String groupName;
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
        private Integer totalImportedChildren;
        private Integer totalSchoolChildren;
        private Integer totalKindergartenChildren;
        private Integer totalUnassignedChildren;
        private Integer totalStudents;
        private Integer totalClassesNoo;
        private Integer totalClassesOoo;
        private Integer totalClassesSoo;
        private List<Integer> parallels;
        private List<BuildingColumn> columns;
        private List<ParallelTotal> parallelTotals;
        private List<KindergartenGroupTotal> kindergartenGroups;
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
