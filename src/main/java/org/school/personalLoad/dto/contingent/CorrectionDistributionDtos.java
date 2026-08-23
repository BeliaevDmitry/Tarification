package org.school.personalLoad.dto.contingent;

import lombok.Data;

import java.time.LocalTime;
import java.util.List;

public final class CorrectionDistributionDtos {
    private CorrectionDistributionDtos() {}

    @Data
    public static class Overview {
        private List<DirectionSummary> directions;
        private List<StaffSummary> staff;
    }

    @Data
    public static class DirectionSummary {
        private Long specialistId;
        private String specialistName;
        private int neededCount;
        private int assignedCount;
        private int unassignedCount;
    }

    @Data
    public static class StaffSummary {
        private Long staffId;
        private Long specialistId;
        private String specialistName;
        private Long employeeId;
        private String employeeName;
        private String position;
        private String personnelNumber;
        private boolean active;
        private long assignedCount;
        private long groupCount;
    }

    @Data
    public static class Directory {
        private List<SpecialistOption> specialists;
        private List<EmployeeOption> employees;
        private List<StaffSummary> staff;
    }

    @Data
    public static class SpecialistOption {
        private Long id;
        private String name;
    }

    @Data
    public static class EmployeeOption {
        private Long id;
        private String fullName;
        private String position;
        private String personnelNumber;
    }

    @Data
    public static class StaffSaveRequest {
        private Long id;
        private Long specialistId;
        private Long employeeId;
        private boolean active = true;
    }

    @Data
    public static class Schedule {
        private StaffSummary selectedStaff;
        private List<GroupView> groups;
        private List<StudentNeedView> availableStudents;
    }

    @Data
    public static class GroupSaveRequest {
        private Long id;
        private Long staffId;
        private Integer weekday;
        private LocalTime startTime;
        private Integer durationMinutes;
        private List<Long> studentIds;
    }

    @Data
    public static class GroupView {
        private Long id;
        private Long staffId;
        private Long specialistId;
        private String specialistName;
        private String employeeName;
        private int sequenceNumber;
        private String displayName;
        private String fullName;
        private int weekday;
        private LocalTime startTime;
        private int durationMinutes;
        private List<StudentNeedView> students;
    }

    @Data
    public static class StudentNeedView {
        private Long studentId;
        private String fullName;
        private String className;
        private Long specialistId;
        private String specialistName;
        private boolean distributionAvailable;
        private boolean assigned;
        private Long staffId;
        private Long employeeId;
        private String employeeName;
        private Long groupId;
        private String groupName;
    }

    @Data
    public static class StudentDistribution {
        private Long studentId;
        private String fullName;
        private String className;
        private boolean ppkSigned;
        private int neededCount;
        private int assignedCount;
        private List<StudentNeedView> directions;
    }
}
