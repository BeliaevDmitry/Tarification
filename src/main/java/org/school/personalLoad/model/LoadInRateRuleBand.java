package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "load_in_rate_rule_band")
public class LoadInRateRuleBand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "min_total_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal minTotalHours = BigDecimal.ZERO;

    @Column(name = "max_total_hours", precision = 10, scale = 2)
    private BigDecimal maxTotalHours;

    @Column(name = "suggested_included_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal suggestedIncludedHours = BigDecimal.ZERO;

    @Column(name = "rate_fraction", precision = 5, scale = 2)
    private BigDecimal rateFraction;
}
