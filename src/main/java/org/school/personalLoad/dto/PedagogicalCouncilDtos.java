package org.school.personalLoad.dto;

import org.school.personalLoad.model.PedagogicalCouncilProtocol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class PedagogicalCouncilDtos {

    private PedagogicalCouncilDtos() {
    }

    public record ProtocolSummary(
            Long id,
            String academicYear,
            String protocolNumber,
            LocalDate meetingDate,
            PedagogicalCouncilProtocol.Status status,
            PedagogicalCouncilProtocol.SourceType sourceType,
            int attendeeCount,
            int itemCount,
            int attachmentCount,
            String fileName,
            String createdByFio,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ProtocolDetails(
            Long id,
            String academicYear,
            String protocolNumber,
            LocalDate meetingDate,
            LocalTime agendaTime,
            PedagogicalCouncilProtocol.Status status,
            PedagogicalCouncilProtocol.SourceType sourceType,
            String schoolCode,
            String schoolName,
            int attendeeCount,
            Long chairTeacherId,
            String chairPosition,
            String chairFio,
            Long secretaryTeacherId,
            String secretaryPosition,
            String secretaryFio,
            String archiveFilename,
            String createdByUsername,
            String createdByFio,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime registeredAt,
            String registeredBy,
            long version,
            List<ItemView> items,
            String headerFingerprint
    ) {
    }

    public record ItemView(
            Long id,
            int itemOrder,
            String agendaTitle,
            Integer agendaDurationMinutes,
            Long speakerTeacherId,
            String speakerPosition,
            String speakerFio,
            String speechContent,
            String decisionText,
            int votesFor,
            int votesAgainst,
            int votesAbstained,
            List<AttachmentView> attachments,
            String fingerprint
    ) {
    }

    public record AttachmentView(
            Long id,
            int attachmentNumber,
            String originalFilename,
            String uploadedBy,
            LocalDateTime createdAt
    ) {
    }

    public record StaffView(
            Long id,
            String fio,
            String shortFio,
            String position
    ) {
    }

    public record CertifierView(
            Long userId,
            Long teacherId,
            String fio,
            String shortFio,
            String position
    ) {
    }

    public record CreateProtocolRequest(
            String academicYear,
            String protocolNumber,
            LocalDate meetingDate,
            LocalTime agendaTime,
            Integer attendeeCount,
            String chairPosition,
            String chairFio,
            String secretaryPosition,
            String secretaryFio,
            List<ItemRequest> items
    ) {
    }

    public record UpdateProtocolRequest(
            String protocolNumber,
            LocalDate meetingDate,
            LocalTime agendaTime,
            Integer attendeeCount,
            String chairPosition,
            String chairFio,
            String secretaryPosition,
            String secretaryFio,
            PedagogicalCouncilProtocol.Status status,
            Long version,
            List<ItemRequest> items,
            String baseHeaderFingerprint,
            List<RemovedItemRequest> removedItems
    ) {
        public UpdateProtocolRequest(String protocolNumber,
                                     LocalDate meetingDate,
                                     LocalTime agendaTime,
                                     Integer attendeeCount,
                                     String chairPosition,
                                     String chairFio,
                                     String secretaryPosition,
                                     String secretaryFio,
                                     PedagogicalCouncilProtocol.Status status,
                                     Long version,
                                     List<ItemRequest> items) {
            this(protocolNumber, meetingDate, agendaTime, attendeeCount, chairPosition, chairFio,
                    secretaryPosition, secretaryFio, status, version, items, null, List.of());
        }
    }

    public record ItemRequest(
            Long id,
            String agendaTitle,
            Integer agendaDurationMinutes,
            Long speakerTeacherId,
            String speakerPosition,
            String speechContent,
            String decisionText,
            Integer votesFor,
            Integer votesAgainst,
            Integer votesAbstained,
            String baseFingerprint
    ) {
        public ItemRequest(Long id,
                           String agendaTitle,
                           Integer agendaDurationMinutes,
                           Long speakerTeacherId,
                           String speakerPosition,
                           String speechContent,
                           String decisionText,
                           Integer votesFor,
                           Integer votesAgainst,
                           Integer votesAbstained) {
            this(id, agendaTitle, agendaDurationMinutes, speakerTeacherId, speakerPosition,
                    speechContent, decisionText, votesFor, votesAgainst, votesAbstained, null);
        }

        public ItemRequest(Long id,
                           String agendaTitle,
                           Integer agendaDurationMinutes,
                           Long speakerTeacherId,
                           String speechContent,
                           String decisionText,
                           Integer votesFor,
                           Integer votesAgainst,
                           Integer votesAbstained) {
            this(id, agendaTitle, agendaDurationMinutes, speakerTeacherId, null,
                    speechContent, decisionText, votesFor, votesAgainst, votesAbstained, null);
        }
    }

    public record RemovedItemRequest(Long id, String baseFingerprint) {
    }

    public record ExtractRequest(
            List<Long> itemIds,
            List<Long> certifierUserIds,
            List<SignerRequest> certifiers,
            boolean externalRecipient,
            String originalStorageLocation,
            boolean includeSourceSigners,
            boolean separateApproval,
            Long approverTeacherId,
            String approverPosition
    ) {
        public ExtractRequest(List<Long> itemIds,
                              List<Long> certifierUserIds,
                              boolean externalRecipient,
                              String originalStorageLocation,
                              boolean separateApproval,
                              Long approverTeacherId) {
            this(itemIds, certifierUserIds, List.of(), externalRecipient, originalStorageLocation,
                    false, separateApproval, approverTeacherId, null);
        }
    }

    public record SignerRequest(
            Long userId,
            String position
    ) {
    }

    public record FilePayload(String filename, byte[] content) {
    }
}
