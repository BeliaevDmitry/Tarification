package org.school.personalLoad.vsoko.mcko.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.vsoko.mcko.dto.VsokoMckoDtos;
import org.school.personalLoad.vsoko.mcko.model.*;
import org.school.personalLoad.vsoko.mcko.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class VsokoMckoImportService {
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("ru"));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<Charset> ZIP_CHARSETS = List.of(
            StandardCharsets.UTF_8, Charset.forName("CP866"), Charset.forName("windows-1251"), Charset.forName("IBM437"));
    private static final Pattern DATE_IN_TEXT = Pattern.compile("(?<!\\d)(\\d{1,2}[./-]\\d{1,2}[./-]\\d{4})(?!\\d)");
    private static final Pattern YEAR_IN_TEXT = Pattern.compile("(20\\d{2})\\s*[/\\-]\\s*(20\\d{2})");
    private static final Pattern CLASS_IN_TEXT = Pattern.compile("(?iu)(?<!\\d)(1[01]|[1-9])\\s*[-–—]?\\s*([А-ЯЁ])(?![А-ЯЁ])");

    private final VsokoMckoImportBatchRepository batchRepository;
    private final MckoImportFileRepository fileRepository;
    private final MckoStudentResultRepository resultRepository;
    private final MckoParticipantRosterRepository rosterRepository;
    private final MckoClassDiagnosticSummaryRepository classSummaryRepository;
    private final MckoTeacherClassAssignmentRepository assignmentRepository;
    private final StudentResultLinker studentResultLinker;
    private final MckoParticipantRosterParser rosterParser;
    private final MckoLegacyPdfParser legacyPdfParser;
    private final ObjectMapper objectMapper;

    @Transactional
    public VsokoMckoDtos.ImportResponse importFiles(String requestedAcademicYear,
                                                    List<MultipartFile> files,
                                                    String uploadedBy) {
        List<MultipartFile> source = files == null ? List.of() : files.stream()
                .filter(Objects::nonNull).filter(file -> !file.isEmpty()).toList();
        if (source.isEmpty()) throw new IllegalArgumentException("Выберите хотя бы один непустой файл");

        VsokoMckoImportBatch batch = new VsokoMckoImportBatch();
        batch.setAcademicYear(cleanYear(requestedAcademicYear));
        batch.setUploadedBy(blank(uploadedBy));
        batch.setFilesTotal(source.size());
        batch = batchRepository.save(batch);

        StudentResultLinker.LinkIndex linkIndex = studentResultLinker.buildIndex();
        List<MckoStudentResult> existingResults = resultRepository.findAll();
        Map<String, MckoStudentResult> existingByFingerprint = existingResults.stream()
                .collect(Collectors.toMap(MckoStudentResult::getFingerprint, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<String, MckoStudentResult> existingByWorkStudent = existingResults.stream()
                .filter(row -> row.getStudentNumber() != null)
                .collect(Collectors.toMap(this::workStudentKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<String, MckoTeacherClassAssignment> assignments = assignmentRepository.findAll().stream()
                .collect(Collectors.toMap(this::assignmentKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<MckoParticipantRosterEntry> existingRoster = rosterRepository.findAll();
        Map<String, MckoClassDiagnosticSummary> existingSummaries = classSummaryRepository.findAll().stream()
                .collect(Collectors.toMap(MckoClassDiagnosticSummary::getFingerprint, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        ImportContext context = new ImportContext(linkIndex, existingByFingerprint, existingByWorkStudent,
                assignments, existingRoster.stream().collect(Collectors.toMap(MckoParticipantRosterEntry::getFingerprint,
                Function.identity(), (a, b) -> a, LinkedHashMap::new)), existingSummaries);
        existingRoster.forEach(row -> indexRoster(context, row));

        List<MckoImportFile> savedFiles = new ArrayList<>();
        int processed = 0;
        int failed = 0;
        int rowsImported = 0;
        for (MultipartFile upload : source) {
            MckoImportFile file = new MckoImportFile();
            file.setBatchId(batch.getId());
            file.setFileName(safeFileName(upload.getOriginalFilename()));
            file.setContentType(upload.getContentType());
            file.setFileSize(upload.getSize());
            file = fileRepository.save(file);
            try {
                byte[] bytes = upload.getBytes();
                ProcessOutcome outcome = processBytes(file.getFileName(), bytes, requestedAcademicYear,
                        file.getId(), context, "");
                file.setFileKind(outcome.fileKind());
                file.setDetectedAcademicYear(outcome.detection().academicYearsText());
                file.setDetectedWorkDate(outcome.detection().workDatesText());
                file.setDetectedSubject(outcome.detection().subjectsText());
                file.setTotalRows(outcome.totalRows());
                file.setImportedRows(outcome.importedRows());
                file.setSkippedRows(outcome.skippedRows());
                file.setReason(outcome.message());
                file.setStatus(outcome.skippedRows() > 0 ? MckoFileStatus.PARTIAL : MckoFileStatus.PROCESSED);
                processed++;
                rowsImported += outcome.importedRows();
            } catch (Exception ex) {
                file.setStatus(MckoFileStatus.FAILED);
                file.setReason(userMessage(ex));
                failed++;
            }
            file.setProcessedAt(LocalDateTime.now());
            savedFiles.add(fileRepository.save(file));
        }
        batch.setFilesProcessed(processed);
        batch.setFilesFailed(failed);
        batch.setRowsImported(rowsImported);
        batchRepository.save(batch);
        return new VsokoMckoDtos.ImportResponse(batch.getId(), source.size(), processed, failed, rowsImported,
                savedFiles.stream().map(this::toFileRow).toList());
    }

    @Transactional(readOnly = true)
    public List<VsokoMckoDtos.FileStatusRow> importHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20_000));
        return fileRepository.findAllByOrderByIdDesc(PageRequest.of(0, safeLimit)).stream().map(this::toFileRow).toList();
    }

    private ProcessOutcome processBytes(String fileName,
                                        byte[] bytes,
                                        String requestedAcademicYear,
                                        Long sourceFileId,
                                        ImportContext context,
                                        String containerName) throws Exception {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return processZip(fileName, bytes, requestedAcademicYear, sourceFileId, context);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return processWorkbook(fileName, bytes, requestedAcademicYear, sourceFileId, context);
        }
        if (lower.endsWith(".pdf")) {
            return processPdf(fileName, containerName, bytes, requestedAcademicYear, sourceFileId, context);
        }
        throw new IllegalArgumentException("Неподдерживаемый формат. Разрешены XLSX, XLS, ZIP и PDF");
    }

    private ProcessOutcome processZip(String archiveFileName,
                                      byte[] bytes,
                                      String requestedAcademicYear,
                                      Long sourceFileId,
                                      ImportContext context) throws Exception {
        int total = 0;
        int imported = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        Detection detection = new Detection();
        List<ZipPayload> payloads = readZipPayloads(bytes);
        for (ZipPayload payload : payloads) {
            try {
                ProcessOutcome outcome = processBytes(payload.fileName(), payload.bytes(), requestedAcademicYear,
                        sourceFileId, context, archiveFileName);
                total += outcome.totalRows();
                imported += outcome.importedRows();
                skipped += outcome.skippedRows();
                detection.merge(outcome.detection());
                if (outcome.skippedRows() > 0 || outcome.importedRows() == 0) {
                    messages.add(payload.fileName() + ": " + outcome.message());
                }
            } catch (Exception ex) {
                skipped++;
                messages.add(payload.fileName() + ": " + userMessage(ex));
            }
        }
        String message = messages.isEmpty()
                ? "Обработано файлов внутри ZIP: " + payloads.size()
                : "Обработано файлов внутри ZIP: " + payloads.size() + ". " + limitMessages(messages);
        return new ProcessOutcome("ZIP", total, imported, skipped, message, detection);
    }

    private List<ZipPayload> readZipPayloads(byte[] bytes) throws Exception {
        Exception lastEncodingError = null;
        for (Charset charset : ZIP_CHARSETS) {
            try {
                return readZipPayloads(bytes, charset);
            } catch (Exception ex) {
                if (!isEncodingError(ex)) {
                    if (ex instanceof IOException io) throw io;
                    throw ex;
                }
                lastEncodingError = ex;
            }
        }
        throw new IOException("Не удалось определить кодировку имён файлов внутри ZIP", lastEncodingError);
    }

    private List<ZipPayload> readZipPayloads(byte[] bytes, Charset charset) throws IOException {
        List<ZipPayload> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), charset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = safeZipEntry(entry.getName());
                String lower = entryName.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".pdf")) {
                    result.add(new ZipPayload(entryName, zip.readAllBytes()));
                }
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("В ZIP нет файлов XLSX, XLS или PDF");
        return result;
    }

    private boolean isEncodingError(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof CharacterCodingException || current instanceof MalformedInputException) return true;
            String message = blank(current.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("malformed input") || message.contains("unmappable character")) return true;
        }
        return false;
    }

    private ProcessOutcome processPdf(String fileName,
                                      String containerName,
                                      byte[] bytes,
                                      String requestedAcademicYear,
                                      Long sourceFileId,
                                      ImportContext context) throws Exception {
        Optional<MckoParticipantRosterParser.ParsedRoster> parsed = rosterParser.parse(
                fileName, containerName, bytes, requestedAcademicYear);
        if (parsed.isEmpty()) {
            Optional<MckoLegacyPdfParser.ParsedPdf> report = legacyPdfParser.parse(fileName, bytes, requestedAcademicYear);
            if (report.isPresent()) {
                return persistLegacyPdf(report.get(), sourceFileId, context);
            }
            Detection detection = new Detection();
            detection.add(cleanYear(requestedAcademicYear), null, "");
            return new ProcessOutcome("PDF_REPORT", 0, 0, 0,
                    "PDF принят, но в нём не найден список участников или таблица результатов класса", detection);
        }

        MckoParticipantRosterParser.ParsedRoster roster = parsed.get();
        List<MckoParticipantRosterEntry> changed = new ArrayList<>();
        for (MckoParticipantRosterParser.Participant participant : roster.participants()) {
            String fingerprint = rosterFingerprint(roster, participant);
            MckoParticipantRosterEntry entity = context.rosterByFingerprint.get(fingerprint);
            if (entity == null) {
                entity = findRoster(context, roster.academicYear(), roster.className(), roster.subjectName(),
                        roster.workDate(), participant.studentNumber());
            }
            if (entity == null) entity = new MckoParticipantRosterEntry();
            entity.setStudentFio(participant.fio());
            entity.setStudentCode(participant.code());
            entity.setStudentNumber(participant.studentNumber());
            entity.setClassName(normalizeClass(roster.className()));
            entity.setSubjectName(display(roster.subjectName()));
            entity.setWorkDate(roster.workDate());
            entity.setAcademicYear(cleanYear(roster.academicYear()));
            entity.setSchoolName(display(roster.schoolName()));
            entity.setSourceFileId(sourceFileId);
            entity.setFingerprint(fingerprint);
            entity.setUpdatedAt(LocalDateTime.now());
            StudentResultLinker.LinkResult link = context.linkIndex.resolve(participant.code(), participant.fio(),
                    roster.academicYear(), roster.className());
            entity.setStudentId(link.studentId());
            entity.setStudentLinkStatus(link.status());
            entity.setStudentLinkMessage(link.message());
            changed.add(entity);
        }
        if (!changed.isEmpty()) {
            List<MckoParticipantRosterEntry> saved = rosterRepository.saveAll(changed);
            for (MckoParticipantRosterEntry entity : saved) {
                context.rosterByFingerprint.put(entity.getFingerprint(), entity);
                indexRoster(context, entity);
            }
        }
        int reconciled = reconcileResultsFromRoster(context);
        Detection detection = new Detection();
        detection.add(roster.academicYear(), roster.workDate(), roster.subjectName());
        String message = "Загружен список участников: " + changed.size()
                + (reconciled > 0 ? ". Привязано результатов: " + reconciled : "");
        return new ProcessOutcome("PDF_LIST", changed.size(), changed.size(), 0, message, detection);
    }

    private ProcessOutcome persistLegacyPdf(MckoLegacyPdfParser.ParsedPdf report,
                                            Long sourceFileId,
                                            ImportContext context) throws Exception {
        MckoLegacyPdfParser.WorkSummary summary = report.summary();
        String summaryFingerprint = sha256(String.join("|", cleanYear(report.academicYear()),
                normalizeClass(report.className()), normalizeSubject(report.subjectName()),
                Objects.toString(report.workDate(), ""), report.kind().name()));
        MckoClassDiagnosticSummary summaryEntity = context.summariesByFingerprint.get(summaryFingerprint);
        if (summaryEntity == null) summaryEntity = new MckoClassDiagnosticSummary();
        summaryEntity.setAcademicYear(cleanYear(report.academicYear()));
        summaryEntity.setClassName(normalizeClass(report.className()));
        summaryEntity.setSubjectName(display(report.subjectName()));
        summaryEntity.setDiagnosticDate(report.workDate());
        summaryEntity.setSchoolName(display(report.schoolName()));
        summaryEntity.setResultKind(report.kind().name());
        summaryEntity.setParticipantCount(summary.participantCount());
        summaryEntity.setAverageScore(summary.averageScore());
        summaryEntity.setAveragePercent(summary.averagePercent());
        summaryEntity.setCityPercent(summary.cityPercent());
        summaryEntity.setSourceFileId(sourceFileId);
        summaryEntity.setFingerprint(summaryFingerprint);
        summaryEntity.setUpdatedAt(LocalDateTime.now());
        summaryEntity = classSummaryRepository.save(summaryEntity);
        context.summariesByFingerprint.put(summaryFingerprint, summaryEntity);

        List<MckoStudentResult> changed = new ArrayList<>();
        int sourceRow = 1;
        for (MckoLegacyPdfParser.StudentRow row : report.students()) {
            String taskScores = row.taskScores() == null || row.taskScores().isEmpty()
                    ? null : objectMapper.writeValueAsString(row.taskScores());
            ParsedResult parsed = new ParsedResult("", blank(row.code()), normalizeClass(report.className()),
                    display(report.subjectName()), report.workDate(), cleanYear(report.academicYear()),
                    display(report.schoolName()), percentText(summary.averagePercent()),
                    percentText(summary.cityPercent()), parallel(report.className()), classLetter(report.className()),
                    blank(row.variant()), row.score(), row.percent(), row.mark(), row.studentNumber(), taskScores,
                    report.kind() == MckoLegacyPdfParser.PdfKind.FUNCTIONAL_LITERACY
                            ? MckoResultType.FUNCTIONAL_LITERACY : MckoResultType.STANDARD,
                    display(row.masteryLevel()), row.section1Percent(), row.section2Percent(), row.section3Percent());
            parsed = enrichWithRoster(parsed, context);
            changed.add(upsert(parsed, sourceFileId, sourceRow++, context));
        }
        if (!changed.isEmpty()) resultRepository.saveAll(changed);
        Detection detection = new Detection();
        detection.add(report.academicYear(), report.workDate(), report.subjectName());
        int imported = changed.size() + 1;
        String kindName = report.kind() == MckoLegacyPdfParser.PdfKind.FUNCTIONAL_LITERACY
                ? "функциональной грамотности" : "обычной диагностики";
        String message = changed.isEmpty()
                ? "Загружен свод класса без строк учеников"
                : "Загружен PDF " + kindName + ": результатов учеников — " + changed.size() + ", сводов класса — 1";
        return new ProcessOutcome("PDF_" + report.kind().name(), imported, imported, 0, message, detection);
    }

    private int reconcileResultsFromRoster(ImportContext context) {
        List<MckoStudentResult> changed = new ArrayList<>();
        Set<MckoStudentResult> uniqueResults = Collections.newSetFromMap(new IdentityHashMap<>());
        uniqueResults.addAll(context.resultsByFingerprint.values());
        for (MckoStudentResult result : uniqueResults) {
            MckoParticipantRosterEntry roster = findRoster(context, result.getAcademicYear(), result.getClassName(),
                    result.getSubjectName(), result.getDiagnosticDate(), result.getStudentNumber());
            if (roster == null) continue;
            boolean identityChanged = !Objects.equals(blank(result.getStudentFioSnapshot()), blank(roster.getStudentFio()))
                    || !Objects.equals(blank(result.getStudentCode()), blank(roster.getStudentCode()));
            if (!identityChanged && result.getStudentId() != null) continue;
            result.setStudentFioSnapshot(roster.getStudentFio());
            result.setStudentCode(roster.getStudentCode());
            StudentResultLinker.LinkResult link = context.linkIndex.resolve(roster.getStudentCode(), roster.getStudentFio(),
                    result.getAcademicYear(), result.getClassName());
            result.setStudentId(link.studentId());
            result.setStudentLinkStatus(link.status());
            result.setStudentLinkMessage(link.message());
            result.setUpdatedAt(LocalDateTime.now());
            changed.add(result);
        }
        if (!changed.isEmpty()) resultRepository.saveAll(changed);
        return changed.size();
    }

    private String rosterFingerprint(MckoParticipantRosterParser.ParsedRoster roster,
                                     MckoParticipantRosterParser.Participant participant) {
        return sha256(String.join("|", cleanYear(roster.academicYear()), normalizeClass(roster.className()),
                normalizeSubject(roster.subjectName()), Objects.toString(roster.workDate(), ""),
                String.valueOf(participant.studentNumber())));
    }

    private ProcessOutcome processWorkbook(String fileName,
                                           byte[] bytes,
                                           String requestedAcademicYear,
                                           Long sourceFileId,
                                           ImportContext context) throws Exception {
        int total = 0;
        int imported = 0;
        int skipped = 0;
        int recognizedSheets = 0;
        List<String> messages = new ArrayList<>();
        List<MckoStudentResult> changed = new ArrayList<>();
        Detection detection = new Detection();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                HeaderLayout layout = detectHeader(sheet, evaluator);
                if (layout == null) continue;
                recognizedSheets++;
                SheetMetadata metadata = detectMetadata(sheet, layout, requestedAcademicYear, evaluator);
                for (int rowIndex = layout.headerRow() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || rowLooksBlank(row, layout, evaluator)) continue;
                    total++;
                    try {
                        ParsedResult parsed = parseRow(row, layout, metadata, evaluator, requestedAcademicYear);
                        if (parsed == null) {
                            skipped++;
                            continue;
                        }
                        parsed = enrichWithRoster(parsed, context);
                        detection.add(parsed.academicYear(), parsed.date(), parsed.subject());
                        MckoStudentResult entity = upsert(parsed, sourceFileId, rowIndex + 1, context);
                        changed.add(entity);
                        imported++;
                    } catch (Exception rowError) {
                        skipped++;
                        if (messages.size() < 12) {
                            messages.add(sheet.getSheetName() + "!" + (rowIndex + 1) + ": " + userMessage(rowError));
                        }
                    }
                }
            }
        }
        if (recognizedSheets == 0) {
            throw new IllegalArgumentException("Не найдена таблица результатов: нужны колонки ФИО/код ученика, класс и предмет");
        }
        if (!changed.isEmpty()) resultRepository.saveAll(changed);
        String message = messages.isEmpty()
                ? "Обработано листов: " + recognizedSheets
                : "Обработано листов: " + recognizedSheets + ". " + limitMessages(messages);
        return new ProcessOutcome("EXCEL", total, imported, skipped, message, detection);
    }

    private HeaderLayout detectHeader(Sheet sheet, FormulaEvaluator evaluator) {
        int last = Math.min(sheet.getLastRowNum(), 80);
        for (int rowIndex = 0; rowIndex <= last; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new LinkedHashMap<>();
            Map<Integer, String> rawHeaders = new LinkedHashMap<>();
            for (Cell cell : row) {
                String raw = cellText(cell, evaluator);
                String normalized = headerKey(raw);
                if (normalized.isBlank()) continue;
                rawHeaders.put(cell.getColumnIndex(), raw);
                putIf(columns, canonicalHeader(normalized), cell.getColumnIndex());
            }
            boolean identity = columns.containsKey("fio") || columns.containsKey("code") || columns.containsKey("studentNumber");
            boolean hasClass = columns.containsKey("class")
                    || (columns.containsKey("parallel") && columns.containsKey("letter"));
            boolean dimensions = hasClass && (columns.containsKey("subject") || !genericSheetName(sheet.getSheetName()));
            if (identity && dimensions) return new HeaderLayout(rowIndex, columns, rawHeaders);
        }
        return null;
    }

    private SheetMetadata detectMetadata(Sheet sheet,
                                         HeaderLayout layout,
                                         String requestedAcademicYear,
                                         FormulaEvaluator evaluator) {
        String subject = "";
        String className = "";
        String school = "";
        LocalDate date = null;
        String academicYear = "";
        int maxRows = Math.min(layout.headerRow(), 35);
        for (int rowIndex = 0; rowIndex <= maxRows; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            for (int col = 0; col <= Math.min(row.getLastCellNum(), 20); col++) {
                String text = cellText(row.getCell(col), evaluator).trim();
                if (text.isBlank()) continue;
                String next = cellText(row.getCell(col + 1), evaluator).trim();
                String normalized = headerKey(text);
                if (subject.isBlank() && normalized.startsWith("предмет") && !next.isBlank()) subject = next;
                if (className.isBlank() && normalized.equals("класс") && !next.isBlank()) className = next;
                if (school.isBlank() && text.toLowerCase(Locale.ROOT).contains("гбоу")) school = text;
                if (date == null) date = firstDate(text);
                if (academicYear.isBlank()) academicYear = firstAcademicYear(text);
                if (className.isBlank()) className = firstClass(text);
            }
        }
        if (subject.isBlank() && !genericSheetName(sheet.getSheetName())) subject = sheet.getSheetName();
        if (academicYear.isBlank() && date != null) academicYear = academicYearFor(date);
        if (academicYear.isBlank() && date == null) academicYear = cleanYear(requestedAcademicYear);
        return new SheetMetadata(subject, normalizeClass(className), date, academicYear, school);
    }

    private ParsedResult parseRow(Row row,
                                  HeaderLayout layout,
                                  SheetMetadata metadata,
                                  FormulaEvaluator evaluator,
                                  String requestedAcademicYear) throws Exception {
        String fio = text(row, layout, "fio", evaluator);
        String code = text(row, layout, "code", evaluator);
        Integer studentNumber = integer(text(row, layout, "studentNumber", evaluator));
        if (fio.isBlank() && code.isBlank() && studentNumber == null) return null;

        Integer parallel = integer(text(row, layout, "parallel", evaluator));
        String letter = text(row, layout, "letter", evaluator);
        String assembledClass = parallel == null || letter.isBlank() ? "" : parallel + "-" + letter;
        String className = firstNonBlank(text(row, layout, "class", evaluator),
                firstNonBlank(assembledClass, metadata.className()));
        String subject = firstNonBlank(text(row, layout, "subject", evaluator), metadata.subject());
        if (subject.isBlank()) throw new IllegalArgumentException("не определён предмет");
        LocalDate date = firstNonNull(dateValue(row, layout, "date", evaluator), metadata.date());
        String academicYear = cleanYear(text(row, layout, "year", evaluator));
        if (academicYear.isBlank() && date != null) academicYear = academicYearFor(date);
        if (academicYear.isBlank()) academicYear = cleanYear(metadata.academicYear());
        if (academicYear.isBlank()) academicYear = cleanYear(requestedAcademicYear);
        if (academicYear.isBlank()) throw new IllegalArgumentException("не определён учебный год");

        boolean functional = layout.columns().containsKey("mastery")
                || layout.columns().containsKey("section1")
                || normalizeSubject(subject).contains("функциональн");
        Map<String, Double> tasks = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : layout.rawHeaders().entrySet()) {
            String taskNo = taskNumber(entry.getValue());
            if (taskNo == null) continue;
            Double value = number(cellText(row.getCell(entry.getKey()), evaluator));
            if (value != null) tasks.put(taskNo, value);
        }
        String directTaskScores = text(row, layout, "taskScoresJson", evaluator);
        String taskScoresJson = !directTaskScores.isBlank()
                ? directTaskScores
                : (tasks.isEmpty() ? null : objectMapper.writeValueAsString(tasks));
        return new ParsedResult(
                display(fio), blank(code), normalizeClass(className), display(subject), date, academicYear,
                firstNonBlank(text(row, layout, "school", evaluator), metadata.school()),
                text(row, layout, "classLevel", evaluator), text(row, layout, "cityLevel", evaluator),
                parallel, letter,
                text(row, layout, "variant", evaluator), number(text(row, layout, "score", evaluator)),
                number(text(row, layout, "percent", evaluator)), integer(text(row, layout, "mark", evaluator)),
                studentNumber, taskScoresJson,
                functional ? MckoResultType.FUNCTIONAL_LITERACY : MckoResultType.STANDARD,
                text(row, layout, "mastery", evaluator), number(text(row, layout, "section1", evaluator)),
                number(text(row, layout, "section2", evaluator)), number(text(row, layout, "section3", evaluator))
        );
    }

    private MckoStudentResult upsert(ParsedResult parsed,
                                    Long sourceFileId,
                                    int sourceRow,
                                    ImportContext context) {
        String fingerprint = fingerprint(parsed);
        MckoStudentResult entity = context.resultsByFingerprint.get(fingerprint);
        if (entity == null && parsed.studentNumber() != null) {
            entity = context.resultsByWorkStudent.get(workStudentKey(parsed));
        }
        if (entity == null) entity = new MckoStudentResult();

        if (!parsed.fio().isBlank()) entity.setStudentFioSnapshot(parsed.fio());
        if (!parsed.code().isBlank()) entity.setStudentCode(parsed.code());
        entity.setClassName(parsed.className());
        entity.setSubjectName(parsed.subject());
        entity.setDiagnosticDate(parsed.date());
        entity.setAcademicYear(parsed.academicYear());
        entity.setSchoolName(parsed.school());
        entity.setClassLevel(parsed.classLevel());
        entity.setCityLevel(parsed.cityLevel());
        entity.setParallel(parsed.parallel() == null ? parallel(parsed.className()) : parsed.parallel());
        entity.setClassLetter(firstNonBlank(parsed.letter(), classLetter(parsed.className())));
        entity.setVariantName(parsed.variant());
        if (parsed.score() != null) entity.setScore(parsed.score());
        if (parsed.percent() != null) entity.setPercent(parsed.percent());
        if (parsed.mark() != null) entity.setMark(parsed.mark());
        entity.setStudentNumber(parsed.studentNumber());
        if (parsed.taskScoresJson() != null) entity.setTaskScoresJson(parsed.taskScoresJson());
        entity.setResultType(parsed.resultType());
        entity.setMasteryLevel(parsed.masteryLevel());
        entity.setSection1Percent(parsed.section1());
        entity.setSection2Percent(parsed.section2());
        entity.setSection3Percent(parsed.section3());
        entity.setSourceFileId(sourceFileId);
        entity.setSourceRow(sourceRow);
        entity.setFingerprint(fingerprint);
        entity.setUpdatedAt(LocalDateTime.now());

        StudentResultLinker.LinkResult link = context.linkIndex.resolve(entity.getStudentCode(), entity.getStudentFioSnapshot(),
                parsed.academicYear(), parsed.className());
        entity.setStudentId(link.studentId());
        entity.setStudentLinkStatus(link.status());
        entity.setStudentLinkMessage(link.message());

        MckoTeacherClassAssignment assignment = context.assignments.get(assignmentKey(parsed.academicYear(),
                parsed.className(), parsed.subject()));
        if (assignment != null) {
            entity.setTeacherId(assignment.getTeacherId());
            entity.setTeacherFioSnapshot(assignment.getTeacherFioSnapshot());
        }
        context.resultsByFingerprint.put(fingerprint, entity);
        if (parsed.studentNumber() != null) context.resultsByWorkStudent.put(workStudentKey(parsed), entity);
        return entity;
    }

    private ParsedResult enrichWithRoster(ParsedResult parsed, ImportContext context) {
        MckoParticipantRosterEntry roster = findRoster(context, parsed.academicYear(), parsed.className(),
                parsed.subject(), parsed.date(), parsed.studentNumber());
        if (roster == null) return parsed;
        return new ParsedResult(firstNonBlank(parsed.fio(), roster.getStudentFio()),
                firstNonBlank(parsed.code(), roster.getStudentCode()), parsed.className(), parsed.subject(),
                parsed.date(), parsed.academicYear(), parsed.school(), parsed.classLevel(), parsed.cityLevel(),
                parsed.parallel(), parsed.letter(), parsed.variant(), parsed.score(), parsed.percent(), parsed.mark(),
                parsed.studentNumber(), parsed.taskScoresJson(), parsed.resultType(), parsed.masteryLevel(),
                parsed.section1(), parsed.section2(), parsed.section3());
    }

    private String canonicalHeader(String key) {
        if (containsAny(key, "фио", "фамилияимя", "ученик", "участник")) return "fio";
        if (containsAny(key, "кодученика", "кодучастника", "индивидуальныйкод", "кодфио")) return "code";
        if (key.equals("класс") || key.equals("классгруппа")) return "class";
        if (key.equals("предмет") || key.equals("учебныйпредмет") || key.equals("диагностика")) return "subject";
        if (key.equals("дата") || containsAny(key, "датапроведения", "датаработы")) return "date";
        if (containsAny(key, "учебныйгод", "годобучения")) return "year";
        if (key.equals("школа") || containsAny(key, "образовательнаяорганизация")) return "school";
        if (key.equals("балл") || key.equals("итоговыйбалл") || key.equals("тестовыйбалл")) return "score";
        if (key.equals("процент") || key.equals("общийпроцент") || key.equals("выполнение")
                || containsAny(key, "процентвып", "процентвыполн")) return "percent";
        if (key.equals("оценка") || key.equals("отметка")) return "mark";
        if (containsAny(key, "вариант")) return "variant";
        if (key.equals("уч") || containsAny(key, "номерученика", "номеручастника", "порядковыйномер")) return "studentNumber";
        if (containsAny(key, "jsonбаллы", "баллыjson", "результатыпозаданиям")) return "taskScoresJson";
        if (key.equals("параллель")) return "parallel";
        if (key.equals("буква") || containsAny(key, "литера", "буквакласса")) return "letter";
        if (containsAny(key, "уровенькласса", "среднийпроценткласса")) return "classLevel";
        if (containsAny(key, "уровеньгорода", "среднийпроцентгорода")) return "cityLevel";
        if (containsAny(key, "уровеньосвоения", "уровеньовладения")) return "mastery";
        if (containsAny(key, "раздел1", "направление1")) return "section1";
        if (containsAny(key, "раздел2", "направление2")) return "section2";
        if (containsAny(key, "раздел3", "направление3")) return "section3";
        return "";
    }

    private void putIf(Map<String, Integer> columns, String key, int value) {
        if (key != null && !key.isBlank()) columns.putIfAbsent(key, value);
    }

    private boolean rowLooksBlank(Row row, HeaderLayout layout, FormulaEvaluator evaluator) {
        return text(row, layout, "fio", evaluator).isBlank()
                && text(row, layout, "code", evaluator).isBlank()
                && text(row, layout, "studentNumber", evaluator).isBlank();
    }

    private String text(Row row, HeaderLayout layout, String key, FormulaEvaluator evaluator) {
        Integer col = layout.columns().get(key);
        return col == null || row == null ? "" : cellText(row.getCell(col), evaluator).trim();
    }

    private LocalDate dateValue(Row row, HeaderLayout layout, String key, FormulaEvaluator evaluator) {
        Integer col = layout.columns().get(key);
        if (col == null || row == null) return null;
        Cell cell = row.getCell(col);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return firstDate(cellText(cell, evaluator));
    }

    private String cellText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        try {
            return FORMATTER.formatCellValue(cell, evaluator);
        } catch (RuntimeException ignored) {
            return FORMATTER.formatCellValue(cell);
        }
    }

    private LocalDate firstDate(String value) {
        String text = blank(value);
        Matcher matcher = DATE_IN_TEXT.matcher(text);
        String candidate = matcher.find() ? matcher.group(1).replace('-', '.') : text;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try { return LocalDate.parse(candidate, format); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String firstAcademicYear(String value) {
        Matcher matcher = YEAR_IN_TEXT.matcher(blank(value));
        return matcher.find() ? matcher.group(1) + "/" + matcher.group(2) : "";
    }

    private String firstClass(String value) {
        Matcher matcher = CLASS_IN_TEXT.matcher(blank(value));
        return matcher.find() ? matcher.group(1) + "-" + matcher.group(2).toUpperCase(Locale.ROOT) : "";
    }

    private String academicYearFor(LocalDate date) {
        int start = date.getMonthValue() >= 8 ? date.getYear() : date.getYear() - 1;
        return start + "/" + (start + 1);
    }

    private String fingerprint(ParsedResult row) {
        String identity = row.studentNumber() == null ? "" : "row:" + row.studentNumber();
        if (identity.isBlank()) {
            identity = !StudentResultLinker.normalizeCode(row.code()).isBlank()
                    ? StudentResultLinker.normalizeCode(row.code())
                    : StudentResultLinker.normalizeName(row.fio());
        }
        return sha256(String.join("|", identity, normalizeSubject(row.subject()), normalizeClass(row.className()),
                cleanYear(row.academicYear()), Objects.toString(row.date(), ""), row.resultType().name()));
    }

    private String workStudentKey(MckoStudentResult row) {
        return assignmentKey(row.getAcademicYear(), row.getClassName(), row.getSubjectName()) + "|"
                + Objects.toString(row.getDiagnosticDate(), "") + "|" + Objects.toString(row.getStudentNumber(), "");
    }

    private String workStudentKey(ParsedResult row) {
        return assignmentKey(row.academicYear(), row.className(), row.subject()) + "|"
                + Objects.toString(row.date(), "") + "|" + Objects.toString(row.studentNumber(), "");
    }

    private void indexRoster(ImportContext context, MckoParticipantRosterEntry row) {
        addRosterIndex(context.rosterByExactKey, rosterExactKey(row.getAcademicYear(), row.getClassName(),
                row.getSubjectName(), row.getWorkDate(), row.getStudentNumber()), row);
        if (row.getWorkDate() != null) {
            addRosterIndex(context.rosterByMonthKey, rosterMonthKey(row.getAcademicYear(), row.getClassName(),
                    row.getSubjectName(), row.getWorkDate(), row.getStudentNumber()), row);
        }
        addRosterIndex(context.rosterByBaseKey, rosterBaseKey(row.getAcademicYear(), row.getClassName(),
                row.getSubjectName(), row.getStudentNumber()), row);
    }

    private void addRosterIndex(Map<String, List<MckoParticipantRosterEntry>> index,
                                String key,
                                MckoParticipantRosterEntry row) {
        if (key.isBlank()) return;
        List<MckoParticipantRosterEntry> values = index.computeIfAbsent(key, ignored -> new ArrayList<>());
        values.removeIf(existing -> Objects.equals(existing.getFingerprint(), row.getFingerprint()));
        values.add(row);
    }

    private MckoParticipantRosterEntry findRoster(ImportContext context,
                                                   String academicYear,
                                                   String className,
                                                   String subject,
                                                   LocalDate date,
                                                   Integer studentNumber) {
        if (studentNumber == null) return null;
        if (date != null) {
            MckoParticipantRosterEntry exact = uniqueRoster(context.rosterByExactKey.get(
                    rosterExactKey(academicYear, className, subject, date, studentNumber)));
            if (exact != null) return exact;
            MckoParticipantRosterEntry month = uniqueRoster(context.rosterByMonthKey.get(
                    rosterMonthKey(academicYear, className, subject, date, studentNumber)));
            if (month != null) return month;
        }
        return uniqueRoster(context.rosterByBaseKey.get(
                rosterBaseKey(academicYear, className, subject, studentNumber)));
    }

    private MckoParticipantRosterEntry uniqueRoster(List<MckoParticipantRosterEntry> values) {
        if (values == null || values.isEmpty()) return null;
        Map<String, MckoParticipantRosterEntry> unique = values.stream().collect(Collectors.toMap(
                MckoParticipantRosterEntry::getFingerprint, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return unique.size() == 1 ? unique.values().iterator().next() : null;
    }

    private String rosterExactKey(String year, String className, String subject, LocalDate date, Integer number) {
        return rosterBaseKey(year, className, subject, number) + "|" + Objects.toString(date, "");
    }

    private String rosterMonthKey(String year, String className, String subject, LocalDate date, Integer number) {
        return rosterBaseKey(year, className, subject, number) + "|" + (date == null ? "" : YearMonth.from(date));
    }

    private String rosterBaseKey(String year, String className, String subject, Integer number) {
        return assignmentKey(year, className, subject) + "|" + Objects.toString(number, "");
    }

    private String assignmentKey(MckoTeacherClassAssignment row) {
        return assignmentKey(row.getAcademicYear(), row.getClassName(), row.getSubjectName());
    }

    private String assignmentKey(String year, String className, String subject) {
        return cleanYear(year) + "|" + normalizeClass(className) + "|" + normalizeSubject(subject);
    }

    private String normalizeSubject(String value) {
        return blank(value).toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String percentText(Double value) {
        if (value == null) return "";
        return (Math.rint(value) == value ? String.valueOf(value.intValue()) : String.valueOf(value)) + "%";
    }

    private String normalizeClass(String value) {
        String normalized = blank(value).toUpperCase(Locale.ROOT).replace('Ё', 'Е')
                .replace('–', '-').replace('—', '-').replaceAll("\\s+", "");
        return normalized.replaceAll("^(1[01]|[1-9])-?([А-ЯA-Z])$", "$1-$2");
    }

    private Integer parallel(String className) {
        Matcher matcher = Pattern.compile("^(1[01]|[1-9])").matcher(normalizeClass(className));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String classLetter(String className) {
        Matcher matcher = Pattern.compile("[А-ЯЁ]$").matcher(normalizeClass(className));
        return matcher.find() ? matcher.group() : "";
    }

    private String taskNumber(String header) {
        String value = blank(header).toLowerCase(Locale.ROOT).trim();
        Matcher matcher = Pattern.compile("^(?:задание\\s*)?(\\d{1,3})$").matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Double number(String value) {
        String normalized = blank(value).replace("%", "").replace(" ", "").replace(',', '.');
        if (normalized.isBlank() || normalized.equals("-") || normalized.equalsIgnoreCase("нет")) return null;
        try { return Double.valueOf(normalized); } catch (NumberFormatException ignored) { return null; }
    }

    private Integer integer(String value) {
        Double parsed = number(value);
        return parsed == null ? null : parsed.intValue();
    }

    private String headerKey(String value) {
        return blank(value).toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replace("%", "процент").replaceAll("[^а-яa-z0-9]+", "");
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) if (value.contains(marker)) return true;
        return false;
    }

    private boolean genericSheetName(String value) {
        String key = headerKey(value);
        return key.isBlank() || containsAny(key, "лист", "sheet", "результат", "данные", "отчет", "функциональнаяграмотность");
    }

    private String cleanYear(String value) {
        Matcher matcher = YEAR_IN_TEXT.matcher(blank(value));
        return matcher.find() ? matcher.group(1) + "/" + matcher.group(2) : blank(value).replace('-', '/');
    }

    private String safeFileName(String value) {
        String normalized = blank(value).replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return name.isBlank() ? "без_имени" : name;
    }

    private String safeZipEntry(String value) {
        String normalized = blank(value).replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../")) {
            throw new IllegalArgumentException("Некорректный путь внутри ZIP");
        }
        return safeFileName(normalized);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String userMessage(Exception ex) {
        if (isEncodingError(ex)) return "Не удалось прочитать русские имена файлов внутри ZIP";
        if (ex instanceof ZipException) return "ZIP-архив повреждён или имеет неподдерживаемую структуру";
        String message = ex.getMessage();
        if ((message == null || message.isBlank()) && ex.getCause() != null) message = ex.getCause().getMessage();
        if (message == null || message.isBlank()) return "Не удалось обработать файл";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("malformed input")) return "Не удалось определить кодировку содержимого файла";
        if (lower.contains("invalid entry size") || lower.contains("unexpected end")) return "ZIP-архив повреждён или загружен не полностью";
        if (lower.contains("encrypted") && lower.contains("document")) return "Файл защищён паролем";
        return message;
    }

    private String limitMessages(List<String> messages) {
        if (messages == null || messages.isEmpty()) return "";
        return messages.stream().limit(12).collect(Collectors.joining("; "))
                + (messages.size() > 12 ? "; ещё: " + (messages.size() - 12) : "");
    }

    private VsokoMckoDtos.FileStatusRow toFileRow(MckoImportFile file) {
        return new VsokoMckoDtos.FileStatusRow(file.getId(), file.getBatchId(), file.getFileName(), file.getFileKind(),
                file.getFileSize(), file.getDetectedAcademicYear(), file.getDetectedWorkDate(), file.getDetectedSubject(),
                file.getStatus(), file.getReason(), file.getTotalRows(), file.getImportedRows(),
                file.getSkippedRows(), file.getProcessedAt());
    }

    private String firstNonBlank(String first, String second) { return blank(first).isBlank() ? blank(second) : blank(first); }
    private <T> T firstNonNull(T first, T second) { return first == null ? second : first; }
    private String display(String value) { return blank(value).replaceAll("\\s+", " ").trim(); }
    private String blank(String value) { return value == null ? "" : value.trim(); }

    private record HeaderLayout(int headerRow, Map<String, Integer> columns, Map<Integer, String> rawHeaders) {}
    private record SheetMetadata(String subject, String className, LocalDate date, String academicYear, String school) {}
    private record ProcessOutcome(String fileKind, int totalRows, int importedRows, int skippedRows,
                                  String message, Detection detection) {}
    private record ZipPayload(String fileName, byte[] bytes) {}

    private static final class Detection {
        private final Set<String> academicYears = new TreeSet<>();
        private final Set<LocalDate> workDates = new TreeSet<>();
        private final Set<String> subjects = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        private void add(String academicYear, LocalDate workDate, String subject) {
            if (academicYear != null && !academicYear.isBlank()) academicYears.add(academicYear.trim());
            if (workDate != null) workDates.add(workDate);
            if (subject != null && !subject.isBlank()) subjects.add(subject.trim());
        }

        private void merge(Detection other) {
            if (other == null) return;
            academicYears.addAll(other.academicYears);
            workDates.addAll(other.workDates);
            subjects.addAll(other.subjects);
        }

        private String academicYearsText() { return String.join(", ", academicYears); }
        private String workDatesText() { return workDates.stream().map(DISPLAY_DATE::format).collect(Collectors.joining(", ")); }
        private String subjectsText() { return String.join(", ", subjects); }
    }

    private static final class ImportContext {
        private final StudentResultLinker.LinkIndex linkIndex;
        private final Map<String, MckoStudentResult> resultsByFingerprint;
        private final Map<String, MckoStudentResult> resultsByWorkStudent;
        private final Map<String, MckoTeacherClassAssignment> assignments;
        private final Map<String, MckoParticipantRosterEntry> rosterByFingerprint;
        private final Map<String, MckoClassDiagnosticSummary> summariesByFingerprint;
        private final Map<String, List<MckoParticipantRosterEntry>> rosterByExactKey = new HashMap<>();
        private final Map<String, List<MckoParticipantRosterEntry>> rosterByMonthKey = new HashMap<>();
        private final Map<String, List<MckoParticipantRosterEntry>> rosterByBaseKey = new HashMap<>();

        private ImportContext(StudentResultLinker.LinkIndex linkIndex,
                              Map<String, MckoStudentResult> resultsByFingerprint,
                              Map<String, MckoStudentResult> resultsByWorkStudent,
                              Map<String, MckoTeacherClassAssignment> assignments,
                              Map<String, MckoParticipantRosterEntry> rosterByFingerprint,
                              Map<String, MckoClassDiagnosticSummary> summariesByFingerprint) {
            this.linkIndex = linkIndex;
            this.resultsByFingerprint = resultsByFingerprint;
            this.resultsByWorkStudent = resultsByWorkStudent;
            this.assignments = assignments;
            this.rosterByFingerprint = rosterByFingerprint;
            this.summariesByFingerprint = summariesByFingerprint;
        }
    }
    private record ParsedResult(String fio, String code, String className, String subject, LocalDate date,
                                String academicYear, String school, String classLevel, String cityLevel,
                                Integer parallel, String letter, String variant, Double score, Double percent,
                                Integer mark, Integer studentNumber, String taskScoresJson, MckoResultType resultType,
                                String masteryLevel, Double section1, Double section2, Double section3) {}
}
