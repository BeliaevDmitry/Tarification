package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

@Data
@Entity
@Table(name = "load_in_rate_rule_subject", uniqueConstraints = {
        @UniqueConstraint(name = "uk_load_in_rate_rule_subject", columnNames = {"rule_id", "subject_id"})
})
public class LoadInRateRuleSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SubjectCatalogEntry subject;

    public Long getSubjectId() {
        return subject == null ? null : subject.getId();
    }
}
