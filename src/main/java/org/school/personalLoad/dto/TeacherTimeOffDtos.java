package org.school.personalLoad.dto;

import org.school.personalLoad.model.TeacherTimeOffEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TeacherTimeOffDtos {
    private TeacherTimeOffDtos() {
    }

    public record TeacherOption(Long id, String fio) {
    }

    public record AccrualRequest(
            Long teacherId,
            Integer durationMinutes,
            String reason,
            Boolean lessonRemoval,
            LocalDate eventDate
    ) {
    }

    public record UsageRequest(
            Long teacherId,
            BigDecimal days,
            LocalDate eventDate
    ) {
    }

    public record EntryView(
            Long id,
            Long teacherId,
            String teacherFio,
            TeacherTimeOffEntry.OperationType operationType,
            int amountMinutes,
            String reason,
            Boolean lessonRemoval,
            LocalDate eventDate,
            LocalDateTime createdAt,
            String createdByFio
    ) {
    }

    public record SummaryView(
            Long teacherId,
            String teacherFio,
            int earnedMinutes,
            int usedMinutes,
            int remainingMinutes
    ) {
    }

    public record WorkspaceView(
            List<SummaryView> summary,
            List<EntryView> entries
    ) {
    }
}
