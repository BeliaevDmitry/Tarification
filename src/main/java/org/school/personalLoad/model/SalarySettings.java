package org.school.personalLoad.model;

import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "salary_settings")
public class SalarySettings {
    public static final Long DEFAULT_ID = 1L;
    public static final BigDecimal DEFAULT_STUDENT_HOUR_RATE = BigDecimal.valueOf(37);

    @Id
    private Long id = DEFAULT_ID;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal studentHourRate = DEFAULT_STUDENT_HOUR_RATE;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
