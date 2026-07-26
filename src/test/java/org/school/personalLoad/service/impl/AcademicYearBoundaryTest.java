package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademicYearBoundaryTest {

    @Test
    void academicYearChangesOnFirstOfAugust() {
        assertEquals("2025/2026", AcademicYearServiceImpl.academicYearForDate(LocalDate.of(2026, 7, 31)));
        assertEquals("2026/2027", AcademicYearServiceImpl.academicYearForDate(LocalDate.of(2026, 8, 1)));
    }
}
