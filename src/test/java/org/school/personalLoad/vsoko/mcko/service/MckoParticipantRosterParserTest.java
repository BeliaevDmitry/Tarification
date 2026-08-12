package org.school.personalLoad.vsoko.mcko.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MckoParticipantRosterParserTest {
    @Test
    void parsesLegacyParticipantListAndDateFromCp866ArchiveName() {
        String text = """
                СПИСОК КОДОВ УЧАСТНИКОВ
                Оценка качества образования
                ГБОУ Школа № 7
                Класс: 10-А
                Русский язык
                Код
                ФИО обучающегося
                участника
                Белогорохов Сергей Сергеевич 9116-0271
                Белоусов Владислав Николаевич 9116-0272
                """;

        MckoParticipantRosterParser.ParsedRoster roster = new MckoParticipantRosterParser()
                .parseText("9116_РУ_10-А_28апреля_список кодов участников.pdf",
                        "9116_list_mcl-28apr26-ir.zip", text, "2025/2026")
                .orElseThrow();

        assertEquals("10-А", roster.className());
        assertEquals("Русский язык", roster.subjectName());
        assertEquals(LocalDate.of(2026, 4, 28), roster.workDate());
        assertEquals("2025/2026", roster.academicYear());
        assertEquals(2, roster.participants().size());
        assertEquals("9116-0271", roster.participants().get(0).code());
        assertEquals(1, roster.participants().get(0).studentNumber());
    }

    @Test
    void parsesNumericDateFromOlderArchiveNameWithoutYearHint() {
        String text = """
                СПИСОК КОДОВ УЧАСТНИКОВ
                ГБОУ Школа № 7
                Класс: 10-А
                Математика
                ФИО обучающегося
                Иванов Иван Иванович 9116-0001
                """;

        MckoParticipantRosterParser.ParsedRoster roster = new MckoParticipantRosterParser()
                .parseText("9116_МАТ_10-А_список.pdf", "9116_list_10cl-28-04-23.zip", text, "")
                .orElseThrow();

        assertEquals(LocalDate.of(2023, 4, 28), roster.workDate());
        assertEquals("2022/2023", roster.academicYear());
    }
}
