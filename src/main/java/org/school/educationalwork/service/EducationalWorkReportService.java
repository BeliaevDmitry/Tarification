package org.school.educationalwork.service;

import lombok.RequiredArgsConstructor;
import org.school.educationalwork.dto.EducationalWorkDtos;
import org.school.educationalwork.model.ClassTeacherReport;
import org.school.educationalwork.model.ValidationIssue;
import org.school.educationalwork.model.ValidationResult;
import org.school.educationalwork.parser.ClassNameNormalizer;
import org.school.educationalwork.parser.ClassTeacherReportParser;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EducationalWorkReportService {
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final ClassTeacherReportParser parser = new ClassTeacherReportParser();
    private final ClassNameNormalizer classNameNormalizer = new ClassNameNormalizer();
    private final Map<String, StoredReport> acceptedReports = new ConcurrentHashMap<>();

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

    public EducationalWorkDtos.UploadResponse submit(String academicYear, MultipartFile file) throws IOException {
        EducationalWorkDtos.UploadResponse response = validate(academicYear, file);
        if (response.accepted() && response.report() != null) {
            ClassTeacherReport report = response.report();
            acceptedReports.put(key(report.academicYear(), report.schoolClass()),
                    new StoredReport(report, file.getOriginalFilename(), file.getBytes()));
        }
        return response;
    }

    public Optional<StoredReport> file(String academicYear, String schoolClass) {
        return classNameNormalizer.normalize(schoolClass).flatMap(c -> Optional.ofNullable(acceptedReports.get(key(academicYear, c))));
    }

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
        List<EducationalWorkDtos.SubmissionRow> submissions = new ArrayList<>();
        int number = 1;
        for (EducationalWorkDtos.ExpectedClass expectedClass : expected.values()) {
            boolean submitted = acceptedReports.containsKey(key(academicYear, expectedClass.schoolClass()));
            String url = submitted ? "/api/educational-work/reports/" + academicYear + "/" + expectedClass.schoolClass() + "/file" : null;
            submissions.add(new EducationalWorkDtos.SubmissionRow(number++, expectedClass.schoolClass(), expectedClass.classTeacherFullName(), submitted, url));
        }
        submissions.sort(Comparator.comparing(EducationalWorkDtos.SubmissionRow::schoolClass, this::compareClasses));
        return new EducationalWorkDtos.SchoolSummary(buildMatrix(expected.keySet(), academicYear),
                buildBuildingSummaries(academicYear, directoryRows), submissions,
                aggregate(academicYear, expected.size()), reportTables(academicYear));
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

    private List<EducationalWorkDtos.BuildingSummary> buildBuildingSummaries(String academicYear, List<ClassroomLeadershipEntry> directoryRows) {
        Map<String, Set<String>> classesByBuilding = directoryRows.stream()
                .collect(Collectors.groupingBy(row -> Optional.ofNullable(row.getNumberSchoolBuilding()).orElse("Без СП"),
                        LinkedHashMap::new,
                        Collectors.mapping(row -> classNameNormalizer.normalize(row.getClassName()).orElse(row.getClassName()),
                                Collectors.toCollection(TreeSet::new))));
        return classesByBuilding.entrySet().stream()
                .map(entry -> new EducationalWorkDtos.BuildingSummary(entry.getKey(), buildMatrix(entry.getValue(), academicYear)))
                .toList();
    }

    private List<EducationalWorkDtos.ParallelRow> buildMatrix(Set<String> expectedClasses, String academicYear) {
        Set<Character> letters = expectedClasses.stream().filter(c -> c.length() > 1).map(c -> c.charAt(c.length() - 1)).collect(Collectors.toCollection(TreeSet::new));
        List<EducationalWorkDtos.ParallelRow> matrix = new ArrayList<>();
        for (int grade = 1; grade <= 11; grade++) {
            List<EducationalWorkDtos.MatrixCell> cells = new ArrayList<>();
            for (char letter : letters) {
                String klass = grade + String.valueOf(letter);
                EducationalWorkDtos.Status status = !expectedClasses.contains(klass)
                        ? EducationalWorkDtos.Status.CLASS_NOT_EXISTS
                        : acceptedReports.containsKey(key(academicYear, klass))
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
        return acceptedReports.values().stream().map(StoredReport::report)
                .filter(report -> report.academicYear().equals(academicYear))
                .sorted(Comparator.comparing(ClassTeacherReport::schoolClass, this::compareClasses))
                .toList();
    }

    private ClassTeacherReport withAcademicYear(ClassTeacherReport report, String academicYear) {
        if (academicYear.equals(report.academicYear())) return report;
        return new ClassTeacherReport(academicYear, report.schoolClass(), report.teacherFullName(), report.performance(),
                report.academicDebts(), report.additionalEducation(), report.activityCounters(), report.studentAchievements(),
                report.specialProjects(), report.teacherPortfolio(), report.staffRecognitions(), report.diagnostics());
    }

    private boolean hasNoErrors(List<ValidationIssue> issues) {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private String key(String year, String schoolClass) { return year + "|" + schoolClass; }
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
