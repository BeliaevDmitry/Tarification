package org.school.educationalwork.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.school.educationalwork.model.ClassTeacherReport;
import org.school.educationalwork.model.ValidationResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassTeacherReportParserMultiRowTest {
    private final ClassTeacherReportParser parser = new ClassTeacherReportParser();

    @Test
    void acceptsSeveralStaffAndDiagnosticRows() throws Exception {
        ValidationResult<ClassTeacherReport> result = parser.parse(new ByteArrayInputStream(document(false)));

        assertTrue(result.valid(), () -> result.issues().toString());
        assertEquals(2, result.data().staffRecognitions().size());
        assertEquals(2, result.data().diagnostics().size());
        assertEquals("Сидоров Сергей Петрович", result.data().staffRecognitions().get(1).fullName());
        assertEquals("Математическая грамотность", result.data().diagnostics().get(1).name());
    }

    @Test
    void reportsExactRowAndColumnForIncompleteDiagnosticRow() throws Exception {
        ValidationResult<ClassTeacherReport> result = parser.parse(new ByteArrayInputStream(document(true)));

        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.location().contains("Диагностики МЦКО")
                && issue.location().contains("строка 4") && issue.location().contains("Дата")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.location().contains("Диагностики МЦКО")
                && issue.location().contains("строка 4") && issue.location().contains("Опубликовано")));
    }

    private byte[] document(boolean appendIncompleteDiagnostic) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Представления результатов деятельности классного руководителя «7А» класса Иванова Мария Петровна");

            table(document, new String[][]{
                    {"Класс", "Количество учащихся", "Отметка 5", "Отметки 4, 5", "С одной 3", "Отметки 3, 4", "неуспевающие"},
                    {"7А", "28", "-", "-", "-", "-", "-"}
            });
            table(document, new String[][]{{"ФИО обучающихся", "1 триместр", "2 триместр", "3 триместр", "Итоговая отметка"}});
            table(document, new String[][]{
                    {"Охват ДО внутри школы", "Охват ДО вне школы", "Не посещают ДО"},
                    {"20 человек / 80%", "5 человек / 10%", "3"}
            });
            table(document, new String[][]{
                    {"ГТО", "2"}, {"Движения Первых", "3"}, {"волонтеров", "1"}, {"Совет обучающихся", "1"}
            });
            table(document, new String[][]{{"Название рейтингового проекта", "Направление", "ФИО ответственного педагога", "Количество участников", "Количество призеров", "Количество победителей", "Результаты"}});
            table(document, new String[][]{{"Класс", "Классный руководитель", "Команда", "Участники", "Результат"}});
            table(document, new String[][]{{"Класс", "Классный руководитель", "ФИО обучающегося", "Результат"}});
            table(document, new String[][]{{"Класс", "Классный руководитель", "ФИО обучающегося", "Результат"}});
            table(document, new String[][]{{"Класс", "Классный руководитель", "Номинация", "ФИО обучающегося", "Результат"}});
            table(document, new String[][]{{"Класс", "Классный руководитель", "ФИО обучающегося", "Результат"}});
            table(document, new String[][]{
                    {"Чемпионаты", "Трансляция опыта", "Научные публикации", "Повышение квалификации"},
                    {"", "", "", ""}
            });
            table(document, new String[][]{
                    {"ФИО", "Категория", "Звание/награды/благодарности ДОМН"},
                    {"Петрова Ольга Сергеевна", "высшая", "Благодарность ДОМН"},
                    {"Сидоров Сергей Петрович", "первая", ""}
            });
            XWPFTable diagnostics = table(document, new String[][]{
                    {"Название", "Результат", "Дата", "Опубликовано"},
                    {"Предметная диагностика", "82%", "15.04.2026", "+"},
                    {"Математическая грамотность", "высокий", "22.05.2026", "-"}
            });
            if (appendIncompleteDiagnostic) {
                var row = diagnostics.createRow();
                row.getCell(0).setText("Дополнительная диагностика");
                row.getCell(1).setText("высокий");
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private XWPFTable table(XWPFDocument document, String[][] values) {
        XWPFTable table = document.createTable(values.length, values[0].length);
        for (int r = 0; r < values.length; r++) {
            for (int c = 0; c < values[r].length; c++) {
                table.getRow(r).getCell(c).setText(values[r][c]);
            }
        }
        return table;
    }
}
