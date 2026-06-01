package org.school.educationalwork.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.educationalwork.dto.EducationalWorkDtos;
import org.school.educationalwork.model.ClassTeacherReport;
import org.school.educationalwork.model.EducationalWorkReportEntity;
import org.school.educationalwork.model.ValidationIssue;
import org.school.educationalwork.model.ValidationResult;
import org.school.educationalwork.parser.ClassNameNormalizer;
import org.school.educationalwork.parser.ClassTeacherReportParser;
import org.school.educationalwork.repository.EducationalWorkReportRepository;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EducationalWorkReportService {
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final EducationalWorkReportRepository reportRepository;
    private final ObjectMapper objectMapper;
    private final ClassTeacherReportParser parser = new ClassTeacherReportParser();
    private final ClassNameNormalizer classNameNormalizer = new ClassNameNormalizer();

    public EducationalWorkDtos.UploadResponse validate(String academicYear, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return new EducationalWorkDtos.UploadResponse(false, null, List.of(
                    ValidationIssue.error("FILE_EMPTY", "Файл", "Файл не передан.", "Заполненный DOCX-отчёт", "")));
        }
        if (!Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return new EducationalWorkDtos.UploadResponse(false, null, List.of(
                    ValidationIssue.error("FILE_TYPE", "Файл", "Неподдерживаемый формат файла.", ".docx", file.getOriginalFilename())));
        }
        ValidationResult<ClassTeacherReport> parsed = parser.parse(file.getInputStream());
        List<ValidationIssue> issues = new ArrayList<>(parsed.issues());
        ClassTeacherReport report = parsed.data();
        String effectiveYear = normalizeYear(academicYear, report == null ? null : report.academicYear());
        if (report != null) {
            validateAgainstDirectory(effectiveYear, report, issues);
            report = withAcademicYear(report, effectiveYear);
        }
        return new EducationalWorkDtos.UploadResponse(hasNoErrors(issues), report, issues);
    }

    @Transactional
    public EducationalWorkDtos.UploadResponse submit(String academicYear, MultipartFile file) throws IOException {
        EducationalWorkDtos.UploadResponse response = validate(academicYear, file);
        if (response.accepted() && response.report() != null) {
            ClassTeacherReport report = response.report();
            EducationalWorkReportEntity entity = reportRepository
                    .findByAcademicYearAndSchoolClass(report.academicYear(), report.schoolClass())
                    .orElseGet(EducationalWorkReportEntity::new);
            entity.setAcademicYear(report.academicYear());
            entity.setSchoolClass(report.schoolClass());
            entity.setTeacherFullName(report.teacherFullName());
            entity.setFileName(Optional.ofNullable(file.getOriginalFilename()).orElse(report.schoolClass() + ".docx"));
            entity.setFileBytes(file.getBytes());
            entity.setReportJson(toJson(report));
            reportRepository.save(entity);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public Optional<StoredReport> file(String academicYear, String schoolClass) {
        return classNameNormalizer.normalize(schoolClass)
                .flatMap(c -> reportRepository.findByAcademicYearAndSchoolClass(academicYear, c))
                .map(entity -> new StoredReport(fromJson(entity.getReportJson()), entity.getFileName(), entity.getFileBytes()));
    }

    @Transactional(readOnly = true)
    public EducationalWorkDtos.SchoolSummary summary(String academicYear) {
        List<ClassroomLeadershipEntry> directoryRows = classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .sorted(Comparator.comparing(ClassroomLeadershipEntry::getNumberSchoolBuilding, Comparator.nullsLast(this::compareText))
                        .thenComparing(ClassroomLeadershipEntry::getClassName, this::compareClasses))
                .toList();
        List<EducationalWorkDtos.ExpectedClass> expectedClasses = expectedClasses(directoryRows);
        Map<String, EducationalWorkDtos.ExpectedClass> expected = expectedClasses.stream()
                .map(item -> new EducationalWorkDtos.ExpectedClass(item.numberSchoolBuilding(),
                        classNameNormalizer.normalize(item.schoolClass()).orElse(item.schoolClass()),
                        item.classTeacherFullName()))
                .collect(Collectors.toMap(EducationalWorkDtos.ExpectedClass::schoolClass, item -> item, (a, b) -> a, LinkedHashMap::new));
        Set<String> submittedClasses = submittedClasses(academicYear);
        List<EducationalWorkDtos.SubmissionRow> submissions = new ArrayList<>();
        int number = 1;
        for (EducationalWorkDtos.ExpectedClass expectedClass : expected.values()) {
            boolean submitted = submittedClasses.contains(expectedClass.schoolClass());
            String url = submitted ? "/api/educational-work/reports/" + academicYear + "/" + expectedClass.schoolClass() + "/file" : null;
            submissions.add(new EducationalWorkDtos.SubmissionRow(number++, expectedClass.schoolClass(), expectedClass.classTeacherFullName(), submitted, url));
        }
        submissions.sort(Comparator.comparing(EducationalWorkDtos.SubmissionRow::schoolClass, this::compareClasses));
        return new EducationalWorkDtos.SchoolSummary(buildMatrix(expected.keySet(), submittedClasses),
                buildBuildingSummaries(directoryRows, submittedClasses), submissions,
                aggregate(academicYear, expected.size()), reportTables(academicYear));
    }

    @Transactional(readOnly = true)
    public byte[] exportIndicators(String academicYear) throws IOException {
        EducationalWorkDtos.SchoolSummary summary = summary(academicYear);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            writeSummarySheet(workbook, headerStyle, summary);
            writeRowsSheet(workbook, headerStyle, "Отчёты", List.of("№", "Класс", "ФИО классного", "Отчёт", "Ссылка"),
                    summary.submissions().stream()
                            .map(row -> row(row.number(), row.schoolClass(), row.classTeacherFullName(), row.submitted() ? "Сдан" : "Не сдан", row.downloadUrl() == null ? "" : row.downloadUrl()))
                            .toList());
            EducationalWorkDtos.ReportTables tables = summary.tables();
            writeRowsSheet(workbook, headerStyle, "Успеваемость", List.of("Класс", "Классный", "Учащихся", "5", "4/5", "Одна 3", "3/4", "Неуспевающие"),
                    tables.performance().stream().map(row -> row(row.schoolClass(), row.classTeacherFullName(), row.studentCount(), row.grade5(), row.grade4And5(), row.oneGrade3(), row.grade3And4(), row.failing())).toList());
            writeRowsSheet(workbook, headerStyle, "ДО", List.of("Класс", "ДО внутри, чел.", "ДО внутри, %", "ДО вне, чел.", "ДО вне, %", "Не посещают ДО"),
                    tables.additionalEducation().stream().map(row -> row(row.schoolClass(), value(row.insideCount()), value(row.insidePercent()), value(row.outsideCount()), value(row.outsidePercent()), value(row.noAdditionalEducationCount()))).toList());
            writeRowsSheet(workbook, headerStyle, "Активность", List.of("Класс", "ГТО", "Движение Первых", "Волонтёры", "Совет обучающихся"),
                    tables.activity().stream().map(row -> row(row.schoolClass(), value(row.gto()), value(row.movementFirst()), value(row.volunteers()), value(row.studentCouncil()))).toList());
            writeRowsSheet(workbook, headerStyle, "Задолженности", List.of("Класс", "ФИО обучающегося", "1 триместр", "2 триместр", "3 триместр", "Итог"),
                    tables.academicDebts().stream().map(row -> row(row.schoolClass(), row.studentName(), row.trimester1(), row.trimester2(), row.trimester3(), row.finalResult())).toList());
            writeRowsSheet(workbook, headerStyle, "Достижения", List.of("Класс", "Уровень", "Проект", "Номинация", "Ответственный", "Участники", "Призёры", "Победители"),
                    tables.studentAchievements().stream().map(row -> row(row.schoolClass(), row.level(), row.project(), row.nomination(), row.responsibleTeacher(), row.participants(), row.prizeWinners(), row.winners())).toList());
            writeRowsSheet(workbook, headerStyle, "Проекты", List.of("Класс", "Проект", "Классный", "Номинация/формат", "Участники", "Результат"),
                    tables.specialProjects().stream().map(row -> row(row.schoolClass(), row.project(), row.classTeacher(), row.nominationOrFormat(), row.students(), row.result())).toList());
            writeRowsSheet(workbook, headerStyle, "Портфолио", List.of("Класс", "Конкурсы", "Трансляция опыта", "Публикации", "Повышение квалификации"),
                    tables.teacherPortfolio().stream().map(row -> row(row.schoolClass(), row.professionalCompetitions(), row.experienceSharing(), row.publications(), row.professionalDevelopment())).toList());
            writeRowsSheet(workbook, headerStyle, "Педагоги", List.of("Класс", "ФИО", "Категория", "Награды"),
                    tables.staffRecognitions().stream().map(row -> row(row.schoolClass(), row.fullName(), row.category(), row.awards())).toList());
            writeRowsSheet(workbook, headerStyle, "Диагностики", List.of("Класс", "Название", "Результат", "Дата", "Опубликовано"),
                    tables.diagnostics().stream().map(row -> row(row.schoolClass(), row.name(), row.result(), row.date(), Boolean.TRUE.equals(row.published()) ? "+" : "-")).toList());
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private List<EducationalWorkDtos.ExpectedClass> expectedClasses(List<ClassroomLeadershipEntry> directoryRows) {
        return directoryRows.stream()
                .map(row -> new EducationalWorkDtos.ExpectedClass(row.getNumberSchoolBuilding(), row.getClassName(), row.getFioTeacher()))
                .toList();
    }

    private void validateAgainstDirectory(String academicYear, ClassTeacherReport report, List<ValidationIssue> issues) {
        Optional<String> normalizedClass = classNameNormalizer.normalize(report.schoolClass());
        if (normalizedClass.isEmpty()) return;
        Optional<ClassroomLeadershipEntry> expected = classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> normalizedClass.get().equals(classNameNormalizer.normalize(row.getClassName()).orElse(row.getClassName())))
                .findFirst();
        if (expected.isEmpty()) {
            issues.add(ValidationIssue.error("CLASS_NOT_FOUND", "Заголовок: класс",
                    "Класс из отчёта не найден в справочнике классов выбранного учебного года.",
                    "Один из классов раздела «Классы» за " + academicYear, report.schoolClass()));
            return;
        }
        String expectedTeacher = normalizePerson(expected.get().getFioTeacher());
        String actualTeacher = normalizePerson(report.teacherFullName());
        if (!expectedTeacher.equals(actualTeacher)) {
            issues.add(ValidationIssue.error("TEACHER_MISMATCH", "Заголовок: ФИО классного руководителя",
                    "ФИО классного руководителя не совпадает со справочником классов.",
                    expected.get().getFioTeacher(), report.teacherFullName()));
        }
    }

    private List<EducationalWorkDtos.BuildingSummary> buildBuildingSummaries(List<ClassroomLeadershipEntry> directoryRows, Set<String> submittedClasses) {
        Map<String, Set<String>> classesByBuilding = directoryRows.stream()
                .collect(Collectors.groupingBy(row -> Optional.ofNullable(row.getNumberSchoolBuilding()).orElse("Без СП"),
                        LinkedHashMap::new,
                        Collectors.mapping(row -> classNameNormalizer.normalize(row.getClassName()).orElse(row.getClassName()),
                                Collectors.toCollection(TreeSet::new))));
        return classesByBuilding.entrySet().stream()
                .map(entry -> new EducationalWorkDtos.BuildingSummary(entry.getKey(), buildMatrix(entry.getValue(), submittedClasses)))
                .toList();
    }

    private List<EducationalWorkDtos.ParallelRow> buildMatrix(Set<String> expectedClasses, Set<String> submittedClasses) {
        Set<Character> letters = expectedClasses.stream().filter(c -> c.length() > 1).map(c -> c.charAt(c.length() - 1)).collect(Collectors.toCollection(TreeSet::new));
        List<EducationalWorkDtos.ParallelRow> matrix = new ArrayList<>();
        for (int grade = 1; grade <= 11; grade++) {
            List<EducationalWorkDtos.MatrixCell> cells = new ArrayList<>();
            for (char letter : letters) {
                String klass = grade + String.valueOf(letter);
                EducationalWorkDtos.Status status = !expectedClasses.contains(klass)
                        ? EducationalWorkDtos.Status.CLASS_NOT_EXISTS
                        : submittedClasses.contains(klass)
                        ? EducationalWorkDtos.Status.SUBMITTED : EducationalWorkDtos.Status.NOT_SUBMITTED;
                cells.add(new EducationalWorkDtos.MatrixCell(klass, status));
            }
            matrix.add(new EducationalWorkDtos.ParallelRow(grade, cells));
        }
        return matrix;
    }

    private EducationalWorkDtos.SchoolAggregate aggregate(String academicYear, int expectedCount) {
        List<ClassTeacherReport> reports = reports(academicYear);
        return new EducationalWorkDtos.SchoolAggregate(reports.size(), expectedCount,
                reports.stream().mapToInt(r -> r.performance().studentCount()).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().gto())).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().movementFirst())).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().volunteers())).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().studentCouncil())).sum(),
                reports.stream().mapToInt(r -> r.studentAchievements().size()).sum(),
                reports.stream().mapToInt(r -> r.specialProjects().size()).sum());
    }

    private EducationalWorkDtos.ReportTables reportTables(String academicYear) {
        List<ClassTeacherReport> reports = reports(academicYear);
        List<EducationalWorkDtos.PerformanceRow> performance = new ArrayList<>();
        List<EducationalWorkDtos.AdditionalEducationRow> additionalEducation = new ArrayList<>();
        List<EducationalWorkDtos.ActivityRow> activity = new ArrayList<>();
        List<EducationalWorkDtos.AcademicDebtRow> academicDebts = new ArrayList<>();
        List<EducationalWorkDtos.StudentAchievementRow> achievements = new ArrayList<>();
        List<EducationalWorkDtos.SpecialProjectRow> projects = new ArrayList<>();
        List<EducationalWorkDtos.TeacherPortfolioRow> portfolio = new ArrayList<>();
        List<EducationalWorkDtos.StaffRecognitionRow> recognitions = new ArrayList<>();
        List<EducationalWorkDtos.DiagnosticRow> diagnostics = new ArrayList<>();
        for (ClassTeacherReport report : reports) {
            String c = report.schoolClass();
            ClassTeacherReport.AcademicPerformance p = report.performance();
            performance.add(new EducationalWorkDtos.PerformanceRow(c, report.teacherFullName(), p.studentCount(), p.grade5().size(), p.grade4And5().size(), p.oneGrade3().size(), p.grade3And4().size(), p.failing().size(), join(p.grade5()), join(p.grade4And5()), join(p.oneGrade3()), join(p.grade3And4()), join(p.failing())));
            ClassTeacherReport.AdditionalEducation add = report.additionalEducation();
            additionalEducation.add(new EducationalWorkDtos.AdditionalEducationRow(c, add.insideCount(), add.insidePercent(), add.outsideCount(), add.outsidePercent(), add.noAdditionalEducationCount()));
            ClassTeacherReport.ActivityCounters counters = report.activityCounters();
            activity.add(new EducationalWorkDtos.ActivityRow(c, counters.gto(), counters.movementFirst(), counters.volunteers(), counters.studentCouncil()));
            report.academicDebts().forEach(row -> academicDebts.add(new EducationalWorkDtos.AcademicDebtRow(c, row.studentName(), row.trimester1(), row.trimester2(), row.trimester3(), row.finalResult())));
            report.studentAchievements().forEach(row -> achievements.add(new EducationalWorkDtos.StudentAchievementRow(c, row.level(), row.project(), row.nomination(), row.responsibleTeacher(), row.participants(), row.prizeWinners(), row.winners())));
            report.specialProjects().forEach(row -> projects.add(new EducationalWorkDtos.SpecialProjectRow(c, row.project(), row.classTeacher(), row.nominationOrFormat(), row.students(), row.result())));
            ClassTeacherReport.TeacherPortfolio tp = report.teacherPortfolio();
            portfolio.add(new EducationalWorkDtos.TeacherPortfolioRow(c, tp.professionalCompetitions(), tp.experienceSharing(), tp.publications(), tp.professionalDevelopment()));
            report.staffRecognitions().forEach(row -> recognitions.add(new EducationalWorkDtos.StaffRecognitionRow(c, row.fullName(), row.category(), row.awards())));
            report.diagnostics().forEach(row -> diagnostics.add(new EducationalWorkDtos.DiagnosticRow(c, row.name(), row.result(), row.date(), row.published())));
        }
        return new EducationalWorkDtos.ReportTables(performance, additionalEducation, activity, academicDebts, achievements, projects, portfolio, recognitions, diagnostics);
    }

    private List<ClassTeacherReport> reports(String academicYear) {
        return reportRepository.findAllByAcademicYear(academicYear).stream()
                .map(entity -> fromJson(entity.getReportJson()))
                .sorted(Comparator.comparing(ClassTeacherReport::schoolClass, this::compareClasses))
                .toList();
    }

    private Set<String> submittedClasses(String academicYear) {
        return reportRepository.findAllByAcademicYear(academicYear).stream()
                .map(EducationalWorkReportEntity::getSchoolClass)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private void writeSummarySheet(Workbook workbook, CellStyle headerStyle, EducationalWorkDtos.SchoolSummary summary) {
        Sheet sheet = workbook.createSheet("Свод");
        int rowIndex = 0;
        for (EducationalWorkDtos.BuildingSummary building : summary.buildingSummaries()) {
            Row title = sheet.createRow(rowIndex++);
            title.createCell(0).setCellValue(building.numberSchoolBuilding());
            title.getCell(0).setCellStyle(headerStyle);
            List<String> letters = building.matrix().isEmpty() ? List.of() : building.matrix().get(0).letters().stream()
                    .map(cell -> cell.schoolClass().replaceAll("\\d+", ""))
                    .toList();
            Row header = sheet.createRow(rowIndex++);
            createCell(header, 0, "Класс / литера", headerStyle);
            for (int i = 0; i < letters.size(); i++) {
                createCell(header, i + 1, letters.get(i), headerStyle);
            }
            for (EducationalWorkDtos.ParallelRow matrixRow : building.matrix()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(matrixRow.parallel());
                for (int i = 0; i < matrixRow.letters().size(); i++) {
                    EducationalWorkDtos.MatrixCell cell = matrixRow.letters().get(i);
                    row.createCell(i + 1).setCellValue(switch (cell.status()) {
                        case SUBMITTED -> "✓";
                        case NOT_SUBMITTED -> "✕";
                        case CLASS_NOT_EXISTS -> "";
                    });
                }
            }
            rowIndex++;
        }
        autosize(sheet, 20);
    }

    private List<?> row(Object... values) {
        return Arrays.asList(values);
    }

    private void writeRowsSheet(Workbook workbook, CellStyle headerStyle, String sheetName, List<String> headers, List<? extends List<?>> rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            createCell(header, i, headers.get(i), headerStyle);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<?> values = rows.get(rowIndex);
            for (int column = 0; column < values.size(); column++) {
                Object value = values.get(column);
                Cell cell = row.createCell(column);
                if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                } else {
                    cell.setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
        }
        autosize(sheet, headers.size());
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private ClassTeacherReport withAcademicYear(ClassTeacherReport report, String academicYear) {
        if (academicYear.equals(report.academicYear())) return report;
        return new ClassTeacherReport(academicYear, report.schoolClass(), report.teacherFullName(), report.performance(),
                report.academicDebts(), report.additionalEducation(), report.activityCounters(), report.studentAchievements(),
                report.specialProjects(), report.teacherPortfolio(), report.staffRecognitions(), report.diagnostics());
    }

    private String toJson(ClassTeacherReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить данные отчёта", e);
        }
    }

    private ClassTeacherReport fromJson(String reportJson) {
        try {
            return objectMapper.readValue(reportJson, ClassTeacherReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось прочитать сохранённые данные отчёта", e);
        }
    }

    private boolean hasNoErrors(List<ValidationIssue> issues) {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private String join(List<String> values) { return String.join(", ", values); }
    private String normalizeYear(String requested, String fallback) { return requested == null || requested.isBlank() ? fallback : requested; }
    private String normalizePerson(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.forLanguageTag("ru")); }

    private int compareText(String a, String b) {
        return java.text.Collator.getInstance(Locale.forLanguageTag("ru")).compare(String.valueOf(a), String.valueOf(b));
    }

    private int compareClasses(String a, String b) {
        int ag = classGrade(a);
        int bg = classGrade(b);
        int compare = Integer.compare(ag, bg);
        return compare != 0 ? compare : String.valueOf(a).compareTo(String.valueOf(b));
    }

    private int classGrade(String value) {
        String digits = String.valueOf(value).replaceAll("\\D", "");
        return digits.isBlank() ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    public record StoredReport(ClassTeacherReport report, String fileName, byte[] bytes) {}
}
