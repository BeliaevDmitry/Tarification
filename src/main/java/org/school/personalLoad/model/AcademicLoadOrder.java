package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "academic_load_order", indexes = {
        @Index(name = "idx_academic_load_order_year_date", columnList = "academic_year,order_date")
})
public class AcademicLoadOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AcademicLoadOrderType type;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    private String protocolNumber;
    private LocalDate protocolDate;
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private String signerName;

    @Column(nullable = false)
    private String signerPosition;

    private String controlOfficerName;

    @Column(nullable = false)
    private String schoolCodeSnapshot;

    @Column(nullable = false)
    private String schoolNameSnapshot;

    @Column(nullable = false)
    private Integer sourceItemCount = 0;

    @Column(nullable = false)
    private String createdByUsername;

    private String createdByFio;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private String documentFilename;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] documentContent;
}
