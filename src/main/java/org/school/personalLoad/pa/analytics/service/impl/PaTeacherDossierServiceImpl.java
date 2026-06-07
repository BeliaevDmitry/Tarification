package org.school.personalLoad.pa.analytics.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.model.PaReportTaskResult;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportTaskResultRepository;
import org.school.personalLoad.pa.analytics.service.PaReportAnalysisService;
import org.school.personalLoad.pa.analytics.service.PaTeacherAnalyticsService;
import org.school.personalLoad.pa.analytics.service.PaTeacherDossierService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaTeacherDossierServiceImpl implements PaTeacherDossierService {

    private static final String DYNAMIC_NOT_AVAILABLE = "NOT_AVAILABLE_NO_ENTRY_EXIT_PAIR";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final PaTeacherAnalyticsService teacherAnalyticsService;
    private final PaReportAnalysisService reportAnalysisService;
    private final PaReportStudentResultRepository studentResultRepository;
    private final PaReportTaskResultRepository taskResultRepository;

    @Override
    public byte[] generateTeacherDossier(String academicYear, String teacherFio) throws IOException {
        PaAnalyticsDtos.TeacherDetailsResponse details = teacherAnalyticsService.getTeacherDetails(academicYear, teacherFio);
        if (details.teacherSummary() == null || details.reports() == null || details.reports().isEmpty()) {
            throw new IllegalArgumentException("Нет проанализированных работ ПА для педагога: " + teacherFio);
        }

        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addTitle(document, "Досье педагогической результативности ВСОКО / ПА");
            addTeacherPassport(document, academicYear, details.teacherSummary());
            addParagraph(document, "Показатель ПА рассчитан по результатам загруженных и проанализированных работ ПА. Он не является полной динамикой ВСОКО при отсутствии входной диагностики.", true, false);

            addPageBreak(document);
            addHeading(document, "Работы педагога", 1);
            addReportsTable(document, details.reports());
            addReportsConclusion(document, details.reports());

            for (PaAnalyticsDtos.TeacherReportDetailRow report : details.reports()) {
                addPageBreak(document);
                addReportSection(document, report);
            }

            document.write(out);
            return out.toByteArray();
        }
    }

    private void addTeacherPassport(XWPFDocument document, String academicYear, PaAnalyticsDtos.TeacherSummaryRow summary) {
        XWPFTable table = document.createTable(14, 2);
        fillRow(table.getRow(0), "Учебный год", academicYear);
        fillRow(table.getRow(1), "Педагог", summary.teacherFio());
        fillRow(table.getRow(2), "Предметы", join(summary.subjects()));
        fillRow(table.getRow(3), "Классы", join(summary.classes()));
        fillRow(table.getRow(4), "Количество работ", summary.reportsCount());
        fillRow(table.getRow(5), "Количество обучающихся с результатом", summary.studentsWithResult());
        fillRow(table.getRow(6), "Средний процент", percent(summary.avgPercent()));
        fillRow(table.getRow(7), "Средняя отметка", number(summary.avgMark(), 2));
        fillRow(table.getRow(8), "Успеваемость", percent(summary.successPercent()));
        fillRow(table.getRow(9), "Качество", percent(summary.qualityPercent()));
        fillRow(table.getRow(10), "Показатель ПА", number(summary.paPerformanceScore(), 2));
        fillRow(table.getRow(11), "Оценка ПА", summary.paPerformanceMark());
        fillRow(table.getRow(12), "Динамика ВСОКО", summary.vsokoDynamicScore() == null ? "Не рассчитана" : number(summary.vsokoDynamicScore(), 2));
        fillRow(table.getRow(13), "Статус динамики ВСОКО", dynamicStatus(summary.vsokoDynamicStatus()));
    }

    private void addReportsTable(XWPFDocument document, List<PaAnalyticsDtos.TeacherReportDetailRow> reports) {
        String[] headers = {"№", "Предмет", "Класс", "Тип работы", "Дата", "Уровень", "С результатом", "Средний %", "Средняя отметка", "Успеваемость", "Качество", "Пробл. задания", "Пробл. темы", "Проверка", "Статус"};
        XWPFTable table = document.createTable(reports.size() + 1, headers.length);
        fillHeader(table.getRow(0), headers);
        for (int i = 0; i < reports.size(); i++) {
            PaAnalyticsDtos.TeacherReportDetailRow report = reports.get(i);
            fillCells(table.getRow(i + 1),
                    i + 1,
                    report.subjectName(),
                    report.className(),
                    report.workType(),
                    date(report),
                    report.level(),
                    report.studentsWithResult(),
                    percent(report.avgPercent()),
                    number(report.avgMark(), 2),
                    percent(report.successPercent()),
                    percent(report.qualityPercent()),
                    report.problemTasksCount(),
                    report.problemTopicsCount(),
                    yesNo(report.needsReview()),
                    report.analysisStatus());
        }
    }

    private void addReportsConclusion(XWPFDocument document, List<PaAnalyticsDtos.TeacherReportDetailRow> reports) {
        long needsReview = reports.stream().filter(report -> Boolean.TRUE.equals(report.needsReview())).count();
        PaAnalyticsDtos.TeacherReportDetailRow lowestPercent = reports.stream()
                .filter(report -> report.avgPercent() != null)
                .min(Comparator.comparing(PaAnalyticsDtos.TeacherReportDetailRow::avgPercent))
                .orElse(null);
        PaAnalyticsDtos.TeacherReportDetailRow mostProblemTasks = reports.stream()
                .max(Comparator.comparing(report -> safeInt(report.problemTasksCount())))
                .orElse(null);
        addParagraph(document, "Краткий вывод:", true, true);
        addParagraph(document, "Проанализировано работ: " + reports.size() + ". Требуют проверки: " + needsReview + ".", false, false);
        if (lowestPercent != null) {
            addParagraph(document, "Наиболее низкий средний процент: " + workLabel(lowestPercent) + " — " + percent(lowestPercent.avgPercent()) + ".", false, false);
        }
        if (mostProblemTasks != null) {
            addParagraph(document, "Больше всего проблемных заданий: " + workLabel(mostProblemTasks) + " — " + safeInt(mostProblemTasks.problemTasksCount()) + ".", false, false);
        }
    }

    private void addReportSection(XWPFDocument document, PaAnalyticsDtos.TeacherReportDetailRow report) {
        PaAnalyticsDtos.ReportAnalysisDetails details = reportAnalysisService.getDetails(report.reportVersionId());
        List<PaAnalyticsDtos.StudentResultRow> students = details.students() == null ? List.of() : details.students();
        List<PaAnalyticsDtos.TaskResultRow> tasks = details.tasks() == null ? List.of() : details.tasks();
        List<PaReportStudentResult> studentEntities = studentResultRepository.findAllByReportVersionIdOrderByStudentFioAsc(report.reportVersionId());
        Map<Long, String> problemTasksByStudent = findProblemTasksByStudent(report.reportVersionId());
        addHeading(document, "Работа: " + workLabel(report), 1);
        addWorkSummary(document, report, details.summary(), students);
        addHeading(document, "Змейка учеников", 2);
        addStudentSnake(document, students);
        addHeading(document, "Задания", 2);
        addTasksTable(document, tasks);
        addHeading(document, "Ученики", 2);
        addStudentsTable(document, studentEntities, students, problemTasksByStudent);
    }

    private void addWorkSummary(XWPFDocument document,
                                PaAnalyticsDtos.TeacherReportDetailRow report,
                                PaAnalyticsDtos.ReportAnalysisListItem summary,
                                List<PaAnalyticsDtos.StudentResultRow> students) {
        XWPFTable table = document.createTable(12, 2);
        fillRow(table.getRow(0), "Учеников всего", summary == null ? report.studentsTotal() : summary.studentsTotal());
        fillRow(table.getRow(1), "С результатом", report.studentsWithResult());
        fillRow(table.getRow(2), "Отсутствовали", countByRowStatus(students, "ABSENT"));
        fillRow(table.getRow(3), "Пустые", countByRowStatus(students, "EMPTY_RESULT"));
        fillRow(table.getRow(4), "Возможные чужие подгруппы", countByRowStatus(students, "POSSIBLE_OTHER_SUBGROUP"));
        fillRow(table.getRow(5), "Средний процент", percent(report.avgPercent()));
        fillRow(table.getRow(6), "Средняя отметка", number(report.avgMark(), 2));
        fillRow(table.getRow(7), "Успеваемость", percent(report.successPercent()));
        fillRow(table.getRow(8), "Качество", percent(report.qualityPercent()));
        fillRow(table.getRow(9), "Проблемные задания", report.problemTasksCount());
        fillRow(table.getRow(10), "Проблемные темы", report.problemTopicsCount());
        fillRow(table.getRow(11), "Требуется проверка", yesNo(report.needsReview()));
    }

    private void addStudentSnake(XWPFDocument document, List<PaAnalyticsDtos.StudentResultRow> students) {
        int columns = 4;
        int rows = Math.max(1, (int) Math.ceil(students.size() / (double) columns));
        XWPFTable table = document.createTable(rows, columns);
        for (int i = 0; i < rows * columns; i++) {
            XWPFTableCell cell = table.getRow(i / columns).getCell(i % columns);
            if (i >= students.size()) {
                setCellText(cell, "");
                continue;
            }
            PaAnalyticsDtos.StudentResultRow student = students.get(i);
            setCellText(cell, shortFio(student.studentFio()) + "\n" + percent(student.percent()) + " · " + value(student.mark()) + "\n" + snakeStatus(student));
            cell.setColor(snakeColor(student));
        }
    }

    private void addTasksTable(XWPFDocument document, List<PaAnalyticsDtos.TaskResultRow> tasks) {
        String[] headers = {"№", "Тема", "Навык", "Тип", "Макс.", "Средний балл", "Средний %", "Ниже 50%", "Пусто", "Статус"};
        XWPFTable table = document.createTable(Math.max(1, tasks.size()) + 1, headers.length);
        fillHeader(table.getRow(0), headers);
        for (int i = 0; i < tasks.size(); i++) {
            PaAnalyticsDtos.TaskResultRow task = tasks.get(i);
            fillCells(table.getRow(i + 1), task.taskNo(), task.topic(), task.skill(), task.taskKind(), number(task.maxScore(), 2), number(task.avgScore(), 2), percent(task.avgPercent()), task.below50Count(), task.emptyCount(), task.status());
        }
    }

    private void addStudentsTable(XWPFDocument document,
                                  List<PaReportStudentResult> studentEntities,
                                  List<PaAnalyticsDtos.StudentResultRow> students,
                                  Map<Long, String> problemTasksByStudent) {
        String[] headers = {"№", "ФИО", "Присутствие", "Вариант", "Итог", "Макс.", "%", "Отметка", "Статус строки", "Проблемные задания ученика"};
        int rowsCount = studentEntities.isEmpty() ? students.size() : studentEntities.size();
        XWPFTable table = document.createTable(Math.max(1, rowsCount) + 1, headers.length);
        fillHeader(table.getRow(0), headers);
        if (!studentEntities.isEmpty()) {
            for (int i = 0; i < studentEntities.size(); i++) {
                PaReportStudentResult student = studentEntities.get(i);
                fillCells(table.getRow(i + 1),
                        i + 1,
                        student.getStudentFio(),
                        student.getPresenceStatus(),
                        student.getVariantName(),
                        number(student.getTotalScore(), 2),
                        number(student.getMaxScore(), 2),
                        percent(student.getPercent()),
                        student.getMark(),
                        rowStatus(student.getRowStatus() == null ? null : student.getRowStatus().name()),
                        problemTasksByStudent.getOrDefault(student.getId(), "—"));
            }
            return;
        }
        for (int i = 0; i < students.size(); i++) {
            PaAnalyticsDtos.StudentResultRow student = students.get(i);
            fillCells(table.getRow(i + 1), i + 1, student.studentFio(), student.presenceStatus(), student.variantName(), number(student.totalScore(), 2), number(student.maxScore(), 2), percent(student.percent()), student.mark(), rowStatus(student.rowStatus() == null ? null : student.rowStatus().name()), "—");
        }
    }

    private Map<Long, String> findProblemTasksByStudent(Long reportVersionId) {
        return taskResultRepository.findAllByReportVersionIdOrderByTaskNoAsc(reportVersionId).stream()
                .filter(task -> task.getStudentResultId() != null)
                .filter(task -> !task.isEmpty())
                .filter(task -> task.getPercent() != null && task.getPercent() < 50D)
                .collect(Collectors.groupingBy(
                        PaReportTaskResult::getStudentResultId,
                        LinkedHashMap::new,
                        Collectors.mapping(task -> value(task.getTaskNo()), Collectors.joining(", "))));
    }

    private void addTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(text);
    }

    private void addHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(level == 1 ? 15 : 13);
        run.setText(text);
    }

    private void addParagraph(XWPFDocument document, String text, boolean italic, boolean bold) {
        XWPFRun run = document.createParagraph().createRun();
        run.setItalic(italic);
        run.setBold(bold);
        run.setText(text);
    }

    private void addPageBreak(XWPFDocument document) {
        XWPFRun run = document.createParagraph().createRun();
        run.addBreak(BreakType.PAGE);
    }

    private void fillHeader(XWPFTableRow row, String... values) {
        fillCells(row, (Object[]) values);
        for (int i = 0; i < values.length; i++) {
            row.getCell(i).setColor("D9EAF7");
        }
    }

    private void fillRow(XWPFTableRow row, String label, Object value) {
        fillCells(row, label, value(value));
        row.getCell(0).setColor("F2F2F2");
    }

    private void fillCells(XWPFTableRow row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            setCellText(row.getCell(i), value(values[i]));
        }
    }

    private void setCellText(XWPFTableCell cell, String text) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setVerticalAlignment(TextAlignment.TOP);
        XWPFRun run = paragraph.createRun();
        String[] lines = value(text).split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i]);
        }
    }

    private String workLabel(PaAnalyticsDtos.TeacherReportDetailRow report) {
        return value(report.subjectName()) + ", " + value(report.className()) + ", " + value(report.workType()) + ", " + date(report);
    }

    private String shortFio(String fio) {
        String[] parts = value(fio).trim().split("\\s+");
        if (parts.length < 2) {
            return value(fio);
        }
        return parts[0] + " " + parts[1].charAt(0) + "." + (parts.length > 2 ? parts[2].charAt(0) + "." : "");
    }

    private String snakeStatus(PaAnalyticsDtos.StudentResultRow student) {
        if (student.percent() == null) {
            return "нет результата";
        }
        if (student.percent() >= 70D) {
            return "норма";
        }
        if (student.percent() >= 50D) {
            return "зона внимания";
        }
        return "проблема";
    }

    private String snakeColor(PaAnalyticsDtos.StudentResultRow student) {
        if (student.percent() == null || student.rowStatus() == null || !"PRESENT_WITH_RESULT".equals(student.rowStatus().name())) {
            return "E5E7EB";
        }
        if (student.percent() >= 70D) {
            return "DCFCE7";
        }
        if (student.percent() >= 50D) {
            return "FEF9C3";
        }
        return "FEE2E2";
    }

    private long countByRowStatus(List<PaAnalyticsDtos.StudentResultRow> students, String status) {
        if (students == null) {
            return 0;
        }
        return students.stream()
                .filter(student -> student.rowStatus() != null && status.equals(student.rowStatus().name()))
                .count();
    }

    private String date(PaAnalyticsDtos.TeacherReportDetailRow report) {
        return report.workDate() == null ? "—" : report.workDate().format(DATE_FORMATTER);
    }

    private String dynamicStatus(String status) {
        if (DYNAMIC_NOT_AVAILABLE.equals(status)) {
            return "Динамика ВСОКО не рассчитана: отсутствует входная/выходная пара.";
        }
        return value(status);
    }

    private String rowStatus(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case "PRESENT_WITH_RESULT" -> "Есть результат";
            case "ABSENT" -> "Отсутствовал";
            case "EMPTY_RESULT" -> "Пустой результат";
            case "POSSIBLE_OTHER_SUBGROUP" -> "Возможна другая подгруппа";
            case "INVALID_ROW" -> "Некорректная строка";
            default -> status;
        };
    }

    private String percent(Double value) {
        return value == null ? "—" : String.format(java.util.Locale.forLanguageTag("ru"), "%.1f%%", value);
    }

    private String number(Double value, int scale) {
        return value == null ? "—" : String.format(java.util.Locale.forLanguageTag("ru"), "% ." + scale + "f", value).trim();
    }

    private String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Да" : "Нет";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? "—" : String.join(", ", values);
    }

    private String value(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }
}
