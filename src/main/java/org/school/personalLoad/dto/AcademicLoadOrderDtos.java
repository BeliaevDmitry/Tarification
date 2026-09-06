package org.school.personalLoad.dto;

import org.school.personalLoad.model.AcademicLoadOrderType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AcademicLoadOrderDtos {
    private AcademicLoadOrderDtos() {
    }

    public record CreateRequest(
            String academicYear,
            AcademicLoadOrderType type,
            String orderNumber,
            LocalDate orderDate,
            String protocolNumber,
            LocalDate protocolDate,
            LocalDate effectiveDate,
            String signerName,
            String signerPosition,
            String controlOfficerName,
            String basisText
    ) {
    }

    public record OrderView(
            Long id,
            String academicYear,
            AcademicLoadOrderType type,
            String typeLabel,
            String orderNumber,
            LocalDate orderDate,
            String schoolCode,
            int sourceItemCount,
            String createdBy,
            LocalDateTime createdAt,
            String documentFilename
    ) {
    }

    public record ReadinessView(
            String academicYear,
            int curriculumEntryCount,
            int curriculumPlanCount,
            int loadEntryCount,
            int teacherCount
    ) {
    }

    public record StaffView(Long id, String fullName, String position) {
    }

    public record ReferencesView(List<StaffView> staff, Long suggestedSignerId) {
    }
}
