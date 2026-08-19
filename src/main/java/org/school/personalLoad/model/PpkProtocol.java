package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ppk_protocol", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ppk_protocol_number", columnNames = "protocol_number"),
        @UniqueConstraint(name = "uk_ppk_protocol_sequence", columnNames = {"calendar_year", "sequence_number"})
})
public class PpkProtocol {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(name = "calendar_year", nullable = false)
    private Integer calendarYear;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "protocol_number", nullable = false, length = 64)
    private String protocolNumber;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol_type", nullable = false, length = 32)
    private PpkProtocolType protocolType = PpkProtocolType.APPOINTMENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StudentProfile student;

    @Column(name = "class_name", length = 128)
    private String className;

    @Column(name = "chair_name", nullable = false)
    private String chairName = "Власова Ю.С.";

    @Column(name = "secretary_name", nullable = false)
    private String secretaryName = "Рыбкина Л.П.";

    @Column(name = "attendees", length = 4000)
    private String attendees;

    @Column(name = "invited_representative", length = 500)
    private String invitedRepresentative;

    @Column(name = "representative_name", length = 500)
    private String representativeName;

    @Column(name = "representative_signature_name", length = 500)
    private String representativeSignatureName;

    @Column(name = "agenda", length = 6000)
    private String agenda;

    @Column(name = "meeting_notes", length = 8000)
    private String meetingNotes;

    @Column(name = "decision_text", length = 8000)
    private String decisionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OvzStageStatus status = OvzStageStatus.NOT_RELEASED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
