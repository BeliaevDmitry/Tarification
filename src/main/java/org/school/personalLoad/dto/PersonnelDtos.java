package org.school.personalLoad.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.school.personalLoad.model.TeacherDirectoryEntry;

public final class PersonnelDtos {
    private PersonnelDtos() {
    }

    public record NameCases(
            String nominative,
            String genitive,
            String dative,
            String accusative,
            String instrumental,
            String prepositional,
            String initials,
            String initialsGenitive,
            String initialsDative,
            String initialsAccusative,
            String initialsInstrumental,
            String initialsPrepositional
    ) {
    }

    public record AcceptEmployeeRequest(
            Long vacancyTeacherId,
            String fioTeacher,
            String phone,
            String email,
            String numberSchoolBuilding,
            String primaryPosition,
            String employmentType,
            LocalDate employmentDate,
            LocalDate birthDate,
            String passportSeries,
            String passportNumber,
            String passportIssuedBy,
            LocalDate passportIssueDate,
            String passportDepartmentCode,
            String registrationAddress,
            String actualAddress,
            String inn,
            String snils,
            String contractNumber,
            LocalDate contractDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            Boolean loadHoursMayBeIncludedInRate,
            Long loadInRateRuleId,
            NameCases nameCases
    ) {
    }

    public record AcceptEmployeeResult(
            Long teacherId,
            boolean linkedToVacancy,
            String previousName,
            String fioTeacher,
            NameCases nameCases
    ) {
    }

    public record AutoBuildingResult(
            int assigned,
            int unchanged,
            int skippedWithoutLoad,
            int skippedTies,
            List<Long> tiedTeacherIds
    ) {
    }

    /**
     * Stable API projection for the personnel table. JPA entities can be represented
     * by Hibernate proxy classes inside a transaction; returning only scalar values
     * keeps persistence internals out of the HTTP response.
     */
    public record PersonnelRow(
            Long id,
            String fioTeacher,
            String fioTeacherGenitive,
            String fioTeacherDative,
            String fioTeacherAccusative,
            String fioTeacherInstrumental,
            String fioTeacherPrepositional,
            String initials,
            String initialsGenitive,
            String initialsDative,
            String initialsAccusative,
            String initialsInstrumental,
            String initialsPrepositional,
            String phone,
            String email,
            String additionalDuties,
            String additionalDutiesSummary,
            String numberSchoolBuilding,
            String primaryPosition,
            String personnelNumber,
            String employmentType,
            LocalDate employmentDate,
            LocalDateTime lastOneCSyncAt,
            LocalDate dismissalDate,
            LocalDate plannedDismissalDate,
            String plannedDismissalComment,
            String plannedDismissalMarkedBy,
            boolean archived,
            LocalDateTime archivedAt,
            LocalDateTime createdAt
    ) {
        public static PersonnelRow from(TeacherDirectoryEntry teacher, String dutiesSummary, NameCases cases) {
            return new PersonnelRow(
                    teacher.getId(),
                    cases.nominative(),
                    cases.genitive(),
                    cases.dative(),
                    cases.accusative(),
                    cases.instrumental(),
                    cases.prepositional(),
                    cases.initials(),
                    cases.initialsGenitive(),
                    cases.initialsDative(),
                    cases.initialsAccusative(),
                    cases.initialsInstrumental(),
                    cases.initialsPrepositional(),
                    teacher.getPhone(),
                    teacher.getEmail(),
                    teacher.getAdditionalDuties(),
                    dutiesSummary,
                    teacher.getNumberSchoolBuilding(),
                    teacher.getPrimaryPosition(),
                    teacher.getPersonnelNumber(),
                    teacher.getEmploymentType(),
                    teacher.getEmploymentDate(),
                    teacher.getLastOneCSyncAt(),
                    teacher.getDismissalDate(),
                    teacher.getPlannedDismissalDate(),
                    teacher.getPlannedDismissalComment(),
                    teacher.getPlannedDismissalMarkedBy(),
                    teacher.isArchived(),
                    teacher.getArchivedAt(),
                    teacher.getCreatedAt()
            );
        }
    }
}
