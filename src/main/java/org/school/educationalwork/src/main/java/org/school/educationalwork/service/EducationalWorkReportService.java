package org.school.educationalwork.src.main.java.org.school.educationalwork.service;

import org.school.educationalwork.dto.EducationalWorkDtos;
import org.school.educationalwork.model.ClassTeacherReport;
import org.school.educationalwork.model.ValidationIssue;
import org.school.educationalwork.model.ValidationResult;
import org.school.educationalwork.parser.ClassNameNormalizer;
import org.school.educationalwork.parser.ClassTeacherReportParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class EducationalWorkReportService {
    private final ClassTeacherReportParser parser = new ClassTeacherReportParser();
    private final ClassNameNormalizer classNameNormalizer = new ClassNameNormalizer();
    private final Map<String, StoredReport> acceptedReports = new ConcurrentHashMap<>();

    public EducationalWorkDtos.UploadResponse validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return new EducationalWorkDtos.UploadResponse(false, null, List.of(
                    ValidationIssue.error("FILE_EMPTY", "Файл", "Файл не передан.", "Заполненный DOCX-отчёт", "")));
        }
        if (!Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase().endsWith(".docx")) {
            return new EducationalWorkDtos.UploadResponse(false, null, List.of(
                    ValidationIssue.error("FILE_TYPE", "Файл", "Неподдерживаемый формат файла.", ".docx", file.getOriginalFilename())));
        }
        ValidationResult<ClassTeacherReport> parsed = parser.parse(file.getInputStream());
        return new EducationalWorkDtos.UploadResponse(parsed.valid(), parsed.data(), parsed.issues());
    }

    public EducationalWorkDtos.UploadResponse submit(MultipartFile file) throws IOException {
        EducationalWorkDtos.UploadResponse response = validate(file);
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

    public EducationalWorkDtos.SchoolSummary summary(String academicYear, List<EducationalWorkDtos.ExpectedClass> expectedClasses) {
        Map<String, EducationalWorkDtos.ExpectedClass> expected = expectedClasses.stream()
                .map(item -> new EducationalWorkDtos.ExpectedClass(classNameNormalizer.normalize(item.schoolClass()).orElse(item.schoolClass()), item.classTeacherFullName()))
                .collect(Collectors.toMap(EducationalWorkDtos.ExpectedClass::schoolClass, item -> item, (a, b) -> a, LinkedHashMap::new));
        List<EducationalWorkDtos.SubmissionRow> submissions = new ArrayList<>();
        int number = 1;
        for (EducationalWorkDtos.ExpectedClass expectedClass : expected.values()) {
            boolean submitted = acceptedReports.containsKey(key(academicYear, expectedClass.schoolClass()));
            String url = submitted ? "/api/educational-work/reports/" + academicYear + "/" + expectedClass.schoolClass() + "/file" : null;
            submissions.add(new EducationalWorkDtos.SubmissionRow(number++, expectedClass.schoolClass(), expectedClass.classTeacherFullName(), submitted, url));
        }
        submissions.sort(Comparator.comparing(EducationalWorkDtos.SubmissionRow::schoolClass, this::compareClasses));
        return new EducationalWorkDtos.SchoolSummary(buildMatrix(expected.keySet(), academicYear), submissions, aggregate(academicYear, expected.size()));
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
        List<ClassTeacherReport> reports = acceptedReports.values().stream().map(StoredReport::report)
                .filter(report -> report.academicYear().equals(academicYear)).toList();
        return new EducationalWorkDtos.SchoolAggregate(reports.size(), expectedCount,
                reports.stream().mapToInt(r -> r.performance().studentCount()).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().gto())).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().movementFirst())).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().volunteers())).sum(),
                reports.stream().mapToInt(r -> value(r.activityCounters().studentCouncil())).sum(),
                reports.stream().mapToInt(r -> r.studentAchievements().size()).sum(),
                reports.stream().mapToInt(r -> r.specialProjects().size()).sum());
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private String key(String year, String schoolClass) { return year + "|" + schoolClass; }
    private int compareClasses(String a, String b) {
        int ag = Integer.parseInt(a.replaceAll("\\D", ""));
        int bg = Integer.parseInt(b.replaceAll("\\D", ""));
        int compare = Integer.compare(ag, bg);
        return compare != 0 ? compare : a.compareTo(b);
    }

    public record StoredReport(ClassTeacherReport report, String fileName, byte[] bytes) {}
}
