package org.school.personalLoad.dto;

import org.school.personalLoad.model.ProbeOrderStatus;
import org.school.personalLoad.model.ProbeOrderApprovalMode;
import org.school.personalLoad.model.ProbeOrderApprovalScope;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class ProbeOrderDtos {
    private ProbeOrderDtos() {
    }

    public record ImportResponse(int eventsRead,
                                 int applicationsRead,
                                 int ordersCreated,
                                 int ordersUpdated,
                                 int releasedOrdersSkipped,
                                 int participantsLinked,
                                 int unresolvedApplications,
                                 List<String> warnings) {
    }

    public record ParticipantView(Long id,
                                  Long studentId,
                                  String fullName,
                                  String className,
                                  String childPhone,
                                  String representativeName,
                                  String representativePhone,
                                  boolean linkedToStudentCard,
                                  List<String> missingData) {
    }

    public record StaffOption(Long id,
                              String fullName,
                              String phone,
                              String position,
                              String buildingCode) {
    }

    public record StudentOption(Long id, String fullName, String className) {
    }

    public record ReferenceData(List<StaffOption> teachers,
                                List<StaffOption> signers,
                                List<StudentOption> students,
                                Long defaultSignerTeacherId) {
    }

    public record CompanionRequest(Long primaryTeacherId, Long secondaryTeacherId) {
    }

    public record ParticipantRequest(Long studentId,
                                     String fullName,
                                     String className,
                                     String childPhone,
                                     String representativeName,
                                     String representativePhone) {
    }

    public record ContactRefreshResponse(int participantsChecked,
                                         int participantsUpdated,
                                         int participantsLinked,
                                         int participantsStillMissingContacts,
                                         OrderView order) {
    }

    public record EditRequest(String eventName,
                              LocalDate eventDate,
                              LocalTime startTime,
                              LocalTime endTime,
                              String venue,
                              String eventAddress,
                              LocalTime gatheringTime,
                              String gatheringPlace,
                              LocalTime returnTime,
                              List<ParticipantRequest> participants) {
    }

    public record GenerateRequest(String orderNumber,
                                  LocalDate orderDate,
                                  Long signerTeacherId,
                                  String signerPosition) {
    }

    public record SettingsRequest(ProbeOrderApprovalMode approvalMode) {
    }

    public record SettingsView(ProbeOrderApprovalMode approvalMode,
                               String approvalModeLabel,
                               boolean canEdit) {
    }

    public record ApprovalView(ProbeOrderApprovalScope scopeType,
                               String scopeCode,
                               String scopeLabel,
                               LocalDateTime approvedAt,
                               String approvedBy) {
    }

    public record OrderView(Long id,
                            String academicYear,
                            String externalEventId,
                            String eventName,
                            LocalDate eventDate,
                            LocalTime startTime,
                            LocalTime endTime,
                            String venue,
                            String eventAddress,
                            String organizer,
                            String partner,
                            Long schoolBuildingId,
                            String buildingCode,
                            String buildingName,
                            String gatheringPlace,
                            LocalTime gatheringTime,
                            LocalTime returnTime,
                            List<String> classNames,
                            int participantCount,
                            int requiredCompanions,
                            StaffOption primaryCompanion,
                            StaffOption secondaryCompanion,
                            boolean companionsComplete,
                            ProbeOrderStatus status,
                            ProbeOrderApprovalMode approvalMode,
                            List<ApprovalView> approvals,
                            boolean approvalComplete,
                            LocalDateTime buildingApprovedAt,
                            String buildingApprovedBy,
                            String orderNumber,
                            LocalDate orderDate,
                            StaffOption signer,
                            String signerPosition,
                            boolean generatedDocumentAvailable,
                            boolean signedScanAvailable,
                            LocalDateTime releasedAt,
                            String releasedBy,
                            String sourceFileName,
                            LocalDateTime sourceUploadedAt,
                            List<ParticipantView> participants,
                            List<String> dataWarnings,
                            String highlight,
                            boolean canEdit,
                            boolean canAcknowledge,
                            boolean canGenerate,
                            boolean canRelease,
                            boolean canUploadScan) {
    }

    public record CalendarEvent(Long orderId,
                                String title,
                                LocalDate date,
                                LocalTime startTime,
                                LocalTime endTime,
                                String buildingCode,
                                String buildingName,
                                List<String> classNames,
                                List<String> companions,
                                String venue,
                                String address,
                                List<CalendarParticipant> participants) {
    }

    public record CalendarParticipant(String type,
                                      Long id,
                                      String code,
                                      String label,
                                      String details) {
    }

    public record HistoryEvent(Long orderId,
                               String academicYear,
                               String eventName,
                               LocalDate eventDate,
                               LocalTime startTime,
                               LocalTime endTime,
                               String venue,
                               String eventAddress,
                               String buildingCode,
                               List<String> classNames,
                               List<String> companions,
                               String orderNumber,
                               LocalDate orderDate) {
    }

    public record FilePayload(String filename, String contentType, byte[] content) {
    }
}
