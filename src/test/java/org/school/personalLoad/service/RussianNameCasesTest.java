package org.school.personalLoad.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RussianNameCasesTest {

    @Test
    void declinesMaleStudentFullNameForPpkDocuments() {
        var cases = RussianNameCases.derive("Левинов Николай Михайлович");

        assertEquals("Левинова Николая Михайловича", cases.genitive());
        assertEquals("Левинову Николаю Михайловичу", cases.dative());
        assertFalse(RussianNameCases.isFemale(cases.nominative()));
    }

    @Test
    void declinesFemaleStudentFullNameForPpkDocuments() {
        var cases = RussianNameCases.derive("Иванова Анна Сергеевна");

        assertEquals("Ивановой Анны Сергеевны", cases.genitive());
        assertEquals("Ивановой Анне Сергеевне", cases.dative());
        assertTrue(RussianNameCases.isFemale(cases.nominative()));
    }

    @Test
    void declinesFemaleNameLoveEndingWithSoftSign() {
        var cases = RussianNameCases.derive("Сапрыкина Любовь Романовна");

        assertEquals("Сапрыкиной Любови Романовны", cases.genitive());
        assertEquals("Сапрыкиной Любови Романовне", cases.dative());
        assertEquals("Сапрыкину Любовь Романовну", cases.accusative());
        assertEquals("Сапрыкиной Любовью Романовной", cases.instrumental());
        assertTrue(RussianNameCases.isFemale(cases.nominative()));
    }
}
