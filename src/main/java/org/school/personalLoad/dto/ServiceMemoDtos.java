package org.school.personalLoad.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ServiceMemoDtos {

    @Value
    @Builder
    public static class PendingTeacher {
        String teacherKey;
        String fioTeacher;
        LocalDate startDate;
        String memoType;
        List<LoadRow> rows;
        int totalHours;
    }

    @Value
    @Builder
    public static class LoadRow {
        String fioTeacher;
        String subjectName;
        String className;
        Integer load;
        String status;
    }

    @Data
    public static class GenerateRequest {
        List<String> fioTeachers;
    }

    @Value
    @Builder
    public static class ProcessedMemo {
        Long id;
        Long teacherId;
        Long contractId;
        String fioTeacher;
        LocalDate startDate;
        String status;
        String createdBy;
        LocalDateTime createdAt;
        String generatedFilename;
        String correctedFilename;
        LocalDateTime signedAt;
        String archiveReason;
    }
}
