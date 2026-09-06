package org.school.personalLoad.dto;

import org.school.personalLoad.model.ExitOrderDictionaryType;
import org.school.personalLoad.model.ProbeOrderApprovalMode;
import org.school.personalLoad.model.ProbeOrderApprovalScope;
import org.school.personalLoad.model.ProbeOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class ExitOrderDtos {
    private ExitOrderDtos() {
    }

    public record StudentOption(Long id, String fullName, String className) {
    }

    public record ClassOption(Long id,
                              String className,
                              Integer parallel,
                              Long schoolBuildingId,
                              String buildingCode,
                              String buildingName,
                              String buildingAddress,
                              boolean suggested,
                              List<StudentOption> students) {
    }

    public record StaffOption(Long id, String fullName, String position, String buildingCode, String phone) {
    }

    public record ReferenceData(List<ClassOption> classes,
                                List<StaffOption> teachers,
                                List<StaffOption> signers,
                                Map<ExitOrderDictionaryType, List<String>> dictionaries,
                                List<Long> suggestedClassIds,
                                Long defaultCompanionTeacherId,
                                Long defaultSignerTeacherId,
                                String suggestedGatheringPlace) {
    }

    public record CreateRequest(String preamble,
                                String eventName,
                                LocalDate eventDate,
                                LocalTime startTime,
                                LocalTime endTime,
                                String venue,
                                String eventAddress,
                                LocalTime gatheringTime,
                                String gatheringPlace,
                                LocalTime returnTime,
                                List<Long> studentIds,
                                Long primaryCompanionTeacherId,
                                Long secondaryCompanionTeacherId,
                                List<Long> additionalCompanionTeacherIds) {
    }

    public record GenerateRequest(String orderNumber,
                                  LocalDate orderDate,
                                  Long signerTeacherId,
                                  String signerPosition) {
    }

    public record AttendanceRequest(List<Long> absentParticipantIds) {
    }

    public record SettingsRequest(ProbeOrderApprovalMode approvalMode,
                                  Long deputyDirectorTeacherId,
                                  Map<ExitOrderDictionaryType, List<String>> dictionaries) {
    }

    public record SettingsView(ProbeOrderApprovalMode approvalMode,
                               String approvalModeLabel,
                               Long deputyDirectorTeacherId,
                               String deputyDirectorName,
                               Map<ExitOrderDictionaryType, List<String>> dictionaries,
                               boolean canEdit) {
    }

    public record ParticipantView(Long id,
                                  Long studentId,
                                  String fullName,
                                  String className,
                                  String buildingCode,
                                  boolean absent) {
    }

    public record ApprovalView(ProbeOrderApprovalScope scopeType,
                               String scopeCode,
                               String scopeLabel,
                               LocalDateTime approvedAt,
                               String approvedBy) {
    }

    public record OrderView(Long id,
                            String academicYear,
                            String preamble,
                            String eventName,
                            LocalDate eventDate,
                            LocalTime startTime,
                            LocalTime endTime,
                            String venue,
                            String eventAddress,
                            LocalTime gatheringTime,
                            String gatheringPlace,
                            LocalTime returnTime,
                            Long schoolBuildingId,
                            String buildingCode,
                            String buildingName,
                            List<String> classNames,
                            int participantCount,
                            int absentCount,
                            int requiredCompanions,
                            StaffOption primaryCompanion,
                            StaffOption secondaryCompanion,
                            List<StaffOption> additionalCompanions,
                            ProbeOrderStatus status,
                            ProbeOrderApprovalMode approvalMode,
                            List<ApprovalView> approvals,
                            boolean approvalComplete,
                            String requestedBy,
                            LocalDateTime requestedAt,
                            String orderNumber,
                            LocalDate orderDate,
                            StaffOption signer,
                            String signerPosition,
                            boolean generatedDocumentAvailable,
                            boolean signedScanAvailable,
                            LocalDateTime attendanceMarkedAt,
                            List<ParticipantView> participants,
                            boolean canEdit,
                            boolean canAcknowledge,
                            boolean canGenerate,
                            boolean canRelease,
                            boolean canUploadScan,
                            boolean canMarkAttendance) {
    }

    public record ClassSummary(String className,
                               String buildingCode,
                               long events,
                               long attended,
                               long absent) {
    }

    public record TeacherSummary(Long teacherId,
                                 String fullName,
                                 String buildingCode,
                                 long events,
                                 long childrenAccompanied) {
    }

    public record SummaryView(List<ClassSummary> classes,
                              List<TeacherSummary> teachers,
                              long totalEvents,
                              long totalAttended,
                              long totalAbsent) {
    }
}
