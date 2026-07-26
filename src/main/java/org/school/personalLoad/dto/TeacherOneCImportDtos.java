package org.school.personalLoad.dto;

import java.time.LocalDate;
import java.util.List;

public final class TeacherOneCImportDtos {

    private TeacherOneCImportDtos() {
    }

    public record Preview(
            int sourceRowCount,
            LocalDate effectiveDate,
            List<PreviewRow> rows
    ) {
    }

    public record PreviewRow(
            String fio,
            Long teacherId,
            String currentPosition,
            String proposedPosition,
            String personnelNumber,
            String employmentType,
            LocalDate employmentDate,
            LocalDate dismissalDate,
            boolean activePrimaryEmployment,
            boolean activeAdditionalEmployment,
            String message,
            List<String> allowedActions,
            String recommendedAction
    ) {
    }

    public record ApplyRequest(List<Decision> decisions) {
    }

    public record Decision(String fio, String action) {
    }
}
