package org.school.personalLoad.service;

import org.school.personalLoad.model.StudentCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The single source of truth for IUP compensation.
 *
 * <p>Keep the complete formula here so a future coefficient change does not
 * require coordinated edits in load, exports and HR documents.</p>
 */
@Service
public class IupCompensationCalculator {

    private static final BigDecimal WEEKS_PER_MONTH =
            BigDecimal.valueOf(34).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
    private static final BigDecimal K2_COEFFICIENT =
            BigDecimal.valueOf(25).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
    private static final BigDecimal K3_COEFFICIENT =
            BigDecimal.valueOf(25).divide(BigDecimal.valueOf(3), 10, RoundingMode.HALF_UP);

    public Calculation calculate(BigDecimal hoursPerWeek,
                                 BigDecimal subjectCoefficient,
                                 StudentCategory category) {
        BigDecimal hours = nonNegative(hoursPerWeek);
        BigDecimal subject = positiveOrOne(subjectCoefficient);
        BigDecimal categoryCoefficient = categoryCoefficient(category);
        BigDecimal amount = hours
                .multiply(subject)
                .multiply(WEEKS_PER_MONTH)
                .multiply(categoryCoefficient)
                .setScale(2, RoundingMode.HALF_UP);
        return new Calculation(hours, subject, categoryCoefficient, amount);
    }

    public BigDecimal categoryCoefficient(StudentCategory category) {
        StudentCategory effective = Objects.requireNonNullElse(category, StudentCategory.NORMAL);
        if (effective == StudentCategory.K2) {
            return K2_COEFFICIENT;
        }
        if (effective == StudentCategory.K3) {
            return K3_COEFFICIENT;
        }
        return BigDecimal.ONE;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    private BigDecimal positiveOrOne(BigDecimal value) {
        return value == null || value.signum() <= 0 ? BigDecimal.ONE : value;
    }

    public record Calculation(
            BigDecimal hoursPerWeek,
            BigDecimal subjectCoefficient,
            BigDecimal categoryCoefficient,
            BigDecimal monthlyAmount
    ) {
    }
}
