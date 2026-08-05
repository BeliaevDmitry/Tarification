package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.StudentCategory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IupCompensationCalculatorTest {

    private final IupCompensationCalculator calculator = new IupCompensationCalculator();

    @Test
    void calculatesAllIupCategoryVariantsInOnePlace() {
        assertEquals(
                new BigDecimal("5.67"),
                calculator.calculate(new BigDecimal("2"), BigDecimal.ONE, StudentCategory.NORMAL)
                        .monthlyAmount()
        );
        assertEquals(
                new BigDecimal("70.83"),
                calculator.calculate(new BigDecimal("2"), BigDecimal.ONE, StudentCategory.K2)
                        .monthlyAmount()
        );
        assertEquals(
                new BigDecimal("47.22"),
                calculator.calculate(new BigDecimal("2"), BigDecimal.ONE, StudentCategory.K3)
                        .monthlyAmount()
        );
    }

    @Test
    void appliesSubjectCoefficientBeforeCategoryCoefficient() {
        assertEquals(
                new BigDecimal("85.00"),
                calculator.calculate(new BigDecimal("2"), new BigDecimal("1.20"), StudentCategory.K2)
                        .monthlyAmount()
        );
    }
}
