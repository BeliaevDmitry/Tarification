package org.school.personalLoad.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectAreaNamesTest {

    @Test
    void baseAreasContainElevenFixedSubjectAreas() {
        assertEquals(11, SubjectAreaNames.BASE_AREAS.size());
        assertTrue(SubjectAreaNames.BASE_AREAS.contains("Коррекционно-развивающая область"));
        assertTrue(SubjectAreaNames.BASE_AREAS.contains("Иное"));
    }

    @Test
    void resolvesNewFixedSubjectAreas() {
        assertEquals("Коррекционно-развивающая область", SubjectAreaNames.resolveBaseArea("Коррекционно-развивающая область"));
        assertEquals("Иное", SubjectAreaNames.resolveBaseArea("Иное"));
    }
}
