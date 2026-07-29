package org.school.personalLoad.dto;

import java.time.LocalDate;
import java.util.List;

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
            String initialsDative
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
            Long loadInRateRuleId
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
}
