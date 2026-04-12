package org.school.personalLoad.dto;

import lombok.Builder;
import lombok.Value;
import org.school.personalLoad.model.contingent.ContingentWarningType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ContingentDtos {
    private ContingentDtos() {}

    @Value @Builder
    public static class SnapshotResponse {
        Long id;
        String academicYear;
        LocalDate snapshotDate;
        LocalDateTime importedAt;
        String sourceFileName;
        Integer studentsCount;
        Integer warningsCount;
    }

    @Value @Builder
    public static class StudentResponse {
        Long id;
        String fullName;
        LocalDate birthDate;
        String classNameRaw;
        String classNameNormalized;
        Integer parallel;
        String buildingCode;
        Map<String, String> rawFields;
    }

    @Value @Builder
    public static class ClassSummaryResponse {
        String className;
        Integer parallel;
        String buildingCode;
        Integer studentsCount;
        boolean curriculumMatched;
    }

    @Value @Builder
    public static class ParallelSummaryResponse {
        Integer parallel;
        Integer classesCount;
        Integer studentsCount;
    }

    @Value @Builder
    public static class BuildingSummaryResponse {
        String buildingCode;
        Integer classesCount;
        Integer studentsCount;
    }

    @Value @Builder
    public static class WarningResponse {
        ContingentWarningType type;
        String className;
        String message;
    }

    @Value @Builder
    public static class ImportResultResponse {
        SnapshotResponse snapshot;
        List<WarningResponse> warnings;
    }
}

