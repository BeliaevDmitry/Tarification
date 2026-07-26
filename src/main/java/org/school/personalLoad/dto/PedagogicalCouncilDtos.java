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
            List<ItemView> items
    ) {
    }

    public record ItemView(
            Long id,
            int itemOrder,
            String agendaTitle,
            LocalTime agendaTime,
            Long speakerTeacherId,
            String speakerPosition,
            String speakerFio,
            String speechContent,
            String decisionText,
            int votesFor,
            int votesAgainst,
            int votesAbstained,
            List<AttachmentView> attachments
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
            Long chairTeacherId,
            Long secretaryTeacherId,
            List<ItemRequest> items
    ) {
    }

    public record UpdateProtocolRequest(
            String protocolNumber,
            LocalDate meetingDate,
            LocalTime agendaTime,
            Integer attendeeCount,
            Long chairTeacherId,
            Long secretaryTeacherId,
            PedagogicalCouncilProtocol.Status status,
            Long version,
            List<ItemRequest> items
    ) {
    }

    public record ItemRequest(
            Long id,
            String agendaTitle,
            LocalTime agendaTime,
            Long speakerTeacherId,
            String speechContent,
            String decisionText,
            Integer votesFor,
            Integer votesAgainst,
            Integer votesAbstained
    ) {
    }

    public record StatusRequest(PedagogicalCouncilProtocol.Status status) {
    }

    public record ExtractRequest(
            List<Long> itemIds,
            List<Long> certifierUserIds,
            boolean externalRecipient,
            String originalStorageLocation,
            boolean separateApproval,
            Long approverTeacherId
    ) {
    }

    public record FilePayload(String filename, byte[] content) {
    }
}
