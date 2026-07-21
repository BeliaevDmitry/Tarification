package org.school.personalLoad.dto;

import org.school.personalLoad.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class HrDocumentDtos {
    private HrDocumentDtos() {}
    public record ContractRequest(Long teacherId, String contractNumber, LocalDate contractDate, String positionName,
                                  LocalDate startDate, LocalDate endDate, Boolean primaryContract, Boolean active) {}
    public record PersonalDataRequest(Long teacherId, LocalDate birthDate, String passportSeries, String passportNumber,
            String passportIssuedBy, LocalDate passportIssueDate, String passportDepartmentCode,
            String registrationAddress, String actualAddress, String phone, String inn, String snils) {}
    public record MemoRequest(String academicYear, Long teacherId, Long contractId, Long catalogItemId,
            String title, LocalDate documentDate, String assignmentName, String assignmentText,
            String agreementText, String contractClause, String dutiesText, BigDecimal amount, LocalDate validFrom, LocalDate validTo,
            Boolean separateAgreement, Boolean saveAsTemplate, String itemsJson) {}
    public record MemoView(Long id, String academicYear, Long teacherId, Long contractId, Long catalogItemId,
            String title, LocalDate documentDate, String assignmentName, BigDecimal amount,
            LocalDate validFrom, LocalDate validTo, boolean separateAgreement, String status,
            LocalDateTime createdAt, String createdBy, boolean deletable) {}
    public record AgreementRequest(Long contractId, Long serviceMemoId, String academicYear, LocalDate documentDate,
            LocalDate validFrom, LocalDate validTo, AdditionalAgreement.Kind kind,
            AdditionalAgreement.ChangeMode changeMode, String summary, String conditionsJson,
            BigDecimal totalAmount, Long replacesAgreementId) {}
    public record AgreementEditRequest(Long contractId, LocalDate documentDate, LocalDate validFrom,
            LocalDate validTo, String summary, String conditionsJson, BigDecimal totalAmount,
            Boolean saveAsTemplate, String templateName) {}
    public record BatchAgreementRequest(String academicYear, LocalDate documentDate, LocalDate validFrom,
            List<Long> contractIds, Long serviceMemoId) {}
    public record AnnulRequest(String reason) {}
    public record StatusRequest(String status) {}
    public record ChangeModeRequest(String changeMode, Long replacesAgreementId) {}
    public record JournalRow(Long teacherId, String fio, Long contractId, String contractNumber, String position,
            boolean personalDataComplete, List<AgreementView> agreements, String actionRequired) {}
    public record AgreementView(Long id, String internalNumber, String visibleNumber, int revision,
            String kind, String status, String changeMode, Long replacesAgreementId,
            LocalDate documentDate, LocalDate validFrom, LocalDate validTo, String summary, String conditionsJson,
            BigDecimal totalAmount, Long serviceMemoId, Long loadServiceMemoId, LocalDateTime issuedAt) {}
    public record AgreementListRow(Long teacherId, String fio, Long contractId, String contractNumber,
            String position, boolean personalDataComplete, AgreementView agreement) {}
}
