package org.school.personalLoad.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class LoadInRateDtos {
    private LoadInRateDtos() {
    }

    public record RuleBandRequest(
            BigDecimal minTotalHours,
            BigDecimal maxTotalHours,
            BigDecimal suggestedIncludedHours,
            BigDecimal rateFraction,
            BigDecimal fixedMonthlySalary
    ) {
        public RuleBandRequest(BigDecimal minTotalHours, BigDecimal maxTotalHours,
                               BigDecimal suggestedIncludedHours, BigDecimal rateFraction) {
            this(minTotalHours, maxTotalHours, suggestedIncludedHours, rateFraction, BigDecimal.ZERO);
        }
    }

    public record RuleRequest(
            String name,
            String documentLabel,
            Boolean active,
            List<Long> subjectIds,
            List<RuleBandRequest> bands
    ) {
    }

    public record AllowedSubjectView(Long id, String name) {
    }

    public record RuleBandView(
            Long id,
            BigDecimal minTotalHours,
            BigDecimal maxTotalHours,
            BigDecimal suggestedIncludedHours,
            BigDecimal rateFraction,
            BigDecimal fixedMonthlySalary
    ) {
    }

    public record RuleView(
            Long id,
            String name,
            String documentLabel,
            boolean active,
            List<AllowedSubjectView> subjects,
            List<RuleBandView> bands,
            LocalDateTime updatedAt
    ) {
    }

    public record AllocationUpdate(
            Long manualLoadEntryId,
            Long contractId,
            BigDecimal includedHours,
            String reason
    ) {
    }

    public record AllocationBatchRequest(List<AllocationUpdate> rows) {
    }

    public record AllocationRow(
            Long manualLoadEntryId,
            Long teacherId,
            String fio,
            Long contractId,
            String contractNumber,
            String positionName,
            Long ruleId,
            String ruleName,
            String documentLabel,
            String academicYear,
            String building,
            String subject,
            String className,
            String groupName,
            String studyPeriod,
            LocalDate loadFrom,
            LocalDate loadTo,
            BigDecimal totalHours,
            BigDecimal includedHours,
            BigDecimal paidHours,
            boolean allocationConfirmed,
            String reason,
            int children,
            BigDecimal subjectCoefficient,
            BigDecimal groupCoefficient,
            BigDecimal amount
    ) {
    }

    public record TeacherSummary(
            Long teacherId,
            String fio,
            Long contractId,
            String contractNumber,
            String positionName,
            BigDecimal totalHoursH1,
            BigDecimal totalHoursH2,
            BigDecimal includedHoursH1,
            BigDecimal includedHoursH2,
            BigDecimal paidHoursH1,
            BigDecimal paidHoursH2,
            BigDecimal suggestedIncludedHours,
            BigDecimal suggestedRateFraction,
            BigDecimal capacityHoursH1,
            BigDecimal capacityHoursH2,
            BigDecimal remainingCapacityHoursH1,
            BigDecimal remainingCapacityHoursH2,
            BigDecimal rateFractionH1,
            BigDecimal rateFractionH2,
            BigDecimal fixedMonthlySalaryH1,
            BigDecimal fixedMonthlySalaryH2,
            BigDecimal matchedRangeMinHoursH1,
            BigDecimal matchedRangeMaxHoursH1,
            BigDecimal matchedRangeMinHoursH2,
            BigDecimal matchedRangeMaxHoursH2,
            boolean complete,
            int unresolvedRows
    ) {
    }

    public record Overview(
            List<AllocationRow> rows,
            List<TeacherSummary> teachers,
            boolean hasUnresolvedRows
    ) {
    }

    public record SaveResult(int updated, int unresolved, boolean agreementsRequireReissue) {
    }
}
