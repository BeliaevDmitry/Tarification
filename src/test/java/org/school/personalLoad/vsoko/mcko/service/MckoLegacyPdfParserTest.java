package org.school.personalLoad.vsoko.mcko.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MckoLegacyPdfParserTest {
    private final MckoLegacyPdfParser parser = new MckoLegacyPdfParser();

    @Test
    void parsesStandardClassPdfRowsAndSummary() {
        String text = """
                Результаты диагностики учебных достижений обучающихся
                Дата: 21-25 октября 2024г. Предмет: Информационная безопасность Округ: Юго-Западный Школа: ГБОУ Школа № 7 Класс: 8Б
                Фамилия, имя № уч. Вариант 1 2 Балл % вып.
                1 1043 2 2 4 83
                2 1044 9116-0102 1 2 3 75
                Число учащихся: 2 Среднее: 3.5 79.0
                """;

        MckoLegacyPdfParser.ParsedPdf result = parser.parseText("9116_pm2740_8Б.pdf", text, "2024/2025").orElseThrow();

        assertEquals(MckoLegacyPdfParser.PdfKind.STANDARD, result.kind());
        assertEquals("8-Б", result.className());
        assertEquals("Информационная безопасность", result.subjectName());
        assertEquals(LocalDate.of(2024, 10, 25), result.workDate());
        assertEquals("2024/2025", result.academicYear());
        assertEquals(2, result.students().size());
        assertEquals(83D, result.students().get(0).percent());
        assertEquals("9116-0102", result.students().get(1).code());
        assertEquals(79D, result.summary().averagePercent());
    }

    @Test
    void parsesFunctionalLiteracyRows() {
        String text = """
                Результаты исследования функциональной грамотности
                Дата: 4-5 марта 2026г. Функциональная грамотность Округ: Юго-Западный Школа: ГБОУ Школа № 7 Класс: 6Ц
                Фамилия, имя № Код Вариант Задание ТБ % выполнения заданий Уровень
                1 9116-0049 3129 0 1 4 17% 25% 0% Ниже базового
                Число учащихся: 1 Среднее по классу: 4 17% 25% 0%
                """;

        MckoLegacyPdfParser.ParsedPdf result = parser.parseText("9116_pm3085_05_March_2026_ФГ-6Ц.pdf", text, "").orElseThrow();

        assertEquals(MckoLegacyPdfParser.PdfKind.FUNCTIONAL_LITERACY, result.kind());
        assertEquals(1, result.students().size());
        assertEquals("9116-0049", result.students().get(0).code());
        assertEquals("Ниже базового", result.students().get(0).masteryLevel());
        assertNull(result.students().get(0).section1Percent());
    }

    @Test
    void parsesReadingLiteracyBlockPercents() {
        String text = """
                Результаты диагностики функциональной грамотности
                Дата: 2-3 марта 2023 года Читательская грамотность Округ: Юго-Западный Школа: ГБОУ Школа № 7 Класс: 6А
                Фамилия, имя № Код Вариант Задание ТБ % выполнения заданий по блокам I II III Уровень
                1 9116-0030 1001 2 1 12 75% 73% 80% 75% 100% 60% Повышенный
                Число учащихся: 1 Среднее по классу: 12 75% 73% 80% 75% 100% 60%
                """;

        MckoLegacyPdfParser.ParsedPdf result = parser.parseText("9116_pm2092_03_March_2023_МГЧ-6А.pdf", text, "").orElseThrow();

        MckoLegacyPdfParser.StudentRow row = result.students().get(0);
        assertEquals(75D, row.percent());
        assertEquals(75D, row.section1Percent());
        assertEquals(100D, row.section2Percent());
        assertEquals(60D, row.section3Percent());
    }

    @Test
    void keepsClassSummaryWhenPdfHasNoStudentRows() {
        String text = """
                Результаты диагностики учебных достижений обучающихся
                Дата: 18.05.2023 Предмет: Математика Округ: Юго-Западный Школа: ГБОУ Школа № 7 Класс: 7К
                Фамилия, имя № уч. Вариант 1 Балл % вып.
                Число учащихся: 22 Среднее по классу: 8.4 56%
                """;

        MckoLegacyPdfParser.ParsedPdf result = parser.parseText("summary.pdf", text, "").orElseThrow();

        assertEquals(MckoLegacyPdfParser.PdfKind.CLASS_SUMMARY, result.kind());
        assertTrue(result.students().isEmpty());
        assertEquals(22, result.summary().participantCount());
        assertEquals(56D, result.summary().averagePercent());
    }
}
