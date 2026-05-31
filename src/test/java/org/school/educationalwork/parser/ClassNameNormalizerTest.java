package org.school.educationalwork.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassNameNormalizerTest {
    private final ClassNameNormalizer normalizer = new ClassNameNormalizer();

    @Test void normalizesLatinAndSeparators() {
        assertEquals("7А", normalizer.normalize("7-a").orElseThrow());
        assertEquals("10Б", normalizer.normalize("10 б").orElseThrow());
    }

    @Test void rejectsImpossibleGrade() {
        assertTrue(normalizer.normalize("12А").isEmpty());
    }
}
