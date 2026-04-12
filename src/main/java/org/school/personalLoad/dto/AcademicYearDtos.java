package org.school.personalLoad.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

public final class AcademicYearDtos {
    private AcademicYearDtos() {}

    @Value
    @Builder
    public static class AcademicYearResponse {
        Long id;
        String name;
        LocalDate startDate;
        LocalDate endDate;
        Integer startYear;
    }

    @Value
    @Builder
    public static class AcademicYearListResponse {
        String currentAcademicYear;
        List<AcademicYearResponse> years;
    }

    @Data
    @NoArgsConstructor
    public static class CreateAcademicYearRequest {
        Integer startYear;
    }
}
