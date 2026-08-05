package org.school.personalLoad.dto.contingent;

import lombok.Data;
import org.school.personalLoad.model.IupOrderTemplateType;
import org.school.personalLoad.model.StudentGender;

import java.time.LocalDate;
import java.util.List;

public final class IupOrderDocumentDtos {

    private IupOrderDocumentDtos() {
    }

    @Data
    public static class GenerateRequest {
        private IupOrderTemplateType templateType;
        private List<Long> iupPlanIds;
        private String orderNumber;
        private LocalDate orderDate;
        private StudentGender studentGender;
        private String studentNameForOrder;
        private String medicalConclusionNumber;
        private LocalDate medicalConclusionDate;
        private String medicalOrganization;
        private String pedagogicalCouncilProtocolNumber;
        private LocalDate pedagogicalCouncilProtocolDate;
        private String ppkProtocolNumber;
        private LocalDate ppkProtocolDate;
        private String previousOrderNumber;
        private LocalDate previousOrderDate;
        private String responsibleCoordinator;
        private String electronicJournalAdministrator;
        private String enrollmentAdministrator;
        private String controlOfficer;
        private String executor;
        private String directorName;
        private String educationLevelAndForm;
    }
}
