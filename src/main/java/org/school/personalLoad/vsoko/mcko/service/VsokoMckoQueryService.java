package org.school.personalLoad.vsoko.mcko.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentNameHistory;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.oge.model.OgeWorkResult;
import org.school.personalLoad.oge.repository.OgeWorkResultRepository;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.ProbeOrderService;
import org.school.personalLoad.vsoko.mcko.dto.VsokoMckoDtos;
import org.school.personalLoad.vsoko.mcko.model.*;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VsokoMckoQueryService {
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MckoStudentResultRepository resultRepository;
    private final MckoClassDiagnosticSummaryRepository classSummaryRepository;
    private final MckoImportFileRepository fileRepository;
    private final MckoTeacherClassAssignmentRepository assignmentRepository;
    private final StudentProfileRepository profileRepository;
    private final StudentNameHistoryRepository nameHistoryRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final ManualLoadEntryRepository manualLoadRepository;
    private final PaReportStudentResultRepository paResultRepository;
    private final OgeWorkResultRepository ogeResultRepository;
    private final StudentResultLinker studentResultLinker;
    @Autowired(required = false)
    private ProbeOrderService probeOrderService;

    @Transactional(readOnly = true)
    public List<VsokoMckoDtos.ResultRow> results(String academicYear,
                                                String className,
                                                String subject,
                                                String student,
                                                String linkStatus,
                                                String teacher,
                                                int limit) {
        Map<Long, String> fileNames = fileRepository.findAll().stream()
                .collect(Collectors.toMap(MckoImportFile::getId, MckoImportFile::getFileName, (a, b) -> a));
        String yearFilter = normalizeYear(academicYear);
        String classFilter = normalizeClass(className);
        String subjectFilter = normalize(subject);
        String studentFilter = StudentResultLinker.normalizeName(student);
        String teacherFilter = normalize(teacher);
        List<MckoStudentResult> source = yearFilter.isBlank()
                ? resultRepository.findAll()
                : resultRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc(yearFilter);
        return source.stream()
                .filter(row -> yearFilter.isBlank() || normalizeYear(row.getAcademicYear()).equals(yearFilter))
                .filter(row -> classFilter.isBlank() || normalizeClass(row.getClassName()).equals(classFilter))
                .filter(row -> subjectFilter.isBlank() || normalize(row.getSubjectName()).contains(subjectFilter))
                .filter(row -> studentFilter.isBlank() || StudentResultLinker.normalizeName(row.getStudentFioSnapshot()).contains(studentFilter))
                .filter(row -> blank(linkStatus).isBlank() || row.getStudentLinkStatus().name().equalsIgnoreCase(linkStatus))
                .filter(row -> teacherFilter.isBlank() || normalize(row.getTeacherFioSnapshot()).contains(teacherFilter))
                .sorted(Comparator.comparing(MckoStudentResult::getAcademicYear, Comparator.nullsLast(String::compareTo))
                        .thenComparing(MckoStudentResult::getClassName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(MckoStudentResult::getSubjectName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(MckoStudentResult::getStudentFioSnapshot, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(Math.max(1, Math.min(limit, 20_000)))
                .map(row -> toResultRow(row, fileNames.get(row.getSourceFileId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public VsokoMckoDtos.FilterOptions filters() {
        List<MckoStudentResult> rows = resultRepository.findAll();
        List<MckoClassDiagnosticSummary> summaries = classSummaryRepository.findAll();
        return new VsokoMckoDtos.FilterOptions(
                mergeDistinct(distinct(rows, MckoStudentResult::getAcademicYear),
                        summaries.stream().map(MckoClassDiagnosticSummary::getAcademicYear).toList()),
                mergeDistinct(distinct(rows, MckoStudentResult::getClassName),
                        summaries.stream().map(MckoClassDiagnosticSummary::getClassName).toList()),
                mergeDistinct(distinct(rows, MckoStudentResult::getSubjectName),
                        summaries.stream().map(MckoClassDiagnosticSummary::getSubjectName).toList()),
                distinct(rows, MckoStudentResult::getTeacherFioSnapshot),
                Arrays.stream(MckoStudentLinkStatus.values()).map(Enum::name).toList());
    }

    @Transactional
    public VsokoMckoDtos.ResultRow linkResult(Long resultId, Long studentId) {
        MckoStudentResult row = requireResult(resultId);
        StudentProfile student = profileRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена: " + studentId));
        row.setStudentId(student.getId());
        row.setStudentLinkStatus(MckoStudentLinkStatus.MANUALLY_LINKED);
        row.setStudentLinkMessage("Карточка выбрана пользователем");
        row.setStudentFioSnapshot(firstNonBlank(row.getStudentFioSnapshot(), student.getCurrentFullName()));
        resultRepository.save(row);
        String sourceFileName = row.getSourceFileId() == null
                ? null
                : fileRepository.findById(row.getSourceFileId()).map(MckoImportFile::getFileName).orElse(null);
        return toResultRow(row, sourceFileName);
    }

    @Transactional
    public VsokoMckoDtos.ReconcileResponse reconcile() {
        StudentResultLinker.LinkIndex index = studentResultLinker.buildIndex();
        int linked = 0;
        int ambiguous = 0;
        int notFound = 0;
        List<MckoStudentResult> changed = new ArrayList<>();
        for (MckoStudentResult row : resultRepository.findAll()) {
            if (row.getStudentLinkStatus() == MckoStudentLinkStatus.MANUALLY_LINKED && row.getStudentId() != null) continue;
            StudentResultLinker.LinkResult link = index.resolve(row.getStudentCode(), row.getStudentFioSnapshot(),
                    row.getAcademicYear(), row.getClassName());
            row.setStudentId(link.studentId());
            row.setStudentLinkStatus(link.status());
            row.setStudentLinkMessage(link.message());
            changed.add(row);
            if (link.studentId() != null) linked++;
            else if (link.status() == MckoStudentLinkStatus.AMBIGUOUS) ambiguous++;
            else notFound++;
        }
        resultRepository.saveAll(changed);
        relinkPa(index);
        relinkOge(index);
        return new VsokoMckoDtos.ReconcileResponse(linked, ambiguous, notFound);
    }

    @Transactional(readOnly = true)
    public List<VsokoMckoDtos.StudentSearchRow> searchStudents(String query, int limit) {
        String needle = StudentResultLinker.normalizeName(query);
        if (needle.length() < 2) return List.of();
        Map<Long, List<StudentNameHistory>> histories = nameHistoryRepository.findAll().stream()
                .filter(row -> row.getStudent() != null && row.getStudent().getId() != null)
                .collect(Collectors.groupingBy(row -> row.getStudent().getId()));
        Map<Long, String> currentClasses = currentClassByStudent();
        Map<Long, Long> mckoCounts = resultRepository.findAll().stream().filter(row -> row.getStudentId() != null)
                .collect(Collectors.groupingBy(MckoStudentResult::getStudentId, Collectors.counting()));
        return profileRepository.findAll().stream()
                .filter(profile -> matchesStudent(profile, histories.getOrDefault(profile.getId(), List.of()), needle))
                .sorted(Comparator.comparing(StudentProfile::getCurrentFullName, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(profile -> new VsokoMckoDtos.StudentSearchRow(profile.getId(), profile.getCurrentFullName(),
                        knownNames(profile, histories.getOrDefault(profile.getId(), List.of())), profile.getBirthDate(),
                        currentClasses.get(profile.getId()), Math.toIntExact(mckoCounts.getOrDefault(profile.getId(), 0L))))
                .toList();
    }

    @Transactional(readOnly = true)
    public VsokoMckoDtos.StudentSummary studentSummary(Long studentId) {
        StudentProfile profile = profileRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена: " + studentId));
        List<StudentNameHistory> histories = nameHistoryRepository.findAllByStudent_IdOrderByValidFromAsc(studentId);
        List<VsokoMckoDtos.TimelineRow> timeline = new ArrayList<>();
        for (MckoStudentResult row : resultRepository.findAllByStudentIdOrderByAcademicYearAscDiagnosticDateAsc(studentId)) {
            timeline.add(new VsokoMckoDtos.TimelineRow("МЦКО", row.getId(), row.getAcademicYear(), row.getClassName(),
                    row.getSubjectName(), row.getDiagnosticDate(), row.getResultType().name(), row.getScore(), null,
                    row.getPercent(), row.getMark(), row.getTeacherFioSnapshot(), row.getMasteryLevel()));
        }
        for (PaReportStudentResult row : paResultRepository.findAllByStudentIdOrderByAcademicYearAscIdAsc(studentId)) {
            timeline.add(new VsokoMckoDtos.TimelineRow("ПА", row.getId(), row.getAcademicYear(), row.getClassName(),
                    row.getSubjectName(), null, "ПА", row.getTotalScore(), row.getMaxScore(), row.getPercent(), row.getMark(),
                    row.getTeacherFio(), row.getRowStatus() == null ? null : row.getRowStatus().name()));
        }
        for (OgeWorkResult row : ogeResultRepository.findAllByStudentIdOrderByAcademicYearAscWorkDateAsc(studentId)) {
            timeline.add(new VsokoMckoDtos.TimelineRow("ОГЭ", row.getId(), row.getAcademicYear(), row.getClassName(),
                    row.getSubjectName(), parseDate(row.getWorkDate()), row.getWorkType(),
                    row.getTestScore() == null ? null : row.getTestScore().doubleValue(), null, null, row.getGrade(),
                    row.getTeacherFio(), row.getSourceIssue()));
        }
        timeline.sort(Comparator.comparing(VsokoMckoDtos.TimelineRow::academicYear, Comparator.nullsLast(String::compareTo))
                .thenComparing(VsokoMckoDtos.TimelineRow::date, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(VsokoMckoDtos.TimelineRow::subjectName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return new VsokoMckoDtos.StudentSummary(profile.getId(), profile.getCurrentFullName(), knownNames(profile, histories),
                profile.getBirthDate(), profile.getChildPhone(), profile.getRepresentativeName(), profile.getRepresentativePhone(),
                timeline, probeOrderService == null ? List.of() : probeOrderService.studentHistory(profile.getId()));
    }

    @Transactional(readOnly = true)
    public byte[] exportStudentSummary(Long studentId) throws Exception {
        VsokoMckoDtos.StudentSummary summary = studentSummary(studentId);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("История результатов");
            String[] headers = {"Учебный год", "Класс", "Источник", "Предмет", "Дата", "Тип работы", "Балл",
                    "Максимум", "Процент", "Оценка", "Педагог", "Статус"};
            writeHeader(sheet, headers);
            int rowNo = 1;
            for (VsokoMckoDtos.TimelineRow result : summary.results()) {
                Row row = sheet.createRow(rowNo++);
                values(row, result.academicYear(), result.className(), result.source(), result.subjectName(),
                        formatDate(result.date()), result.workType(), result.score(), result.maxScore(), result.percent(),
                        result.mark(), result.teacherFio(), result.status());
            }
            finishTable(sheet, headers.length, rowNo);
            Sheet card = workbook.createSheet("Карточка ребёнка");
            values(card.createRow(0), "ID карточки", summary.studentId());
            values(card.createRow(1), "Текущее ФИО", summary.currentFullName());
            values(card.createRow(2), "Известные ФИО", String.join("; ", summary.knownNames()));
            values(card.createRow(3), "Дата рождения", summary.birthDate());
            values(card.createRow(4), "Телефон ребёнка", summary.childPhone());
            values(card.createRow(5), "ФИО представителя", summary.representativeName());
            values(card.createRow(6), "Телефон представителя", summary.representativePhone());
            card.autoSizeColumn(0);
            card.setColumnWidth(1, 15000);
            return bytes(workbook);
        }
    }

    @Transactional(readOnly = true)
    public VsokoMckoDtos.ClassSummary classSummary(String academicYear, String className) {
        String year = normalizeYear(academicYear);
        String classKey = normalizeClass(className);
        List<MckoStudentResult> mcko = resultRepository
                .findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc(year).stream()
                .filter(row -> normalizeClass(row.getClassName()).equals(classKey)).toList();
        List<MckoClassDiagnosticSummary> summaries = classSummaryRepository.findAllByAcademicYear(year).stream()
                .filter(row -> normalizeClass(row.getClassName()).equals(classKey)).toList();
        List<PaReportStudentResult> pa = paResultRepository.findAllByAcademicYear(year).stream()
                .filter(row -> normalizeClass(row.getClassName()).equals(classKey))
                .filter(PaReportStudentResult::isHasResult).toList();
        Set<String> subjects = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        mcko.stream().map(MckoStudentResult::getSubjectName)
                .filter(value -> !blank(value).isBlank()).forEach(subjects::add);
        summaries.stream().map(MckoClassDiagnosticSummary::getSubjectName)
                .filter(value -> !blank(value).isBlank()).forEach(subjects::add);
        pa.stream().map(PaReportStudentResult::getSubjectName)
                .filter(value -> !blank(value).isBlank()).forEach(subjects::add);
        List<VsokoMckoDtos.ClassSubjectComparison> rows = new ArrayList<>();
        for (String subject : subjects) {
            List<MckoStudentResult> mr = mcko.stream().filter(row -> sameSubject(row.getSubjectName(), subject)).toList();
            List<MckoClassDiagnosticSummary> sr = summaries.stream()
                    .filter(row -> sameSubject(row.getSubjectName(), subject)).toList();
            List<PaReportStudentResult> pr = pa.stream().filter(row -> sameSubject(row.getSubjectName(), subject)).toList();
            int mckoCount = mr.isEmpty() ? sr.stream().map(MckoClassDiagnosticSummary::getParticipantCount)
                    .filter(Objects::nonNull).mapToInt(Integer::intValue).sum() : mr.size();
            Double mckoPercent = average(mr, MckoStudentResult::getPercent);
            if (mckoPercent == null) {
                mckoPercent = weightedSummaryAverage(sr, MckoClassDiagnosticSummary::getAveragePercent);
            }
            rows.add(new VsokoMckoDtos.ClassSubjectComparison(subject, mckoCount, mckoPercent,
                    average(mr, row -> row.getMark() == null ? null : row.getMark().doubleValue()), pr.size(),
                    average(pr, PaReportStudentResult::getPercent),
                    average(pr, row -> row.getMark() == null ? null : row.getMark().doubleValue())));
        }
        return new VsokoMckoDtos.ClassSummary(academicYear, className, rows);
    }

    @Transactional(readOnly = true)
    public byte[] exportClassSummary(String academicYear, String className) throws Exception {
        VsokoMckoDtos.ClassSummary summary = classSummary(academicYear, className);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Сравнение МЦКО и ПА");
            String[] headers = {"Учебный год", "Класс", "Предмет", "МЦКО: детей", "МЦКО: средний %",
                    "МЦКО: средняя оценка", "ПА: детей", "ПА: средний %", "ПА: средняя оценка"};
            writeHeader(sheet, headers);
            int rowNo = 1;
            for (VsokoMckoDtos.ClassSubjectComparison result : summary.subjects()) {
                Row row = sheet.createRow(rowNo++);
                values(row, summary.academicYear(), summary.className(), result.subjectName(), result.mckoCount(),
                        result.mckoAveragePercent(), result.mckoAverageMark(), result.paCount(),
                        result.paAveragePercent(), result.paAverageMark());
            }
            finishTable(sheet, headers.length, rowNo);
            return bytes(workbook);
        }
    }

    @Transactional(readOnly = true)
    public VsokoMckoDtos.ParallelSummary parallelSummary(String academicYear) {
        String year = normalizeYear(academicYear);
        List<MckoClassDiagnosticSummary> summaries = classSummaryRepository.findAllByAcademicYear(year).stream()
                .filter(row -> row.getAveragePercent() != null)
                .filter(row -> parallel(row.getClassName()) != null)
                .toList();
        List<MckoStudentResult> results = resultRepository
                .findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc(year).stream()
                .filter(row -> row.getPercent() != null)
                .filter(row -> resultParallel(row) != null)
                .toList();

        Map<String, String> subjectNames = new TreeMap<>();
        summaries.forEach(row -> putSubjectName(subjectNames, row.getSubjectName()));
        results.forEach(row -> putSubjectName(subjectNames, row.getSubjectName()));
        TreeSet<Integer> parallelNumbers = new TreeSet<>();
        summaries.forEach(row -> parallelNumbers.add(parallel(row.getClassName())));
        results.forEach(row -> parallelNumbers.add(resultParallel(row)));

        List<VsokoMckoDtos.ParallelSubjectRow> subjectRows = new ArrayList<>();
        for (Map.Entry<String, String> subjectEntry : subjectNames.entrySet()) {
            List<VsokoMckoDtos.ParallelSubjectCell> cells = new ArrayList<>();
            for (Integer parallelNumber : parallelNumbers) {
                List<MckoClassDiagnosticSummary> summaryRows = summaries.stream()
                        .filter(row -> parallelNumber.equals(parallel(row.getClassName())))
                        .filter(row -> normalizeSubject(row.getSubjectName()).equals(subjectEntry.getKey()))
                        .toList();
                List<MckoStudentResult> resultRows = results.stream()
                        .filter(row -> parallelNumber.equals(resultParallel(row)))
                        .filter(row -> normalizeSubject(row.getSubjectName()).equals(subjectEntry.getKey()))
                        .toList();
                Double schoolPercent = weightedSummaryAverage(summaryRows, MckoClassDiagnosticSummary::getAveragePercent);
                if (schoolPercent == null) schoolPercent = average(resultRows, MckoStudentResult::getPercent);
                if (schoolPercent == null) continue;
                Double cityPercent = weightedSummaryAverage(summaryRows, MckoClassDiagnosticSummary::getCityPercent);
                if (cityPercent == null) cityPercent = averagePercentText(resultRows, MckoStudentResult::getCityLevel);
                int participantCount = summaryRows.stream().map(MckoClassDiagnosticSummary::getParticipantCount)
                        .filter(Objects::nonNull).filter(count -> count > 0).mapToInt(Integer::intValue).sum();
                if (participantCount == 0) participantCount = resultRows.size();
                int diagnosticCount = summaryRows.isEmpty()
                        ? (int) resultRows.stream().map(this::diagnosticKey).distinct().count()
                        : summaryRows.size();
                Double difference = cityPercent == null ? null : roundPercent(schoolPercent - cityPercent);
                cells.add(new VsokoMckoDtos.ParallelSubjectCell(parallelNumber, participantCount, diagnosticCount,
                        schoolPercent, cityPercent, difference, cityComparison(difference)));
            }
            if (!cells.isEmpty()) {
                subjectRows.add(new VsokoMckoDtos.ParallelSubjectRow(subjectEntry.getValue(), cells));
            }
        }
        List<Integer> populatedParallels = subjectRows.stream().flatMap(row -> row.parallels().stream())
                .map(VsokoMckoDtos.ParallelSubjectCell::parallel).distinct().sorted().toList();
        return new VsokoMckoDtos.ParallelSummary(year, populatedParallels, subjectRows);
    }

    @Transactional(readOnly = true)
    public byte[] exportParallelSummary(String academicYear) throws Exception {
        VsokoMckoDtos.ParallelSummary summary = parallelSummary(academicYear);
        try (Workbook workbook = new XSSFWorkbook()) {
            Map<String, CellStyle> heatStyles = Map.of(
                    "ABOVE_CITY", heatStyle(workbook, IndexedColors.LIGHT_GREEN),
                    "AT_CITY", heatStyle(workbook, IndexedColors.LIGHT_YELLOW),
                    "BELOW_CITY", heatStyle(workbook, IndexedColors.ROSE),
                    "NO_CITY_DATA", heatStyle(workbook, IndexedColors.GREY_25_PERCENT));
            Sheet matrix = workbook.createSheet("Матрица");
            String[] matrixHeaders = new String[summary.parallels().size() + 1];
            matrixHeaders[0] = "Предмет";
            for (int index = 0; index < summary.parallels().size(); index++) {
                matrixHeaders[index + 1] = summary.parallels().get(index) + " параллель";
            }
            writeHeader(matrix, matrixHeaders);
            int matrixRowNo = 1;
            for (VsokoMckoDtos.ParallelSubjectRow subject : summary.subjects()) {
                Row row = matrix.createRow(matrixRowNo++);
                row.createCell(0).setCellValue(subject.subjectName());
                Map<Integer, VsokoMckoDtos.ParallelSubjectCell> cells = subject.parallels().stream()
                        .collect(Collectors.toMap(VsokoMckoDtos.ParallelSubjectCell::parallel, Function.identity()));
                for (int column = 0; column < summary.parallels().size(); column++) {
                    VsokoMckoDtos.ParallelSubjectCell value = cells.get(summary.parallels().get(column));
                    Cell cell = row.createCell(column + 1);
                    if (value == null) {
                        cell.setBlank();
                    } else {
                        cell.setCellValue(parallelCellText(value));
                        cell.setCellStyle(heatStyles.get(value.comparison()));
                    }
                }
            }
            finishTable(matrix, matrixHeaders.length, matrixRowNo);
            matrix.createFreezePane(1, 1);
            matrix.setColumnWidth(0, 8500);
            for (int column = 1; column < matrixHeaders.length; column++) matrix.setColumnWidth(column, 5000);

            Sheet details = workbook.createSheet("Данные");
            String[] detailHeaders = {"Учебный год", "Предмет", "Параллель", "Участий", "Диагностик",
                    "% школы", "% города", "Разница, п.п.", "Сравнение"};
            writeHeader(details, detailHeaders);
            int detailRowNo = 1;
            for (VsokoMckoDtos.ParallelSubjectRow subject : summary.subjects()) {
                for (VsokoMckoDtos.ParallelSubjectCell value : subject.parallels()) {
                    Row row = details.createRow(detailRowNo++);
                    values(row, summary.academicYear(), subject.subjectName(), value.parallel(), value.participantCount(),
                            value.diagnosticCount(), value.schoolPercent(), value.cityPercent(), value.difference(),
                            comparisonLabel(value.comparison()));
                    row.getCell(5).setCellStyle(heatStyles.get(value.comparison()));
                }
            }
            finishTable(details, detailHeaders.length, detailRowNo);
            return bytes(workbook);
        }
    }

    @Transactional(readOnly = true)
    public List<VsokoMckoDtos.TeacherAssignmentRow> assignments(String academicYear) {
        return assignmentRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAsc(normalizeYear(academicYear)).stream()
                .map(this::toAssignmentRow).toList();
    }

    @Transactional
    public VsokoMckoDtos.TeacherAssignmentRow saveAssignment(VsokoMckoDtos.TeacherAssignmentRequest request) {
        if (request == null) throw new IllegalArgumentException("Не переданы данные закрепления");
        String year = normalizeYear(request.academicYear());
        String className = normalizeClassDisplay(request.className());
        String subject = display(request.subjectName());
        if (year.isBlank() || className.isBlank() || subject.isBlank() || request.teacherId() == null) {
            throw new IllegalArgumentException("Укажите учебный год, класс, предмет и педагога");
        }
        TeacherDirectoryEntry teacher = teacherRepository.findById(request.teacherId())
                .orElseThrow(() -> new IllegalArgumentException("Педагог не найден: " + request.teacherId()));
        MckoTeacherClassAssignment row = request.id() == null
                ? assignmentRepository.findByAcademicYearAndClassNameIgnoreCaseAndSubjectNameIgnoreCase(year, className, subject)
                    .orElseGet(MckoTeacherClassAssignment::new)
                : assignmentRepository.findById(request.id()).orElseThrow(() -> new IllegalArgumentException("Закрепление не найдено"));
        row.setAcademicYear(year);
        row.setClassName(className);
        row.setSubjectName(subject);
        row.setTeacherId(teacher.getId());
        row.setTeacherFioSnapshot(teacher.getFioTeacher());
        row = assignmentRepository.save(row);
        applyAssignment(row);
        return toAssignmentRow(row);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        MckoTeacherClassAssignment row = assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Закрепление не найдено: " + id));
        for (MckoStudentResult result : resultRepository.findAll()) {
            if (assignmentMatches(row, result)) {
                result.setTeacherId(null);
                result.setTeacherFioSnapshot(null);
                resultRepository.save(result);
            }
        }
        assignmentRepository.delete(row);
    }

    @Transactional
    public int autofillAssignments(String academicYear) {
        String year = normalizeYear(academicYear);
        Map<String, LinkedHashSet<String>> teacherNamesByKey = new LinkedHashMap<>();
        paResultRepository.findAll().stream()
                .filter(row -> normalizeYear(row.getAcademicYear()).equals(year))
                .filter(row -> !blank(row.getTeacherFio()).isBlank())
                .forEach(row -> teacherNamesByKey.computeIfAbsent(assignmentKey(year, row.getClassName(), row.getSubjectName()), ignored -> new LinkedHashSet<>())
                        .add(display(row.getTeacherFio())));
        for (ManualLoadEntry row : manualLoadRepository.findAllByAcademicYear(year)) {
            if (blank(row.getClassName()).isBlank() || blank(row.getSubjectName()).isBlank() || blank(row.getFioTeacher()).isBlank()) continue;
            teacherNamesByKey.computeIfAbsent(assignmentKey(year, row.getClassName(), row.getSubjectName()), ignored -> new LinkedHashSet<>())
                    .add(display(row.getFioTeacher()));
        }
        Map<String, TeacherDirectoryEntry> teachers = teacherRepository.findAll().stream()
                .collect(Collectors.toMap(row -> normalize(row.getFioTeacher()), Function.identity(), (a, b) -> a));
        Map<String, MckoTeacherClassAssignment> existing = assignmentRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAsc(year).stream()
                .collect(Collectors.toMap(this::assignmentKey, Function.identity(), (a, b) -> a));
        Map<String, MckoStudentResult> exampleByKey = resultRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc(year).stream()
                .collect(Collectors.toMap(row -> assignmentKey(year, row.getClassName(), row.getSubjectName()), Function.identity(), (a, b) -> a));
        int count = 0;
        for (Map.Entry<String, LinkedHashSet<String>> entry : teacherNamesByKey.entrySet()) {
            if (entry.getValue().size() != 1 || !exampleByKey.containsKey(entry.getKey())) continue;
            TeacherDirectoryEntry teacher = teachers.get(normalize(entry.getValue().iterator().next()));
            if (teacher == null) continue;
            MckoStudentResult example = exampleByKey.get(entry.getKey());
            MckoTeacherClassAssignment row = existing.getOrDefault(entry.getKey(), new MckoTeacherClassAssignment());
            row.setAcademicYear(year);
            row.setClassName(example.getClassName());
            row.setSubjectName(example.getSubjectName());
            row.setTeacherId(teacher.getId());
            row.setTeacherFioSnapshot(teacher.getFioTeacher());
            row = assignmentRepository.save(row);
            applyAssignment(row);
            existing.put(entry.getKey(), row);
            count++;
        }
        return count;
    }

    @Transactional
    public int importAssignments(String academicYear, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Выберите Excel-файл");
        int imported = 0;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> columns = new HashMap<>();
            if (header != null) for (Cell cell : header) columns.put(normalize(cellText(cell)), cell.getColumnIndex());
            Integer classCol = findColumn(columns, "класс");
            Integer subjectCol = findColumn(columns, "предмет");
            Integer teacherCol = findColumn(columns, "педагог", "учитель", "фио педагога");
            Integer yearCol = findColumn(columns, "учебный год", "год");
            if (classCol == null || subjectCol == null || teacherCol == null) {
                throw new IllegalArgumentException("Нужны колонки «Класс», «Предмет», «Педагог»");
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String className = cellText(row.getCell(classCol));
                String subject = cellText(row.getCell(subjectCol));
                String teacherFio = cellText(row.getCell(teacherCol));
                String year = yearCol == null ? academicYear : firstNonBlank(cellText(row.getCell(yearCol)), academicYear);
                if (className.isBlank() || subject.isBlank() || teacherFio.isBlank()) continue;
                int sourceRow = i + 1;
                TeacherDirectoryEntry teacher = teacherRepository.findByFioTeacherIgnoreCase(teacherFio)
                        .orElseThrow(() -> new IllegalArgumentException("Строка " + sourceRow + ": педагог не найден — " + teacherFio));
                saveAssignment(new VsokoMckoDtos.TeacherAssignmentRequest(null, year, className, subject, teacher.getId()));
                imported++;
            }
        }
        return imported;
    }

    @Transactional(readOnly = true)
    public byte[] exportAssignments(String academicYear) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Закрепление педагогов");
            String[] headers = {"Учебный год", "Класс", "Предмет", "Педагог", "ID педагога"};
            writeHeader(sheet, headers);
            int rowNo = 1;
            for (MckoTeacherClassAssignment assignment : assignmentRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAsc(normalizeYear(academicYear))) {
                Row row = sheet.createRow(rowNo++);
                values(row, assignment.getAcademicYear(), assignment.getClassName(), assignment.getSubjectName(),
                        assignment.getTeacherFioSnapshot(), assignment.getTeacherId());
            }
            finishTable(sheet, headers.length, rowNo);
            return bytes(workbook);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportResults(String academicYear, String className, String subject, String student,
                                String linkStatus, String teacher) throws Exception {
        List<VsokoMckoDtos.ResultRow> rows = results(academicYear, className, subject, student, linkStatus, teacher, 20_000);
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle header = headerStyle(workbook);
            createResultsSheet(workbook, header, rows.stream().filter(row -> !"FUNCTIONAL_LITERACY".equals(row.resultType())).toList());
            createFunctionalSheet(workbook, header, rows.stream().filter(row -> "FUNCTIONAL_LITERACY".equals(row.resultType())).toList());
            createClassSummariesSheet(workbook, header, academicYear, className, subject);
            createWorksSheets(workbook, header, rows);
            createErrorsSheet(workbook, header);
            return bytes(workbook);
        }
    }

    @Transactional(readOnly = true)
    public byte[] interviewWorkbook(String academicYear, List<Long> teacherIds) throws Exception {
        String year = normalizeYear(academicYear);
        LinkedHashSet<Long> selected = teacherIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(teacherIds);
        if (selected.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы одного педагога в список");
        Map<Long, TeacherDirectoryEntry> teachers = teacherRepository.findAllById(selected).stream()
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, Function.identity()));
        if (teachers.size() != selected.size()) throw new IllegalArgumentException("Один из выбранных педагогов не найден");
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle header = interviewHeaderStyle(workbook);
            for (Long teacherId : selected) {
                TeacherDirectoryEntry teacher = teachers.get(teacherId);
                String sheetName = uniqueSheetName(workbook, teacher.getFioTeacher());
                Sheet sheet = workbook.createSheet(sheetName);
                createInterviewSheet(sheet, header, year, teacher);
            }
            return bytes(workbook);
        }
    }

    private void createInterviewSheet(Sheet sheet, CellStyle header, String year, TeacherDirectoryEntry teacher) {
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 4);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.setFitToPage(true);
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("Материалы к собеседованию по результатам ВСОКО");
        title.getCell(0).getCellStyle().setWrapText(true);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 16));
        Row info = sheet.createRow(1);
        info.createCell(0).setCellValue("Педагог: " + teacher.getFioTeacher());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));
        info.createCell(9).setCellValue("Учебный год: " + year);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 9, 16));
        String[] headers = {"Класс", "Предмет", "МЦКО: писали", "5", "4", "3", "2", "Средний % класса",
                "Уровень города", "Средняя МЦКО", "ПА: результатов", "Средний % ПА", "Средняя ПА",
                "Разница оценок", "Выше ПА", "Равно", "Ниже ПА"};
        Row headerRow = sheet.createRow(3);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(header);
        }
        List<MckoTeacherClassAssignment> teacherAssignments = assignmentRepository
                .findAllByAcademicYearOrderByClassNameAscSubjectNameAsc(year).stream()
                .filter(row -> Objects.equals(row.getTeacherId(), teacher.getId())).toList();
        List<MckoStudentResult> allMcko = resultRepository.findAllByAcademicYearOrderByClassNameAscSubjectNameAscStudentFioSnapshotAsc(year);
        List<PaReportStudentResult> allPa = paResultRepository.findAllByAcademicYear(year).stream()
                .filter(PaReportStudentResult::isHasResult).toList();
        int rowNo = 4;
        for (MckoTeacherClassAssignment assignment : teacherAssignments) {
            List<MckoStudentResult> mr = allMcko.stream().filter(row -> assignmentMatches(assignment, row)).toList();
            List<PaReportStudentResult> pr = allPa.stream()
                    .filter(row -> normalizeClass(row.getClassName()).equals(normalizeClass(assignment.getClassName())))
                    .filter(row -> sameSubject(row.getSubjectName(), assignment.getSubjectName())).toList();
            Double mckoMark = average(mr, row -> row.getMark() == null ? null : row.getMark().doubleValue());
            Double paMark = average(pr, row -> row.getMark() == null ? null : row.getMark().doubleValue());
            int above = 0, equal = 0, below = 0;
            Map<Long, Integer> paMarks = pr.stream().filter(row -> row.getStudentId() != null && row.getMark() != null)
                    .collect(Collectors.toMap(PaReportStudentResult::getStudentId, PaReportStudentResult::getMark, (a, b) -> b));
            for (MckoStudentResult row : mr) {
                Integer paValue = row.getStudentId() == null ? null : paMarks.get(row.getStudentId());
                if (row.getMark() == null || paValue == null) continue;
                if (row.getMark() > paValue) above++; else if (row.getMark().equals(paValue)) equal++; else below++;
            }
            Row out = sheet.createRow(rowNo++);
            values(out, assignment.getClassName(), assignment.getSubjectName(), mr.size(), markCount(mr, 5), markCount(mr, 4),
                    markCount(mr, 3), markCount(mr, 2), average(mr, MckoStudentResult::getPercent),
                    averagePercentText(mr, MckoStudentResult::getCityLevel), mckoMark, pr.size(),
                    average(pr, PaReportStudentResult::getPercent), paMark,
                    mckoMark == null || paMark == null ? null : mckoMark - paMark, above, equal, below);
        }
        if (teacherAssignments.isEmpty()) {
            Row row = sheet.createRow(rowNo++);
            row.createCell(0).setCellValue("Нет закреплений педагога за классом и предметом в выбранном году");
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 16));
        }
        sheet.setRepeatingRows(new CellRangeAddress(0, 3, -1, -1));
        finishTable(sheet, headers.length, rowNo, 3);
        sheet.setColumnWidth(0, 3000);
        sheet.setColumnWidth(1, 8000);
    }

    private void createResultsSheet(Workbook wb, CellStyle header, List<VsokoMckoDtos.ResultRow> rows) {
        Sheet sheet = wb.createSheet("Результаты");
        String[] headers = {"ФИО", "Код ученика", "Класс", "Предмет", "Дата", "Учебный год", "Школа",
                "Уровень класса", "Уровень города", "Параллель", "Литера", "Вариант", "Балл", "Процент",
                "Оценка", "Номер ученика", "JSON баллы", "Карточка ID", "Педагог", "Статус привязки", "Источник"};
        writeHeader(sheet, header, headers);
        int rowNo = 1;
        for (VsokoMckoDtos.ResultRow row : rows) {
            Row out = sheet.createRow(rowNo++);
            values(out, row.studentFio(), row.studentCode(), row.className(), row.subjectName(), formatDate(row.diagnosticDate()),
                    row.academicYear(), row.schoolName(), row.classLevel(), row.cityLevel(), parallel(row.className()),
                    classLetter(row.className()), row.variantName(), row.score(), row.percent(), row.mark(), null, null,
                    row.studentId(), row.teacherFio(), row.linkStatus().name(), row.sourceFileName());
        }
        finishTable(sheet, headers.length, rowNo);
    }

    private void createFunctionalSheet(Workbook wb, CellStyle header, List<VsokoMckoDtos.ResultRow> rows) {
        Sheet sheet = wb.createSheet("Функциональная грамотность");
        String[] headers = {"ФИО", "Код ученика", "Класс", "Предмет", "Дата", "Учебный год", "Школа",
                "Уровень класса", "Уровень города", "Общий процент", "Уровень освоения", "Раздел 1 %",
                "Раздел 2 %", "Раздел 3 %", "Карточка ID", "Педагог", "Статус привязки"};
        writeHeader(sheet, header, headers);
        int rowNo = 1;
        for (VsokoMckoDtos.ResultRow row : rows) {
            Row out = sheet.createRow(rowNo++);
            values(out, row.studentFio(), row.studentCode(), row.className(), row.subjectName(), formatDate(row.diagnosticDate()),
                    row.academicYear(), row.schoolName(), row.classLevel(), row.cityLevel(), row.percent(), row.masteryLevel(),
                    row.section1Percent(), row.section2Percent(), row.section3Percent(), row.studentId(), row.teacherFio(),
                    row.linkStatus().name());
        }
        finishTable(sheet, headers.length, rowNo);
    }

    private void createClassSummariesSheet(Workbook wb, CellStyle header, String academicYear,
                                           String className, String subject) {
        Sheet sheet = wb.createSheet("Своды классов");
        String[] headers = {"Учебный год", "Класс", "Предмет", "Дата", "Школа", "Тип отчёта",
                "Участников", "Средний балл", "Средний % класса", "Средний % города", "Источник"};
        writeHeader(sheet, header, headers);
        String yearFilter = normalizeYear(academicYear);
        String classFilter = normalizeClass(className);
        String subjectFilter = normalize(subject);
        Map<Long, String> fileNames = fileRepository.findAll().stream()
                .collect(Collectors.toMap(MckoImportFile::getId, MckoImportFile::getFileName, (a, b) -> a));
        int rowNo = 1;
        for (MckoClassDiagnosticSummary summary : classSummaryRepository.findAll()) {
            if (!yearFilter.isBlank() && !normalizeYear(summary.getAcademicYear()).equals(yearFilter)) continue;
            if (!classFilter.isBlank() && !normalizeClass(summary.getClassName()).equals(classFilter)) continue;
            if (!subjectFilter.isBlank() && !normalize(summary.getSubjectName()).contains(subjectFilter)) continue;
            Row row = sheet.createRow(rowNo++);
            values(row, summary.getAcademicYear(), summary.getClassName(), summary.getSubjectName(),
                    formatDate(summary.getDiagnosticDate()), summary.getSchoolName(), summary.getResultKind(),
                    summary.getParticipantCount(), summary.getAverageScore(), summary.getAveragePercent(),
                    summary.getCityPercent(), fileNames.get(summary.getSourceFileId()));
        }
        finishTable(sheet, headers.length, rowNo);
    }

    private void createWorksSheets(Workbook wb, CellStyle header, List<VsokoMckoDtos.ResultRow> rows) {
        Map<String, List<VsokoMckoDtos.ResultRow>> groups = rows.stream().collect(Collectors.groupingBy(row ->
                String.join("|", blank(row.schoolName()), blank(row.subjectName()), formatDate(row.diagnosticDate()),
                        blank(row.academicYear()), blank(row.className())), LinkedHashMap::new, Collectors.toList()));
        Sheet all = wb.createSheet("Все работы");
        String[] headers = {"Школа", "Предмет", "Дата", "Учебный год", "Класс", "Уровень класса", "Уровень города",
                "Строк в листе детей", "Строк в результатах", "Строк в ФГ", "PDF-файл с результатами класса"};
        writeHeader(all, header, headers);
        Sheet missing = wb.createSheet("Незагруженные работы");
        String[] missingHeaders = {"Школа", "Предмет", "Дата", "Учебный год", "Класс", "Проблема", "Уровень класса",
                "Уровень города", "Строк в листе детей", "Строк в результатах", "Строк в ФГ", "PDF-файл с результатами класса"};
        writeHeader(missing, header, missingHeaders);
        int allRow = 1, missingRow = 1;
        for (List<VsokoMckoDtos.ResultRow> group : groups.values()) {
            VsokoMckoDtos.ResultRow first = group.get(0);
            long standard = group.stream().filter(row -> !"FUNCTIONAL_LITERACY".equals(row.resultType())).count();
            long fg = group.size() - standard;
            Row out = all.createRow(allRow++);
            values(out, first.schoolName(), first.subjectName(), formatDate(first.diagnosticDate()), first.academicYear(),
                    first.className(), first.classLevel(), first.cityLevel(), group.size(), standard, fg, 0);
            long scored = group.stream().filter(row -> row.score() != null || row.percent() != null || row.mark() != null).count();
            if (scored == 0) {
                Row miss = missing.createRow(missingRow++);
                values(miss, first.schoolName(), first.subjectName(), formatDate(first.diagnosticDate()), first.academicYear(),
                        first.className(), "Загружен список детей, но нет результатов работы", first.classLevel(), first.cityLevel(),
                        group.size(), standard, fg, 0);
            }
        }
        finishTable(all, headers.length, allRow);
        finishTable(missing, missingHeaders.length, missingRow);
    }

    private void createErrorsSheet(Workbook wb, CellStyle header) {
        Sheet sheet = wb.createSheet("Ошибки обработки");
        String[] headers = {"Файл", "Учебный год", "Дата работы", "Предмет", "Статус", "Причина",
                "Всего строк", "Импортировано", "Пропущено", "Обработан"};
        writeHeader(sheet, header, headers);
        int rowNo = 1;
        for (MckoImportFile file : fileRepository.findTop200ByOrderByIdDesc()) {
            if (file.getStatus() == MckoFileStatus.PROCESSED) continue;
            Row row = sheet.createRow(rowNo++);
            values(row, file.getFileName(), file.getDetectedAcademicYear(), file.getDetectedWorkDate(),
                    file.getDetectedSubject(), file.getStatus().name(), file.getReason(), file.getTotalRows(),
                    file.getImportedRows(), file.getSkippedRows(), Objects.toString(file.getProcessedAt(), ""));
        }
        finishTable(sheet, headers.length, rowNo);
    }

    private void relinkPa(StudentResultLinker.LinkIndex index) {
        List<PaReportStudentResult> changed = new ArrayList<>();
        for (PaReportStudentResult row : paResultRepository.findAll()) {
            StudentResultLinker.LinkResult link = index.resolve(null, row.getStudentFio(), row.getAcademicYear(), row.getClassName());
            row.setStudentId(link.studentId());
            row.setStudentLinkStatus(link.status().name());
            row.setStudentLinkMessage(link.message());
            changed.add(row);
        }
        paResultRepository.saveAll(changed);
    }

    private void relinkOge(StudentResultLinker.LinkIndex index) {
        List<OgeWorkResult> changed = new ArrayList<>();
        for (OgeWorkResult row : ogeResultRepository.findAll()) {
            StudentResultLinker.LinkResult link = index.resolve(null, row.getFullName(), row.getAcademicYear(), row.getClassName());
            row.setStudentId(link.studentId());
            row.setNeedsManualStudentMatch(link.studentId() == null);
            row.setSourceIssue(link.studentId() == null ? link.message() : null);
            changed.add(row);
        }
        ogeResultRepository.saveAll(changed);
    }

    private void applyAssignment(MckoTeacherClassAssignment assignment) {
        List<MckoStudentResult> changed = resultRepository.findAll().stream().filter(row -> assignmentMatches(assignment, row)).toList();
        changed.forEach(row -> {
            row.setTeacherId(assignment.getTeacherId());
            row.setTeacherFioSnapshot(assignment.getTeacherFioSnapshot());
        });
        resultRepository.saveAll(changed);
    }

    private boolean assignmentMatches(MckoTeacherClassAssignment assignment, MckoStudentResult row) {
        return normalizeYear(assignment.getAcademicYear()).equals(normalizeYear(row.getAcademicYear()))
                && normalizeClass(assignment.getClassName()).equals(normalizeClass(row.getClassName()))
                && sameSubject(assignment.getSubjectName(), row.getSubjectName());
    }

    private boolean matchesStudent(StudentProfile profile, List<StudentNameHistory> histories, String needle) {
        if (StudentResultLinker.normalizeName(profile.getCurrentFullName()).contains(needle)) return true;
        return histories.stream().anyMatch(row -> StudentResultLinker.normalizeName(row.getFullName()).contains(needle));
    }

    private List<String> knownNames(StudentProfile profile, List<StudentNameHistory> histories) {
        LinkedHashSet<String> names = histories.stream().map(StudentNameHistory::getFullName).filter(value -> !blank(value).isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        names.add(profile.getCurrentFullName());
        return names.stream().toList();
    }

    private Map<Long, String> currentClassByStudent() {
        Map<Long, StudentClassEnrollment> latest = new HashMap<>();
        for (StudentClassEnrollment row : enrollmentRepository.findAll()) {
            if (row.getStudent() == null || row.getStudent().getId() == null) continue;
            StudentClassEnrollment current = latest.get(row.getStudent().getId());
            if (current == null || compareEnrollment(row, current) > 0) latest.put(row.getStudent().getId(), row);
        }
        return latest.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getClassName()));
    }

    private int compareEnrollment(StudentClassEnrollment a, StudentClassEnrollment b) {
        int year = normalizeYear(a.getAcademicYear()).compareTo(normalizeYear(b.getAcademicYear()));
        if (year != 0) return year;
        return Comparator.nullsFirst(LocalDate::compareTo).compare(a.getValidFrom(), b.getValidFrom());
    }

    private MckoStudentResult requireResult(Long id) {
        return resultRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Результат МЦКО не найден: " + id));
    }

    private VsokoMckoDtos.ResultRow toResultRow(MckoStudentResult row, String fileName) {
        return new VsokoMckoDtos.ResultRow(row.getId(), row.getStudentId(), row.getStudentFioSnapshot(), row.getStudentCode(),
                row.getStudentLinkStatus(), row.getStudentLinkMessage(), row.getClassName(), row.getSubjectName(),
                row.getDiagnosticDate(), row.getAcademicYear(), row.getSchoolName(), row.getClassLevel(), row.getCityLevel(),
                row.getResultType().name(), row.getVariantName(), row.getScore(), row.getPercent(), row.getMark(),
                row.getMasteryLevel(), row.getSection1Percent(), row.getSection2Percent(), row.getSection3Percent(),
                row.getTeacherId(), row.getTeacherFioSnapshot(), fileName);
    }

    private VsokoMckoDtos.TeacherAssignmentRow toAssignmentRow(MckoTeacherClassAssignment row) {
        return new VsokoMckoDtos.TeacherAssignmentRow(row.getId(), row.getAcademicYear(), row.getClassName(), row.getSubjectName(),
                row.getTeacherId(), row.getTeacherFioSnapshot());
    }

    private List<String> distinct(List<MckoStudentResult> rows, Function<MckoStudentResult, String> getter) {
        return rows.stream().map(getter).filter(value -> !blank(value).isBlank()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> mergeDistinct(List<String> first, List<String> second) {
        TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        first.stream().filter(value -> !blank(value).isBlank()).forEach(result::add);
        second.stream().filter(value -> !blank(value).isBlank()).forEach(result::add);
        return result.stream().toList();
    }

    private <T> Double average(List<T> rows, Function<T, Double> getter) {
        DoubleSummaryStatistics stats = rows.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).summaryStatistics();
        return stats.getCount() == 0 ? null : Math.round(stats.getAverage() * 100.0) / 100.0;
    }

    private Double weightedSummaryAverage(List<MckoClassDiagnosticSummary> rows,
                                          Function<MckoClassDiagnosticSummary, Double> getter) {
        double total = 0;
        int weightTotal = 0;
        for (MckoClassDiagnosticSummary row : rows) {
            Double value = getter.apply(row);
            if (value == null) continue;
            int weight = row.getParticipantCount() == null || row.getParticipantCount() <= 0
                    ? 1 : row.getParticipantCount();
            total += value * weight;
            weightTotal += weight;
        }
        return weightTotal == 0 ? null : Math.round(total / weightTotal * 100.0) / 100.0;
    }

    private void putSubjectName(Map<String, String> subjectNames, String subjectName) {
        String key = normalizeSubject(subjectName);
        if (!key.isBlank()) subjectNames.putIfAbsent(key, display(subjectName));
    }

    private Integer resultParallel(MckoStudentResult row) {
        return row.getParallel() == null ? parallel(row.getClassName()) : row.getParallel();
    }

    private String diagnosticKey(MckoStudentResult row) {
        return normalizeClass(row.getClassName()) + "|" + Objects.toString(row.getDiagnosticDate(), "без даты");
    }

    private Double roundPercent(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String cityComparison(Double difference) {
        if (difference == null) return "NO_CITY_DATA";
        if (difference > 1.0) return "ABOVE_CITY";
        if (difference < -1.0) return "BELOW_CITY";
        return "AT_CITY";
    }

    private String comparisonLabel(String comparison) {
        return switch (comparison) {
            case "ABOVE_CITY" -> "Выше города";
            case "AT_CITY" -> "На уровне города";
            case "BELOW_CITY" -> "Ниже города";
            default -> "Нет данных города";
        };
    }

    private String parallelCellText(VsokoMckoDtos.ParallelSubjectCell value) {
        if (value.cityPercent() == null) {
            return String.format(Locale.forLanguageTag("ru-RU"), "%.1f%%\nгород: нет данных\nучастий: %d · работ: %d",
                    value.schoolPercent(), value.participantCount(), value.diagnosticCount());
        }
        return String.format(Locale.forLanguageTag("ru-RU"), "%.1f%%\nгород: %.1f%%\n%+.1f п.п. · участий: %d · работ: %d",
                value.schoolPercent(), value.cityPercent(), value.difference(), value.participantCount(),
                value.diagnosticCount());
    }

    private <T> Double averagePercentText(List<T> rows, Function<T, String> getter) {
        return average(rows, row -> parsePercent(getter.apply(row)));
    }

    private Double parsePercent(String value) {
        String normalized = blank(value).replace("%", "").replace(',', '.').trim();
        try { return normalized.isBlank() ? null : Double.valueOf(normalized); } catch (NumberFormatException ignored) { return null; }
    }

    private long markCount(List<MckoStudentResult> rows, int mark) {
        return rows.stream().filter(row -> Objects.equals(row.getMark(), mark)).count();
    }

    private boolean sameSubject(String a, String b) {
        String x = normalizeSubject(a);
        String y = normalizeSubject(b);
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    private String assignmentKey(MckoTeacherClassAssignment row) {
        return assignmentKey(row.getAcademicYear(), row.getClassName(), row.getSubjectName());
    }

    private String assignmentKey(String year, String className, String subject) {
        return normalizeYear(year) + "|" + normalizeClass(className) + "|" + normalizeSubject(subject);
    }

    private String normalizeSubject(String value) {
        return normalize(value).replaceAll("[^а-яa-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalize(String value) { return blank(value).toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " "); }
    private String normalizeYear(String value) { return blank(value).replace('-', '/').replaceAll("\\s+", ""); }
    private String normalizeClass(String value) {
        String normalized = blank(value).toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", "")
                .replace('–', '-').replace('—', '-');
        return normalized.replaceAll("^(1[01]|[1-9])-?([а-яa-z])$", "$1-$2");
    }
    private String normalizeClassDisplay(String value) {
        String normalized = blank(value).toUpperCase(Locale.ROOT).replace('Ё', 'Е').replaceAll("\\s+", "")
                .replace('–', '-').replace('—', '-');
        return normalized.replaceAll("^(1[01]|[1-9])-?([А-ЯA-Z])$", "$1-$2");
    }
    private String display(String value) { return blank(value).replaceAll("\\s+", " "); }
    private String blank(String value) { return value == null ? "" : value.trim(); }
    private String firstNonBlank(String first, String second) { return blank(first).isBlank() ? blank(second) : blank(first); }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : List.of(RU_DATE, DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy.MM.dd"))) {
            try { return LocalDate.parse(blank(value), formatter); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private Integer findColumn(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer exact = columns.get(normalize(name));
            if (exact != null) return exact;
            for (Map.Entry<String, Integer> entry : columns.entrySet()) if (entry.getKey().contains(normalize(name))) return entry.getValue();
        }
        return null;
    }

    private String cellText(Cell cell) { return cell == null ? "" : FORMATTER.formatCellValue(cell).trim(); }
    private String formatDate(LocalDate date) { return date == null ? "" : date.format(RU_DATE); }

    private Integer parallel(String className) {
        Matcher matcher = Pattern.compile("^(1[01]|[1-9])").matcher(normalizeClass(className));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String classLetter(String className) {
        Matcher matcher = Pattern.compile("[а-яё]$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(normalizeClass(className));
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : "";
    }

    private void writeHeader(Sheet sheet, String[] headers) { writeHeader(sheet, headerStyle(sheet.getWorkbook()), headers); }
    private void writeHeader(Sheet sheet, CellStyle style, String[] headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) { Cell cell = row.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(style); }
    }

    private void values(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object value = values[i];
            if (value == null) cell.setBlank();
            else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (value instanceof Boolean bool) cell.setCellValue(bool);
            else cell.setCellValue(String.valueOf(value));
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle interviewHeaderStyle(Workbook workbook) {
        CellStyle style = headerStyle(workbook);
        style.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        return style;
    }

    private CellStyle heatStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private void finishTable(Sheet sheet, int columns, int rows) {
        finishTable(sheet, columns, rows, 0);
    }

    private void finishTable(Sheet sheet, int columns, int rows, int headerRow) {
        sheet.createFreezePane(0, Math.min(rows, headerRow + 1));
        if (rows > headerRow && columns > 0) {
            sheet.setAutoFilter(new CellRangeAddress(headerRow, rows - 1, 0, columns - 1));
        }
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 12000));
        }
    }

    private byte[] bytes(Workbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    private String uniqueSheetName(Workbook workbook, String fio) {
        String base = display(fio).replaceAll("[\\[\\]*/?:\\\\]", " ");
        if (base.length() > 28) base = base.substring(0, 28).trim();
        if (base.isBlank()) base = "Педагог";
        String candidate = base;
        int suffix = 2;
        while (workbook.getSheet(candidate) != null) {
            String tail = " " + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 31 - tail.length())) + tail;
        }
        return candidate;
    }
}
