package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.IupDeliveryForm;
import org.school.personalLoad.model.IupParticipationMode;
import org.school.personalLoad.model.IupStatus;
import org.school.personalLoad.model.StudentCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StudentSupportDtos {

    private StudentSupportDtos() {
    }

    @Data
    public static class SummaryResponse {
        private Long snapshotId;
        private LocalDate snapshotDate;
        private LocalDate asOfDate;
        private Integer totalStudents;
        private Integer unlinkedStudents;
        private List<ClassSummary> classes;
        private List<RegisterRow> registerRows;
        private List<String> warnings;
    }

    @Data
    public static class ClassSummary {
        private String className;
        private Integer total;
        private Integer normal;
        private Integer k2;
        private Integer k3;
        private Integer iup;
    }

    @Data
    public static class RegisterRow {
        private Long studentId;
        private String fullName;
        private String className;
        private LocalDate birthDate;
        private StudentCategory underlyingCategory;
        private Long supportStatusId;
        private Long nosologyId;
        private String nosologyCode;
        private String nosologyName;
        private String aoopVariant;
        private LocalDate categoryValidFrom;
        private LocalDate categoryValidTo;
        private Boolean hasIup;
        private Long iupPlanId;
        private IupStatus iupStatus;
        private String orderNumber;
        private LocalDate orderDate;
        private LocalDate iupValidFrom;
        private LocalDate iupValidTo;
    }

    @Data
    public static class StudentOption {
        private Long studentId;
        private String fullName;
        private String className;
        private LocalDate birthDate;
        private String recordNumber;
    }

    @Data
    public static class CurriculumOption {
        private Long curriculumEntryId;
        private String className;
        private String subjectName;
        private boolean subgroupRequired;
        private Integer subgroupCount;
    }

    @Data
    public static class TeacherOption {
        private Long teacherId;
        private String fullName;
        private boolean archived;
    }

    @Data
    public static class ReferenceDataResponse {
        private List<StudentOption> students;
        private List<CurriculumOption> curriculum;
        private List<TeacherOption> teachers;
        private int totalContingentStudents;
        private int unlinkedStudents;
    }

    @Data
    public static class StatusSaveRequest {
        private Long id;
        private Long studentId;
        private StudentCategory category;
        private Long nosologyId;
        private String nosologyCode;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String comment;
    }

    @Data
    public static class IupSaveRequest {
        private Long id;
        private Long studentId;
        private IupStatus status;
        private String orderNumber;
        private LocalDate orderDate;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String comment;
        private List<SubjectLineRequest> subjects;
    }

    @Data
    public static class SubjectLineRequest {
        private String subjectName;
        private Long curriculumEntryId;
        private IupParticipationMode participationMode;
        private BigDecimal classHours;
        private BigDecimal individualHours;
        private String groupNameEducationalPlan;
        private List<TeacherAssignmentRequest> teachers;
    }

    @Data
    public static class TeacherAssignmentRequest {
        private Long teacherId;
        private BigDecimal hoursPerWeek;
        private IupDeliveryForm deliveryForm;
        private LocalDate validFrom;
        private LocalDate validTo;
    }

    @Data
    public static class IupPlanView {
        private Long id;
        private Long studentId;
        private IupStatus status;
        private String orderNumber;
        private LocalDate orderDate;
        private LocalDate validFrom;
        private LocalDate validTo;
        private Integer versionNumber;
        private String comment;
        private List<SubjectLineView> subjects;
    }

    @Data
    public static class SubjectLineView {
        private Long id;
        private String subjectName;
        private Long curriculumEntryId;
        private IupParticipationMode participationMode;
        private BigDecimal classHours;
        private BigDecimal individualHours;
        private String groupNameEducationalPlan;
        private List<TeacherAssignmentView> teachers;
    }

    @Data
    public static class TeacherAssignmentView {
        private Long id;
        private Long teacherId;
        private String teacherFullName;
        private BigDecimal hoursPerWeek;
        private IupDeliveryForm deliveryForm;
        private LocalDate validFrom;
        private LocalDate validTo;
    }
}
