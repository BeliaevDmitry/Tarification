package org.school.personalLoad.pa.analytics.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;
import org.school.personalLoad.pa.analytics.model.PaAnalysisStatus;
import org.school.personalLoad.pa.analytics.model.PaReportAnalysisSummary;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.model.PaReportTaskResult;
import org.school.personalLoad.pa.analytics.model.PaSpecificationMatchSource;
import org.school.personalLoad.pa.analytics.model.PaStudentResultStatus;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportTaskResultRepository;
import org.school.personalLoad.pa.analytics.service.PaReportAnalysisJobRunner;
import org.school.personalLoad.pa.analytics.service.PaReportAnalysisService;
import org.school.personalLoad.pa.model.PaGradingScale;
import org.school.personalLoad.pa.model.PaReportVersion;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaSpecification;
import org.school.personalLoad.pa.model.PaSpecificationTask;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.school.personalLoad.pa.repository.PaSpecificationRepository;
import org.school.personalLoad.pa.repository.PaSpecificationTaskRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaReportAnalysisServiceImpl implements PaReportAnalysisService {

    private static final String PA_REPORT_STORAGE_DIR = "pa-reports";
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("ru"));
    private static final int FIRST_TASK_COL = 4;
    private static final int FIRST_STUDENT_ROW = 3;
    private static final Pattern PARALLEL_PATTERN = Pattern.compile("^(\\d{1,2}).*");

    private final PaReportVersionRepository reportVersionRepository;
    private final PaSpecificationRepository specificationRepository;
    private final PaSpecificationTaskRepository specificationTaskRepository;
    private final PaReportAnalysisSummaryRepository summaryRepository;
    private final PaReportStudentResultRepository studentResultRepository;
    private final PaReportTaskResultRepository taskResultRepository;
    private final ObjectProvider<PaReportAnalysisJobRunner> jobRunnerProvider;

    @Override
    @Transactional
    public void analyzeReport(Long reportVersionId) {
        PaReportVersion version = findReportVersion(reportVersionId);
        LocalDateTime startedAt = LocalDateTime.now();
        String skipReason = validateReportForAnalysis(version);
        if (skipReason != null) {
            PaReportAnalysisSummary summary = findOrCreateSummary(version);
            summary.setAnalysisStartedAt(startedAt);
            summary.setAnalysisFinishedAt(LocalDateTime.now());
            summary.setAnalysisStatus(PaAnalysisStatus.SKIPPED);
            summary.setAnalysisMessage(skipReason);
            summary.setNeedsReview(true);
            summaryRepository.save(summary);
            return;
        }

        taskResultRepository.deleteByReportVersionId(reportVersionId);
        studentResultRepository.deleteByReportVersionId(reportVersionId);
        try {
            analyzeAcceptedReport(version, startedAt);
        } catch (Exception exception) {
            throw new IllegalStateException("Ошибка анализа отчёта ПА " + reportVersionId, exception);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAnalysisError(Long reportVersionId, Exception exception) {
        PaReportVersion version = reportVersionRepository.findById(reportVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Не найден reportVersionId=" + reportVersionId));
        LocalDateTime now = LocalDateTime.now();
        PaReportAnalysisSummary summary = findOrCreateSummary(version);
        summary.setReportVersionId(reportVersionId);
        if (summary.getAcademicYear() == null || summary.getAcademicYear().isBlank()) {
            summary.setAcademicYear(version.getAcademicYear());
        }
        summary.setAnalysisStartedAt(summary.getAnalysisStartedAt() == null ? now : summary.getAnalysisStartedAt());
        summary.setAnalysisFinishedAt(now);
        summary.setAnalysisStatus(PaAnalysisStatus.ERROR);
        summary.setNeedsReview(true);
        summary.setAnalysisMessage(buildErrorMessage(exception));

        try {
            Path directory = Path.of(PA_REPORT_STORAGE_DIR, safeYear(summary.getAcademicYear()), "analysis-logs");
            Files.createDirectories(directory);
            String fileName = "analysis_error_report_" + reportVersionId + "_" + now.format(LOG_TIMESTAMP_FORMAT) + ".txt";
            Path logPath = directory.resolve(fileName);
            Files.writeString(logPath, buildErrorLog(version, reportVersionId, exception), StandardCharsets.UTF_8);
            summary.setAnalysisErrorLogPath(logPath.toString());
            summary.setAnalysisErrorLogFileName(fileName);
        } catch (Exception logException) {
            summary.setAnalysisMessage(summary.getAnalysisMessage()
                    + "; не удалось записать txt-лог: " + buildErrorMessage(logException));
        }

        summaryRepository.save(summary);
    }

    @Override
    public PaAnalyticsDtos.RebuildAllResult rebuildAll(String academicYear) {
        List<Long> reportVersionIds = reportVersionRepository.findAll().stream()
                .filter(version -> Objects.equals(version.getAcademicYear(), academicYear))
                .filter(version -> "ACCEPTED".equalsIgnoreCase(version.getStatus()))
                .filter(PaReportVersion::isUploadedBackSuccess)
                .filter(version -> version.getSourceFilePath() != null && !version.getSourceFilePath().isBlank())
                .map(PaReportVersion::getId)
                .toList();
        PaReportAnalysisJobRunner jobRunner = jobRunnerProvider.getObject();
        int processed = 0;
        int failed = 0;
        for (Long reportVersionId : reportVersionIds) {
            try {
                jobRunner.analyzeOneInNewTransaction(reportVersionId);
                processed++;
            } catch (Exception exception) {
                failed++;
                saveErrorSafely(jobRunner, reportVersionId, exception);
            }
        }
        return new PaAnalyticsDtos.RebuildAllResult("REBUILD_FINISHED", academicYear, processed, failed);
    }

    private void saveErrorSafely(PaReportAnalysisJobRunner jobRunner, Long reportVersionId, Exception exception) {
        try {
            jobRunner.saveAnalysisErrorInNewTransaction(reportVersionId, exception);
        } catch (Exception ignored) {
            // Массовый пересчёт не должен падать из-за сбоя записи диагностического лога по одному отчёту.
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaAnalyticsDtos.ReportAnalysisDetails getDetails(Long reportVersionId) {
        PaAnalyticsDtos.ReportAnalysisListItem summary = summaryRepository.findByReportVersionId(reportVersionId)
                .map(summaryRow -> toListItem(summaryRow, reportVersionRepository.findById(reportVersionId).orElse(null)))
                .orElse(null);
        List<PaAnalyticsDtos.StudentResultRow> students = studentResultRepository
                .findAllByReportVersionIdOrderByStudentFioAsc(reportVersionId)
                .stream()
                .map(this::toStudentRow)
                .toList();
        List<PaAnalyticsDtos.TaskResultRow> tasks = toTaskRows(
                taskResultRepository.findAllByReportVersionIdOrderByTaskNoAsc(reportVersionId)
        );
        return new PaAnalyticsDtos.ReportAnalysisDetails(summary, students, tasks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaAnalyticsDtos.ReportAnalysisListItem> getReports(String academicYear,
                                                                   String subjectName,
                                                                   String teacherFio,
                                                                   String className,
                                                                   String workType,
                                                                   Boolean onlyProblems,
                                                                   Boolean onlyNeedsReview,
                                                                   Boolean includeTechnical) {
        Map<Long, PaReportVersion> versionsById = reportVersionRepository.findAll().stream()
                .collect(Collectors.toMap(PaReportVersion::getId, version -> version, (first, second) -> first));
        return summaryRepository.findAllByAcademicYearOrderBySubjectNameAscClassNameAscTeacherFioAsc(academicYear)
                .stream()
                .filter(summary -> Boolean.TRUE.equals(includeTechnical) || isUserVisibleReport(summary, versionsById.get(summary.getReportVersionId())))
                .filter(summary -> matches(summary.getSubjectName(), subjectName))
                .filter(summary -> matches(summary.getTeacherFio(), teacherFio))
                .filter(summary -> matches(summary.getClassName(), className))
                .filter(summary -> matches(summary.getWorkType(), workType))
                .filter(summary -> !Boolean.TRUE.equals(onlyNeedsReview) || summary.isNeedsReview())
                .filter(summary -> !Boolean.TRUE.equals(onlyProblems) || positive(summary.getProblemTasksCount()) || positive(summary.getProblemTopicsCount()))
                .map(summary -> toListItem(summary, versionsById.get(summary.getReportVersionId())))
                .sorted(Comparator.comparing(PaAnalyticsDtos.ReportAnalysisListItem::subjectName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(PaAnalyticsDtos.ReportAnalysisListItem::className, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(PaAnalyticsDtos.ReportAnalysisListItem::teacherFio, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private void analyzeAcceptedReport(PaReportVersion version, LocalDateTime startedAt) throws Exception {
        Path reportPath = Path.of(version.getSourceFilePath());
        try (Workbook workbook = WorkbookFactory.create(reportPath.toFile())) {
            Sheet dataSheet = workbook.getSheet("Сбор информации");
            if (dataSheet == null) {
                throw new IllegalStateException("В отчёте отсутствует лист «Сбор информации»");
            }

            List<String> warnings = new ArrayList<>();
            SheetStructure structure = detectSheetStructure(dataSheet);
            if (structure.taskColumns().isEmpty()) {
                throw new IllegalStateException("На листе «Сбор информации» не найдены колонки заданий");
            }
            if (structure.totalCol() == null) {
                warnings.add("Не найдена колонка «Итог», итоговый балл рассчитан по заданиям");
            }
            if (structure.markCol() == null) {
                warnings.add("Не найдена колонка «Отметка» или «Зачёт/незачёт»");
            }

            SpecificationResolution specification = resolveSpecificationForAnalysis(version);
            Map<Integer, PaSpecificationTask> specificationTasks = specification.specification() == null
                    ? Map.of()
                    : specificationTaskRepository.findAllBySpecificationIdOrderByTaskNoAsc(specification.specification().getId())
                    .stream()
                    .collect(Collectors.toMap(PaSpecificationTask::getTaskNo, task -> task, (first, second) -> first));
            if (specification.specification() == null) {
                warnings.add("Спецификация не найдена, темы и навыки не подтянуты");
            }
            GradingRules gradingRules = resolveGradingRules(specification.specification());
            if (gradingRules.fallbackUsed()) {
                warnings.add(gradingRules.warningMessage());
            }

            boolean subgroupSubject = isSubgroupSubject(version.getSubjectName());
            List<PaReportStudentResult> students = new ArrayList<>();
            List<PaReportTaskResult> taskResults = new ArrayList<>();
            for (int rowIndex = FIRST_STUDENT_ROW; rowIndex <= dataSheet.getLastRowNum(); rowIndex++) {
                Row row = dataSheet.getRow(rowIndex);
                String studentFio = cellText(row, 1);
                if (studentFio.isBlank()) {
                    continue;
                }
                StudentParseResult parsedStudent = parseStudentRow(version, row, structure, subgroupSubject, gradingRules);
                PaReportStudentResult savedStudent = studentResultRepository.save(parsedStudent.student());
                students.add(savedStudent);
                if (savedStudent.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT
                        || savedStudent.getRowStatus() == PaStudentResultStatus.EMPTY_RESULT) {
                    taskResults.addAll(buildTaskResults(version, savedStudent.getId(), row, structure, specificationTasks));
                }
            }
            if (!taskResults.isEmpty()) {
                taskResultRepository.saveAll(taskResults);
            }

            PaReportAnalysisSummary summary = findOrCreateSummary(version);
            fillSummaryFromAnalysis(summary, version, students, taskResults, specification, gradingRules, warnings, startedAt);
            summaryRepository.save(summary);
        }
    }

    private SheetStructure detectSheetStructure(Sheet dataSheet) {
        Row headerRow = dataSheet.getRow(0);
        Row taskNoRow = dataSheet.getRow(1);
        Integer totalCol = null;
        Integer markCol = null;
        int lastCell = Math.max(lastCellNum(headerRow), lastCellNum(taskNoRow));
        for (int col = FIRST_TASK_COL; col <= lastCell; col++) {
            String header = normalize(cellText(headerRow, col));
            if (totalCol == null && header.contains("итог")) {
                totalCol = col;
            }
            if (markCol == null && (header.contains("отмет") || header.contains("зач"))) {
                markCol = col;
            }
        }

        List<TaskColumn> taskColumns = new ArrayList<>();
        for (int col = FIRST_TASK_COL; col <= lastCell; col++) {
            if (totalCol != null && col >= totalCol) {
                break;
            }
            String taskNoText = cellText(taskNoRow, col);
            if (taskNoText.isBlank()) {
                break;
            }
            Integer taskNo = parseTaskNo(taskNoText);
            if (taskNo == null) {
                break;
            }
            Double maxScore = parseDouble(cellText(dataSheet.getRow(2), col));
            taskColumns.add(new TaskColumn(col, taskNo, maxScore));
        }
        if (totalCol == null && !taskColumns.isEmpty()) {
            totalCol = taskColumns.get(taskColumns.size() - 1).col() + 1;
        }
        if (markCol == null && totalCol != null) {
            markCol = totalCol + 1;
        }
        return new SheetStructure(taskColumns, totalCol, markCol);
    }

    private StudentParseResult parseStudentRow(PaReportVersion version,
                                               Row row,
                                               SheetStructure structure,
                                               boolean subgroupSubject,
                                               GradingRules gradingRules) {
        String studentFio = cellText(row, 1).trim();
        String presenceStatus = cellText(row, 2).trim();
        String variantName = cellText(row, 3).trim();
        boolean absent = isAbsent(presenceStatus);
        boolean present = isPresent(presenceStatus);
        boolean hasAnyScore = structure.taskColumns().stream()
                .map(task -> cellText(row, task.col()))
                .anyMatch(text -> !text.isBlank());
        if (presenceStatus.isBlank() && hasAnyScore) {
            presenceStatus = "Был";
            present = true;
        }
        Double totalScore = structure.totalCol() == null ? null : parseDouble(cellText(row, structure.totalCol()));
        if (totalScore == null && hasAnyScore) {
            totalScore = structure.taskColumns().stream()
                    .map(task -> parseDouble(cellText(row, task.col())))
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }
        Double maxScore = structure.taskColumns().stream()
                .map(TaskColumn::maxScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (maxScore != null && maxScore <= 0D) {
            maxScore = null;
        }
        Double percent = totalScore == null || maxScore == null || maxScore <= 0D ? null : totalScore / maxScore * 100D;
        Integer mark = null;

        PaStudentResultStatus rowStatus;
        boolean possibleOtherSubgroup = false;
        boolean hasResult = false;
        if (absent) {
            rowStatus = PaStudentResultStatus.ABSENT;
        } else if (present && hasAnyScore) {
            rowStatus = PaStudentResultStatus.PRESENT_WITH_RESULT;
            hasResult = true;
        } else if ((presenceStatus.isBlank() && !hasAnyScore) || (present && !hasAnyScore)) {
            if (subgroupSubject) {
                rowStatus = PaStudentResultStatus.POSSIBLE_OTHER_SUBGROUP;
                possibleOtherSubgroup = true;
            } else {
                rowStatus = PaStudentResultStatus.EMPTY_RESULT;
            }
        } else if (!presenceStatus.isBlank() && !present && !absent) {
            rowStatus = PaStudentResultStatus.INVALID_ROW;
        } else {
            rowStatus = PaStudentResultStatus.EMPTY_RESULT;
        }
        if (rowStatus == PaStudentResultStatus.PRESENT_WITH_RESULT) {
            mark = calculateMark(percent, gradingRules);
        }

        PaReportStudentResult student = new PaReportStudentResult();
        student.setReportVersionId(version.getId());
        student.setAcademicYear(version.getAcademicYear());
        student.setSubjectName(version.getSubjectName());
        student.setClassName(version.getScopeValue());
        student.setTeacherFio(version.getTeacherFio());
        student.setStudentFio(studentFio);
        student.setStudentFioNormalized(normalizeFio(studentFio));
        student.setPresenceStatus(presenceStatus);
        student.setVariantName(variantName);
        student.setTotalScore(totalScore);
        student.setMaxScore(maxScore);
        student.setPercent(percent);
        student.setMark(mark);
        student.setHasResult(hasResult);
        student.setPossibleOtherSubgroup(possibleOtherSubgroup);
        student.setRowStatus(rowStatus);
        student.setCreatedAt(LocalDateTime.now());
        return new StudentParseResult(student);
    }

    private List<PaReportTaskResult> buildTaskResults(PaReportVersion version,
                                                      Long studentResultId,
                                                      Row row,
                                                      SheetStructure structure,
                                                      Map<Integer, PaSpecificationTask> specificationTasks) {
        List<PaReportTaskResult> results = new ArrayList<>();
        for (TaskColumn taskColumn : structure.taskColumns()) {
            PaSpecificationTask specificationTask = specificationTasks.get(taskColumn.taskNo());
            Double maxScore = specificationTask != null && specificationTask.getMaxScore() != null
                    ? specificationTask.getMaxScore().doubleValue()
                    : taskColumn.maxScore();
            Double score = parseDouble(cellText(row, taskColumn.col()));
            PaReportTaskResult result = new PaReportTaskResult();
            result.setReportVersionId(version.getId());
            result.setStudentResultId(studentResultId);
            result.setTaskNo(taskColumn.taskNo());
            result.setTopic(specificationTask == null ? null : specificationTask.getTopic());
            result.setSkill(specificationTask == null ? null : specificationTask.getSkill());
            result.setTaskKind(specificationTask == null || specificationTask.getTaskKind() == null ? null : specificationTask.getTaskKind().name());
            result.setRepeatFromTaskNo(specificationTask == null ? null : specificationTask.getRepeatFromTaskNo());
            result.setMaxScore(maxScore);
            result.setScore(score);
            result.setPercent(score == null || maxScore == null || maxScore <= 0D ? null : score / maxScore * 100D);
            result.setEmpty(score == null);
            result.setCreatedAt(LocalDateTime.now());
            results.add(result);
        }
        return results;
    }

    private void fillSummaryFromAnalysis(PaReportAnalysisSummary summary,
                                         PaReportVersion version,
                                         List<PaReportStudentResult> students,
                                         List<PaReportTaskResult> taskResults,
                                         SpecificationResolution specification,
                                         GradingRules gradingRules,
                                         List<String> warnings,
                                         LocalDateTime startedAt) {
        fillSummaryFromVersion(summary, version);
        summary.setSpecificationFound(specification.specification() != null);
        summary.setSpecificationId(specification.specification() == null ? null : specification.specification().getId());
        summary.setSpecificationSource(specification.source());

        long studentsWithResult = students.stream().filter(student -> student.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT).count();
        long studentsAbsent = students.stream().filter(student -> student.getRowStatus() == PaStudentResultStatus.ABSENT).count();
        long studentsEmpty = students.stream().filter(student -> student.getRowStatus() == PaStudentResultStatus.EMPTY_RESULT).count();
        long possibleOtherSubgroup = students.stream().filter(student -> student.getRowStatus() == PaStudentResultStatus.POSSIBLE_OTHER_SUBGROUP).count();
        long invalidRows = students.stream().filter(student -> student.getRowStatus() == PaStudentResultStatus.INVALID_ROW).count();

        summary.setStudentsTotal(students.size());
        summary.setStudentsWithResult((int) studentsWithResult);
        summary.setStudentsAbsent((int) studentsAbsent);
        summary.setStudentsEmpty((int) studentsEmpty);
        summary.setPossibleOtherSubgroupCount((int) possibleOtherSubgroup);
        summary.setAvgPercent(avg(students.stream()
                .filter(student -> student.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT)
                .map(PaReportStudentResult::getPercent)
                .filter(Objects::nonNull)
                .toList()));
        summary.setAvgMark(avg(students.stream()
                .filter(student -> student.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT)
                .map(PaReportStudentResult::getMark)
                .filter(Objects::nonNull)
                .map(Integer::doubleValue)
                .toList()));
        if (gradingRules.passFail()) {
            summary.setSuccessPercent(studentsWithResult == 0 ? null : percent(students.stream()
                    .filter(student -> student.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT)
                    .filter(student -> student.getPercent() != null && student.getPercent() >= gradingRules.passPercent())
                    .count(), studentsWithResult));
            summary.setQualityPercent(null);
        } else {
            summary.setSuccessPercent(studentsWithResult == 0 ? null : percent(students.stream()
                    .filter(student -> student.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT)
                    .filter(student -> student.getMark() != null && student.getMark() >= 3)
                    .count(), studentsWithResult));
            summary.setQualityPercent(studentsWithResult == 0 ? null : percent(students.stream()
                    .filter(student -> student.getRowStatus() == PaStudentResultStatus.PRESENT_WITH_RESULT)
                    .filter(student -> student.getMark() != null && student.getMark() >= 4)
                    .count(), studentsWithResult));
        }

        List<PaAnalyticsDtos.TaskResultRow> aggregatedTasks = toTaskRows(taskResults);
        List<PaAnalyticsDtos.TaskResultRow> problemTasks = aggregatedTasks.stream()
                .filter(row -> row.avgPercent() != null && row.avgPercent() < 50D)
                .toList();
        summary.setProblemTasksCount(problemTasks.size());
        summary.setProblemTopicsCount((int) problemTasks.stream()
                .map(PaAnalyticsDtos.TaskResultRow::topic)
                .filter(topic -> topic != null && !topic.isBlank())
                .distinct()
                .count());

        if (studentsWithResult == 0) {
            warnings.add("Нет учеников с заполненными результатами");
        }
        if (studentsEmpty > 0) {
            warnings.add("Есть строки учеников без результатов");
        }
        if (invalidRows > 0) {
            warnings.add("Есть некорректные строки учеников");
        }

        boolean needsReview = !summary.isSpecificationFound()
                || studentsEmpty > 0
                || invalidRows > 0
                || studentsWithResult == 0
                || !warnings.isEmpty();
        summary.setNeedsReview(needsReview);
        summary.setAnalysisStatus(warnings.isEmpty() ? PaAnalysisStatus.SUCCESS : PaAnalysisStatus.WARNING);
        summary.setAnalysisMessage(warnings.isEmpty() ? "Анализ выполнен успешно" : String.join("; ", warnings));
        summary.setAnalysisErrorLogPath(null);
        summary.setAnalysisErrorLogFileName(null);
        summary.setAnalysisStartedAt(startedAt);
        summary.setAnalysisFinishedAt(LocalDateTime.now());
    }

    private GradingRules resolveGradingRules(PaSpecification specification) {
        if (specification == null) {
            return GradingRules.fallback(false, "Отметки рассчитаны по fallback-порогам 85/61/35");
        }
        if (specification.getGradingScale() == PaGradingScale.PASS_FAIL) {
            Integer passPercent = specification.getPassPercent();
            if (passPercent == null || passPercent < 0 || passPercent > 100) {
                return GradingRules.passFailFallback("Некорректный порог зачёта в спецификации, использован fallback-порог 35%");
            }
            return GradingRules.passFail(passPercent);
        }
        Integer grade5 = specification.getGrade5Percent();
        Integer grade4 = specification.getGrade4Percent();
        Integer grade3 = specification.getGrade3Percent();
        if (!validFivePointThresholds(grade5, grade4, grade3)) {
            return GradingRules.fallback(true, "Некорректные пороги отметок в спецификации, использованы fallback-пороги 85/61/35");
        }
        return GradingRules.fivePoint(grade5, grade4, grade3);
    }

    private boolean validFivePointThresholds(Integer grade5, Integer grade4, Integer grade3) {
        return grade5 != null && grade4 != null && grade3 != null
                && grade5 >= 0 && grade5 <= 100
                && grade4 >= 0 && grade4 <= 100
                && grade3 >= 0 && grade3 <= 100
                && grade5 > grade4
                && grade4 > grade3;
    }

    private Integer calculateMark(Double percent, GradingRules gradingRules) {
        if (percent == null || gradingRules.passFail()) {
            return null;
        }
        if (percent >= gradingRules.grade5Percent()) {
            return 5;
        }
        if (percent >= gradingRules.grade4Percent()) {
            return 4;
        }
        if (percent >= gradingRules.grade3Percent()) {
            return 3;
        }
        return 2;
    }

    private SpecificationResolution resolveSpecificationForAnalysis(PaReportVersion report) {
        String className = report.getScopeValue();
        String parallel = extractParallel(className);
        List<SpecificationCandidate> candidates = List.of(
                new SpecificationCandidate(PaScopeType.CLASS, className, report.getWorkDate(), PaSpecificationMatchSource.CLASS_EXACT_DATE),
                new SpecificationCandidate(PaScopeType.CLASS, className, null, PaSpecificationMatchSource.CLASS_NO_DATE),
                new SpecificationCandidate(PaScopeType.PARALLEL, parallel, report.getWorkDate(), PaSpecificationMatchSource.PARALLEL_EXACT_DATE),
                new SpecificationCandidate(PaScopeType.PARALLEL, parallel, null, PaSpecificationMatchSource.PARALLEL_NO_DATE)
        );
        List<PaSpecification> specs = specificationRepository.findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(report.getAcademicYear());
        for (SpecificationCandidate candidate : candidates) {
            if (candidate.scopeValue() == null || candidate.scopeValue().isBlank()) {
                continue;
            }
            Optional<PaSpecification> found = specs.stream()
                    .filter(PaSpecification::isActiveVersion)
                    .filter(spec -> Objects.equals(normalize(spec.getSubjectName()), normalize(report.getSubjectName())))
                    .filter(spec -> spec.getScopeType() == candidate.scopeType())
                    .filter(spec -> Objects.equals(normalizeClass(spec.getScopeValue()), normalizeClass(candidate.scopeValue())))
                    .filter(spec -> spec.getLevel() == report.getLevel())
                    .filter(spec -> spec.getWorkType() == report.getWorkType())
                    .filter(spec -> Objects.equals(spec.getWorkDate(), candidate.workDate()))
                    .max(Comparator.comparing(PaSpecification::getVersionNo, Comparator.nullsFirst(Integer::compareTo)));
            if (found.isPresent()) {
                return new SpecificationResolution(found.get(), candidate.source());
            }
        }
        return new SpecificationResolution(null, PaSpecificationMatchSource.NOT_FOUND);
    }

    private String validateReportForAnalysis(PaReportVersion version) {
        if (!"ACCEPTED".equalsIgnoreCase(nvl(version.getStatus()))) {
            return "Анализ пропущен: версия отчёта не принята (status=" + nvl(version.getStatus()) + ")";
        }
        if (!version.isUploadedBackSuccess()) {
            return "Анализ пропущен: отчёт не был успешно сдан обратно";
        }
        if (version.getSourceFilePath() == null || version.getSourceFilePath().isBlank()) {
            return "Анализ пропущен: не заполнен путь к исходному файлу отчёта";
        }
        if (!Files.isRegularFile(Path.of(version.getSourceFilePath()))) {
            return "Анализ пропущен: файл отчёта не найден на диске";
        }
        return null;
    }

    private PaReportVersion findReportVersion(Long reportVersionId) {
        return reportVersionRepository.findById(reportVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Версия отчёта ПА не найдена: " + reportVersionId));
    }

    private PaReportAnalysisSummary findOrCreateSummary(PaReportVersion version) {
        PaReportAnalysisSummary summary = summaryRepository.findByReportVersionId(version.getId())
                .orElseGet(PaReportAnalysisSummary::new);
        fillSummaryFromVersion(summary, version);
        return summary;
    }

    private void fillSummaryFromVersion(PaReportAnalysisSummary summary, PaReportVersion version) {
        summary.setReportVersionId(version.getId());
        summary.setAcademicYear(version.getAcademicYear());
        summary.setSubjectName(version.getSubjectName());
        summary.setClassName(version.getScopeValue());
        summary.setTeacherFio(version.getTeacherFio());
        summary.setWorkType(version.getWorkType() == null ? null : version.getWorkType().name());
        summary.setWorkDate(version.getWorkDate());
        summary.setLevel(version.getLevel() == null ? null : version.getLevel().name());
    }

    private PaAnalyticsDtos.ReportAnalysisListItem toListItem(PaReportAnalysisSummary summary, PaReportVersion version) {
        return new PaAnalyticsDtos.ReportAnalysisListItem(
                summary.getReportVersionId(),
                summary.getAcademicYear(),
                summary.getSubjectName(),
                summary.getClassName(),
                summary.getTeacherFio(),
                summary.getWorkType(),
                summary.getWorkDate(),
                summary.getLevel(),
                summary.getStudentsTotal(),
                summary.getStudentsWithResult(),
                summary.getAvgPercent(),
                summary.getAvgMark(),
                summary.getSuccessPercent(),
                summary.getQualityPercent(),
                summary.getProblemTasksCount(),
                summary.getProblemTopicsCount(),
                summary.isNeedsReview(),
                summary.getAnalysisStatus(),
                summary.getAnalysisMessage(),
                !isUserVisibleReport(summary, version)
        );
    }

    private boolean isUserVisibleReport(PaReportAnalysisSummary summary, PaReportVersion version) {
        if (version == null || summary == null) {
            return false;
        }
        if (!version.isActiveVersion() || !"ACCEPTED".equalsIgnoreCase(nvl(version.getStatus())) || !version.isUploadedBackSuccess()) {
            return false;
        }
        if (isBlank(version.getSourceFilePath()) || isBlank(version.getSubjectName()) || isBlank(version.getScopeValue())) {
            return false;
        }
        if (summary.getAnalysisStatus() == PaAnalysisStatus.SKIPPED || summary.getAnalysisStatus() == PaAnalysisStatus.NOT_ANALYZED) {
            return false;
        }
        return !isBlank(summary.getSubjectName()) && !isBlank(summary.getClassName());
    }

    private PaAnalyticsDtos.StudentResultRow toStudentRow(PaReportStudentResult row) {
        return new PaAnalyticsDtos.StudentResultRow(
                row.getStudentFio(),
                row.getPresenceStatus(),
                row.getVariantName(),
                row.getTotalScore(),
                row.getMaxScore(),
                row.getPercent(),
                row.getMark(),
                row.getRowStatus(),
                row.isPossibleOtherSubgroup()
        );
    }

    private List<PaAnalyticsDtos.TaskResultRow> toTaskRows(List<PaReportTaskResult> rows) {
        Map<Integer, List<PaReportTaskResult>> grouped = rows.stream()
                .collect(Collectors.groupingBy(PaReportTaskResult::getTaskNo));
        return grouped.entrySet().stream()
                .map(entry -> toTaskRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PaAnalyticsDtos.TaskResultRow::taskNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private PaAnalyticsDtos.TaskResultRow toTaskRow(Integer taskNo, List<PaReportTaskResult> rows) {
        PaReportTaskResult sample = rows.stream()
                .filter(row -> row.getTopic() != null || row.getSkill() != null || row.getTaskKind() != null || row.getMaxScore() != null)
                .findFirst()
                .orElse(rows.get(0));
        Double avgScore = avg(rows.stream()
                .filter(row -> row.getScore() != null)
                .map(PaReportTaskResult::getScore)
                .toList());
        Double avgPercent = avg(rows.stream()
                .filter(row -> row.getPercent() != null)
                .map(PaReportTaskResult::getPercent)
                .toList());
        long below50Count = rows.stream()
                .filter(row -> row.getPercent() != null && row.getPercent() < 50D)
                .count();
        long emptyCount = rows.stream()
                .filter(PaReportTaskResult::isEmpty)
                .count();
        return new PaAnalyticsDtos.TaskResultRow(
                taskNo,
                sample.getTopic(),
                sample.getSkill(),
                sample.getTaskKind(),
                sample.getMaxScore(),
                avgScore,
                avgPercent,
                below50Count,
                emptyCount,
                taskStatus(avgPercent)
        );
    }

    private String taskStatus(Double avgPercent) {
        if (avgPercent == null) {
            return "Нет данных";
        }
        if (avgPercent < 50D) {
            return "Проблема";
        }
        if (avgPercent < 70D) {
            return "Зона внимания";
        }
        return "Норма";
    }

    private boolean isSubgroupSubject(String subjectName) {
        String normalized = normalize(subjectName);
        return normalized.contains("информатика")
                || normalized.contains("английский")
                || normalized.contains("немецкий")
                || normalized.contains("французский")
                || normalized.contains("китайский")
                || normalized.contains("испанский")
                || normalized.contains("иностранный язык");
    }

    private boolean isAbsent(String presenceStatus) {
        String normalized = normalize(presenceStatus);
        return normalized.equals("не был") || normalized.equals("нет") || normalized.equals("н") || normalized.equals("отсутствовал");
    }

    private boolean isPresent(String presenceStatus) {
        String normalized = normalize(presenceStatus);
        return normalized.equals("был") || normalized.equals("да") || normalized.equals("присутствовал");
    }

    private String cellText(Row row, int col) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    private int lastCellNum(Row row) {
        return row == null || row.getLastCellNum() < 0 ? FIRST_TASK_COL : row.getLastCellNum();
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace("%", "")
                .replace(" ", "")
                .replace(" ", "")
                .replace(',', '.');
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        Double number = parseDouble(value);
        return number == null ? null : number.intValue();
    }

    private Integer parseTaskNo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        Integer number = parseInteger(trimmed);
        if (number != null) {
            return number;
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(trimmed);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    private Double avg(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private Double percent(long value, long total) {
        return total == 0 ? null : value * 100D / total;
    }

    private boolean matches(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private String safeYear(String academicYear) {
        return academicYear == null || academicYear.isBlank() ? "unknown" : academicYear.replace("/", "-");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ").trim();
    }

    private String normalizeClass(String value) {
        return normalize(value).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeFio(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }

    private String extractParallel(String className) {
        Matcher matcher = PARALLEL_PATTERN.matcher(nvl(className).trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private String buildErrorMessage(Exception exception) {
        if (exception == null) {
            return "Неизвестная ошибка анализа";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getName();
        }
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }

    private String buildErrorLog(PaReportVersion version, Long reportVersionId, Exception exception) {
        return "reportVersionId: " + reportVersionId + System.lineSeparator()
                + "academicYear: " + (version == null ? "" : nvl(version.getAcademicYear())) + System.lineSeparator()
                + "subjectName: " + (version == null ? "" : nvl(version.getSubjectName())) + System.lineSeparator()
                + "className: " + (version == null ? "" : nvl(version.getScopeValue())) + System.lineSeparator()
                + "teacherFio: " + (version == null ? "" : nvl(version.getTeacherFio())) + System.lineSeparator()
                + "workType: " + (version == null || version.getWorkType() == null ? "" : version.getWorkType().name()) + System.lineSeparator()
                + "level: " + (version == null || version.getLevel() == null ? "" : version.getLevel().name()) + System.lineSeparator()
                + "sourceFileName: " + (version == null ? "" : nvl(version.getSourceFileName())) + System.lineSeparator()
                + "sourceFilePath: " + (version == null ? "" : nvl(version.getSourceFilePath())) + System.lineSeparator()
                + "error: " + buildErrorMessage(exception) + System.lineSeparator()
                + "stacktrace:" + System.lineSeparator()
                + stackTrace(exception);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String stackTrace(Exception exception) {
        if (exception == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private record SheetStructure(List<TaskColumn> taskColumns, Integer totalCol, Integer markCol) {
    }

    private record TaskColumn(int col, Integer taskNo, Double maxScore) {
    }

    private record StudentParseResult(PaReportStudentResult student) {
    }

    private record SpecificationResolution(PaSpecification specification, PaSpecificationMatchSource source) {
    }

    private record GradingRules(boolean passFail,
                                int grade5Percent,
                                int grade4Percent,
                                int grade3Percent,
                                int passPercent,
                                boolean fallbackUsed,
                                String warningMessage) {
        private static GradingRules fivePoint(int grade5Percent, int grade4Percent, int grade3Percent) {
            return new GradingRules(false, grade5Percent, grade4Percent, grade3Percent, 0, false, null);
        }

        private static GradingRules fallback(boolean warning, String warningMessage) {
            return new GradingRules(false, 85, 61, 35, 0, warning, warningMessage);
        }

        private static GradingRules passFail(int passPercent) {
            return new GradingRules(true, 0, 0, 0, passPercent, false, null);
        }

        private static GradingRules passFailFallback(String warningMessage) {
            return new GradingRules(true, 0, 0, 0, 35, true, warningMessage);
        }
    }

    private record SpecificationCandidate(PaScopeType scopeType,
                                          String scopeValue,
                                          java.time.LocalDate workDate,
                                          PaSpecificationMatchSource source) {
    }
}
