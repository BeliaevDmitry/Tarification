package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "primary_subject_rule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_primary_subject_rule_name", columnNames = "primarySubject")
})
public class PrimarySubjectRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String primarySubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrimarySubjectRuleType ruleType = PrimarySubjectRuleType.KEYWORDS;

    @Column(nullable = false, length = 2000)
    private String ruleValue = "";

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
