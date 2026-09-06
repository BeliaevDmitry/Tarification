package org.school.personalLoad.masterfot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FotDtos {
    private FotDtos() {}
    public record SourceRow(int row, String teacher, String group, String part, String subject,
                            BigDecimal total, BigDecimal assigned, BigDecimal unassigned) {}
    public record Source(String academicYear, LocalDate date, String organization, List<SourceRow> rows) {}
    public record Finding(String key, String type, String building, String className, String subject,
                          String teacher, String expected, String actual, String detail,
                          String mappingType, String mappingSource) {}
    public record Choice(String id, String label) {}
    public record MappingRequest(String type, String source, String target) {}
    public record DecisionRequest(String status, String comment, long version) {}
    public record BatchRow(Long id, String filename, LocalDate date, LocalDateTime importedAt,
                           String importedBy, int rows, int findings, boolean complete) {}
    public record IssueRow(String id, Finding finding, String status, String comment, boolean archived,
                           Long firstBatchId, Long lastBatchId, Long archivedBatchId,
                           LocalDateTime updatedAt, String updatedBy, long version) {}
    public record Overview(List<BatchRow> batches, List<IssueRow> issues) {}
    public record Options(List<Choice> groups, List<Choice> subjects, List<Choice> teachers,
                          List<MappingRequest> mappings) {}
    public record Comparison(List<Finding> findings, boolean complete) {}
}
