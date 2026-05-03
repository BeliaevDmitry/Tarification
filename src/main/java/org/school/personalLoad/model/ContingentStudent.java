package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "contingent_student", indexes = {
        @Index(name = "idx_contingent_student_snapshot", columnList = "snapshotId"),
        @Index(name = "idx_contingent_student_class", columnList = "className")
})
public class ContingentStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long snapshotId;

    @Column(nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String recordNumber;

    @Column(nullable = false)
    private String enrollmentDate;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String shortName;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false)
    private String birthDate;

    @Column(nullable = false)
    private String birthCertificate;

    @Column(nullable = false)
    private String socialCard;

    @Column(nullable = false)
    private String pensionInsurance;

    @Column(nullable = false)
    private String medicalInsurance;

    @Column(nullable = false)
    private String passport;

    @Column(nullable = false)
    private String citizenship;

    @Column(nullable = false)
    private String additionalInfoCode;

    @Column(nullable = false)
    private String aoopVariant;

    @Column(nullable = false)
    private String educationReceivingForm;

    @Column(nullable = false)
    private String educationForm;

    @Column(nullable = false)
    private String className;

    @Column(nullable = false)
    private String alphabetBookNumber;

    @Column(nullable = false, length = 1000)
    private String registrationAddress;

    @Column(nullable = false, length = 1000)
    private String temporaryRegistrationAddress;

    @Column(nullable = false, length = 1000)
    private String actualAddress;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String onVshuFrom;

    @Column(nullable = false, length = 1000)
    private String onVshuReason;

    @Column(nullable = false)
    private String onKdnFrom;

    @Column(nullable = false, length = 1000)
    private String onKdnReason;

    @Column(nullable = false)
    private String onPdnFrom;

    @Column(nullable = false, length = 1000)
    private String onPdnReason;

    @Column(nullable = false)
    private String removedFromVshu;

    @Column(nullable = false, length = 1000)
    private String removedFromVshuReason;

    @Column(nullable = false, columnDefinition = "text")
    private String rawPayload;
}
