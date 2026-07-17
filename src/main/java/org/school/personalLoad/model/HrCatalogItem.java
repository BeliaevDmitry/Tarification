package org.school.personalLoad.model;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hr_catalog_item")
public class HrCatalogItem {
    public enum Category { COMPENSATION, INCENTIVE, ADDITIONAL_WORK }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String schoolCode;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Category category;
    private String contractClause;
    private BigDecimal defaultAmount;
    @Column(length = 4000) private String memoText;
    @Column(length = 4000) private String agreementText;
    @Lob private String dutiesText;
    @Column(nullable = false) private boolean separateAgreement;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
}
