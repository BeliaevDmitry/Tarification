package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_support_document", indexes = {
        @Index(name = "idx_support_document_student_year", columnList = "student_id,academic_year"),
        @Index(name = "idx_support_document_dates", columnList = "valid_from,valid_to"),
        @Index(name = "idx_support_document_type", columnList = "document_type")
})
public class StudentSupportDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StudentProfile student;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private StudentSupportDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "accepted_form", nullable = false)
    private StudentSupportDocumentForm acceptedForm = StudentSupportDocumentForm.COPY;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "nosology_code", length = 16)
    private String nosologyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_stage", length = 16)
    private SupportEducationStage educationStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_program", length = 64)
    private CpmPcEducationProgram educationProgram;

    @Column(name = "prolongation_available", nullable = false)
    private boolean prolongationAvailable;

    @Column(name = "prolongation_used", nullable = false)
    private boolean prolongationUsed;

    @Column(name = "prolonged_grade")
    private Integer prolongedGrade;

    @Column(name = "prolonged_academic_year", length = 16)
    private String prolongedAcademicYear;

    @Column(name = "ipra_present", nullable = false)
    private boolean ipraPresent;

    @Column(name = "issuing_organization", length = 500)
    private String issuingOrganization;

    @Column(name = "received_at")
    private LocalDate receivedAt;

    @Column(name = "responsible_employee", length = 255)
    private String responsibleEmployee;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
