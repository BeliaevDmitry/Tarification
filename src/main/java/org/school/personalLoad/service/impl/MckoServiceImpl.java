package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.MckoDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.MckoService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MckoServiceImpl implements MckoService {
    private static final Set<String> ALLOWED_EXAM_TYPES = Set.of(
            "комплексная диагностика ноо",
            "комплексная диагностика егэ (29.11.2025 - 13.12.2025)",
            "диагностика егэ",
            "комплексная диагностика егэ",
            "предметная и метапредметная диагностика",
            "комплексный тренинг егэ",
            "ознакомительный тренинг в формате егэ",
            "комплексная диагностика огэ",
            "комплексная диагностика егэ при приеме на работу",
            "онлайн-тренинг в формате егэ",
            "комплексная диагностика егэ для кандидатов в члены пк",
            "комплексный тренинг ноо"
    );
    private static final String PRIMARY_META_SUBJECT = "Метапредметные умения (начальное образование)";
    private static final String PRIMARY_META_ALIAS = "Начальная школа";
    private static final long PRIMARY_GROUP_SUBJECT_ID = -1L;
    private static final long MATH_GROUP_SUBJECT_ID = -2L;
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MckoCertificateRepository certificateRepository;
    private final MckoImportBatchRepository importBatchRepository;
    private final MckoSubjectMappingRepository mappingRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final SubjectCatalogRepository subjectRepository;
    private final ManualLoadEntryRepository manualLoadRepository;

    @Override
    public List<MckoDtos.CertificateRow> certificates(String academicYear, String mode) {
        Set<Long> teacherFilter = teacherIdsForMode(academicYear, mode);
        return certificateRepository.findAll().stream()
                .filter(row -> teacherFilter.isEmpty() || (row.getTeacherId() != null && teacherFilter.contains(row.getTeacherId())))
                .sorted(Comparator.comparing(MckoCertificate::getTeacherFioSnapshot, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(MckoCertificate::getMckoSubject, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(MckoCertificate::getDiagnosticDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toRow)
                .toList();
    }

    @Override
    @Transactional
    public MckoDtos.ImportResult importCertificates(MultipartFile file) {
        List<String> warnings = new ArrayList<>();
        MckoImportBatch batch = new MckoImportBatch();
        batch.setFileName(file.getOriginalFilename());
        batch = importBatchRepository.save(batch);
        int total = 0;
        int imported = 0;
        int skipped = 0;
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            HeaderIndex header = headerIndex(sheet);
            Map<String, TeacherDirectoryEntry> teachers = teacherRepository.findAll().stream()
                    .collect(Collectors.toMap(t -> normalize(t.getFioTeacher()), Function.identity(), (a, b) -> a));
            for (int i = header.headerRow() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String fio = cell(row, header.requiredAny("фио педагогов", "фио"));
                if (fio.isBlank()) continue;
                total++;
                String examType = cell(row, header.required("тип экзамена"));
                LocalDate diagnosticDate = dateCell(row, header.required("дата диагностики"));
                String subject = cell(row, header.required("предмет"));
                String level = cell(row, header.required("достигнутый уровень"));
                boolean published = parsePublished(cell(row, header.required("публикация результатов")));
                if (!ALLOWED_EXAM_TYPES.contains(normalize(examType))) {
                    skipped++;
                    addWarning(warnings, "Строка " + (i + 1) + ": пропущен тип экзамена «" + examType + "»");
                    continue;
                }
                TeacherDirectoryEntry teacher = teachers.get(normalize(fio));
                if (teacher == null || diagnosticDate == null || subject.isBlank()) {
                    skipped++;
                    warnings.add("Строка " + (i + 1) + ": не найден педагог или не заполнены дата/предмет");
                    continue;
                }
                MckoCertificate cert = certificateRepository
                        .findFirstByTeacherIdAndMckoSubjectIgnoreCaseAndDiagnosticDateAndExamTypeIgnoreCaseAndSource(
                                teacher.getId(), subject, diagnosticDate, examType, MckoCertificateSource.IMPORT)
                        .orElseGet(MckoCertificate::new);
                cert.setTeacherId(teacher.getId());
                cert.setTeacherFioSnapshot(teacher.getFioTeacher());
                cert.setMckoSubject(canonicalMckoSubject(subject));
                cert.setExamType(examType.trim());
                cert.setDiagnosticDate(diagnosticDate);
                cert.setExpiresAt(diagnosticDate.plusYears(3));
                cert.setLevel(level.trim());
                cert.setPublished(published);
                cert.setSource(MckoCertificateSource.IMPORT);
                cert.setImportBatch(batch);
                certificateRepository.save(cert);
                imported++;
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось импортировать МЦКО: " + ex.getMessage(), ex);
        }
        batch.setTotalRows(total);
        batch.setImportedRows(imported);
        batch.setSkippedRows(skipped);
        importBatchRepository.save(batch);
        return new MckoDtos.ImportResult(total, imported, skipped, warnings);
    }

    @Override
    @Transactional
    public MckoDtos.CertificateRow createManualCertificate(Long teacherId, String mckoSubject, String examType, LocalDate diagnosticDate,
                                                           String level, boolean published, String comment, MultipartFile scan) throws IOException {
        TeacherDirectoryEntry teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Педагог не найден"));
        MckoCertificate cert = new MckoCertificate();
        cert.setTeacherId(teacher.getId());
        cert.setTeacherFioSnapshot(teacher.getFioTeacher());
        cert.setMckoSubject(canonicalMckoSubject(required(mckoSubject, "Предмет МЦКО")));
        cert.setExamType(required(examType, "Тип экзамена"));
        cert.setDiagnosticDate(Objects.requireNonNull(diagnosticDate, "Дата диагностики обязательна"));
        cert.setExpiresAt(diagnosticDate.plusYears(3));
        cert.setLevel(required(level, "Уровень"));
        cert.setPublished(published);
        cert.setComment(comment);
        cert.setSource(MckoCertificateSource.MANUAL);
        if (scan != null && !scan.isEmpty()) {
            cert.setScanFileName(scan.getOriginalFilename());
            cert.setScanContentType(scan.getContentType());
            cert.setScanContent(scan.getBytes());
        }
        return toRow(certificateRepository.save(cert));
    }

    @Override
    @Transactional
    public MckoDtos.CertificateRow updateCertificate(Long id, Long teacherId, String mckoSubject, String examType,
                                                      LocalDate diagnosticDate, String level, boolean published, String comment,
                                                      MultipartFile scan, boolean removeScan) throws IOException {
        MckoCertificate cert = certificate(id);
        TeacherDirectoryEntry teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Педагог не найден"));
        cert.setTeacherId(teacher.getId());
        cert.setTeacherFioSnapshot(teacher.getFioTeacher());
        cert.setMckoSubject(canonicalMckoSubject(required(mckoSubject, "Предмет МЦКО")));
        cert.setExamType(required(examType, "Тип экзамена"));
        cert.setDiagnosticDate(Objects.requireNonNull(diagnosticDate, "Дата диагностики обязательна"));
        cert.setExpiresAt(diagnosticDate.plusYears(3));
        cert.setLevel(required(level, "Уровень"));
        cert.setPublished(published);
        cert.setComment(comment);
        if (removeScan) {
            cert.setScanFileName(null);
            cert.setScanContentType(null);
            cert.setScanContent(null);
        }
        if (scan != null && !scan.isEmpty()) {
            cert.setScanFileName(scan.getOriginalFilename());
            cert.setScanContentType(scan.getContentType());
            cert.setScanContent(scan.getBytes());
        }
        return toRow(certificateRepository.save(cert));
    }

    @Override
    public void deleteCertificate(Long id) {
        certificateRepository.deleteById(id);
    }

    @Override
    public MckoCertificate certificate(Long id) {
        return certificateRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Сертификат не найден"));
    }

    @Override
    public List<MckoDtos.SubjectMappingRow> mappings() {
        return mappingRepository.findAll().stream()
                .sorted(Comparator.comparing(MckoSubjectMapping::getMckoSubject, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MckoSubjectMapping::getGradeBand, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(MckoSubjectMapping::getSubjectName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::toMappingRow)
                .toList();
    }

    @Override
    @Transactional
    public MckoDtos.SubjectMappingRow createMapping(String mckoSubject, Long subjectId, String gradeBand) {
        SubjectCatalogEntry subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Предмет не найден"));
        String mcko = canonicalMckoSubject(required(mckoSubject, "Предмет МЦКО"));
        String band = canonicalGradeBand(gradeBand);
        if (mappingRepository.existsByMckoSubjectIgnoreCaseAndSubjectIdAndGradeBandIgnoreCase(mcko, subjectId, band)) {
            throw new IllegalArgumentException("Такое соответствие уже есть");
        }
        MckoSubjectMapping row = new MckoSubjectMapping();
        row.setMckoSubject(mcko);
        row.setSubjectId(subjectId);
        row.setSubjectName(subject.getSubjectName());
        row.setGradeBand(band);
        row.setIgnored(false);
        return toMappingRow(mappingRepository.save(row));
    }

    @Override
    @Transactional
    public MckoDtos.SubjectMappingRow ignoreSubject(String mckoSubject) {
        String mcko = canonicalMckoSubject(required(mckoSubject, "Предмет МЦКО"));
        List<MckoSubjectMapping> existing = mappingRepository.findAllByMckoSubjectIgnoreCase(mcko);
        existing.stream()
                .filter(row -> !row.isIgnored())
                .forEach(row -> mappingRepository.deleteById(row.getId()));
        Optional<MckoSubjectMapping> ignored = existing.stream().filter(MckoSubjectMapping::isIgnored).findFirst();
        if (ignored.isPresent()) {
            return toMappingRow(ignored.get());
        }
        MckoSubjectMapping row = new MckoSubjectMapping();
        row.setMckoSubject(mcko);
        row.setGradeBand("ALL");
        row.setIgnored(true);
        return toMappingRow(mappingRepository.save(row));
    }

    @Override
    public void deleteMapping(Long id) {
        mappingRepository.deleteById(id);
    }

    @Override
    public List<MckoDtos.EligibilityRow> eligibility(String academicYear) {
        Map<String, List<MckoCertificate>> byTeacherSubject = certificateRepository.findAll().stream()
                .collect(Collectors.groupingBy(row -> row.getTeacherId() + "|" + mckoSubjectKey(row.getMckoSubject())));
        List<MckoSubjectMapping> mappings = mappingRepository.findAll();
        Set<String> ignoredMckoSubjects = mappings.stream()
                .filter(MckoSubjectMapping::isIgnored)
                .map(row -> mckoSubjectKey(row.getMckoSubject()))
                .collect(Collectors.toSet());
        List<MckoSubjectMapping> activeMappings = mappings.stream()
                .filter(row -> !row.isIgnored())
                .filter(row -> !ignoredMckoSubjects.contains(mckoSubjectKey(row.getMckoSubject())))
                .toList();
        return manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(this::isCoreLoad)
                .filter(row -> row.getTeacherId() != null)
                .map(row -> eligibilityForLoad(row, byTeacherSubject, activeMappings))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        r -> r.teacherId() + "|" + r.subjectId() + "|" + normalize(r.subjectName()),
                        Function.identity(),
                        this::worseEligibility,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(MckoDtos.EligibilityRow::teacherFio, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(MckoDtos.EligibilityRow::subjectName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Override
    public Resource exportCertificates(String academicYear, String mode) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("МЦКО");
            Row header = sheet.createRow(0);
            String[] headers = {"Учитель", "Предмет МЦКО", "Тип экзамена", "Уровень", "Опубликован", "Дата сдачи", "Дата окончания", "Статус", "Предупреждение", "Источник", "Комментарий"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            List<MckoDtos.CertificateRow> rows = certificates(academicYear, mode);
            for (int i = 0; i < rows.size(); i++) {
                MckoDtos.CertificateRow r = rows.get(i);
                Row x = sheet.createRow(i + 1);
                x.createCell(0).setCellValue(nvl(r.teacherFio()));
                x.createCell(1).setCellValue(nvl(r.mckoSubject()));
                x.createCell(2).setCellValue(nvl(r.examType()));
                x.createCell(3).setCellValue(nvl(r.level()));
                x.createCell(4).setCellValue(r.published() ? "Да" : "Нет");
                x.createCell(5).setCellValue(r.diagnosticDate() == null ? "" : RU_DATE.format(r.diagnosticDate()));
                x.createCell(6).setCellValue(r.expiresAt() == null ? "" : RU_DATE.format(r.expiresAt()));
                x.createCell(7).setCellValue(nvl(r.status()));
                x.createCell(8).setCellValue(nvl(r.warning()));
                x.createCell(9).setCellValue(nvl(r.source()));
                x.createCell(10).setCellValue(nvl(r.comment()));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return new ByteArrayResource(out.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сформировать выгрузку МЦКО", ex);
        }
    }

    private MckoDtos.EligibilityRow eligibilityForLoad(ManualLoadEntry load,
                                                   Map<String, List<MckoCertificate>> byTeacherSubject,
                                                   List<MckoSubjectMapping> mappings) {
        Long teacherId = load.getTeacherId();
        String teacherFio = load.getFioTeacher();
        SubjectGroup subjectGroup = subjectGroup(load);
        if (teacherId == null) return null;
        List<String> mckoSubjects = new ArrayList<>();
        if (subjectGroup.primary()) {
            mckoSubjects.addAll(mappings.stream()
                    .filter(row -> mappingAppliesToLoad(row, load))
                    .map(MckoSubjectMapping::getMckoSubject)
                    .filter(this::isPrimaryMckoSubject)
                    .map(this::canonicalMckoSubject)
                    .toList());
        } else {
            mckoSubjects.addAll(mappings.stream()
                    .filter(row -> mappingAppliesToLoad(row, load))
                    .map(MckoSubjectMapping::getMckoSubject)
                    .map(this::canonicalMckoSubject)
                    .toList());
        }
        if (mckoSubjects.isEmpty()) {
            return null;
        }
        List<MckoCertificate> candidates = mckoSubjects.stream()
                .flatMap(mcko -> byTeacherSubject.getOrDefault(teacherId + "|" + mckoSubjectKey(mcko), List.of()).stream())
                .filter(this::isActiveCertificate)
                .toList();
        Optional<MckoCertificate> best = candidates.stream()
                .filter(this::isActivePassing)
                .max(this::compareCertificates);
        if (best.isEmpty()) {
            Optional<MckoCertificate> nonPassing = candidates.stream().max(this::compareCertificates);
            String message = nonPassing
                    .map(cert -> "МЦКО уровень " + nvl(cert.getLevel()))
                    .orElse("НЕТ МЦКО");
            MckoCertificate cert = nonPassing.orElse(null);
            return new MckoDtos.EligibilityRow(teacherId, teacherFio, subjectGroup.subjectId(), subjectGroup.subjectName(),
                    "MISSING", message, cert == null ? null : cert.getLevel(), cert == null ? null : cert.getDiagnosticDate(), cert == null ? null : cert.getExpiresAt());
        }
        MckoCertificate cert = best.get();
        String status = certificateStatus(cert);
        String message = eligibilityMessage(cert);
        return new MckoDtos.EligibilityRow(teacherId, teacherFio, subjectGroup.subjectId(), subjectGroup.subjectName(), status, message,
                cert.getLevel(), cert.getDiagnosticDate(), cert.getExpiresAt());
    }

    private boolean isCoreLoad(ManualLoadEntry row) {
        return row.getCurriculumPart() == null || row.getCurriculumPart() == CurriculumPart.CORE;
    }

    private boolean isPrimaryClass(String className) {
        Integer grade = gradeOfClass(className);
        return grade != null && grade >= 1 && grade <= 4;
    }

    private Integer gradeOfClass(String className) {
        String value = String.valueOf(className == null ? "" : className).trim();
        if (value.isBlank()) return null;
        try {
            int grade = Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
            return grade;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private SubjectGroup subjectGroup(ManualLoadEntry load) {
        if (isPrimaryClass(load.getClassName())) {
            return new SubjectGroup(PRIMARY_GROUP_SUBJECT_ID, "Начальная школа", true);
        }
        if (isMathSubject(load.getSubjectName())) {
            return new SubjectGroup(MATH_GROUP_SUBJECT_ID, "Математика", false);
        }
        return new SubjectGroup(load.getSubjectId(), load.getSubjectName(), false);
    }

    private boolean isMathSubject(String subjectName) {
        String value = normalize(subjectName);
        return value.contains("математ")
                || value.contains("алгебр")
                || value.contains("геометр")
                || value.contains("вероят")
                || value.contains("статист");
    }

    private boolean isPrimaryMckoSubject(String mckoSubject) {
        String value = normalize(mckoSubject);
        return value.equals(normalize(PRIMARY_META_SUBJECT)) || value.equals(normalize(PRIMARY_META_ALIAS));
    }

    private String canonicalMckoSubject(String mckoSubject) {
        String value = required(mckoSubject, "Предмет МЦКО");
        return normalize(value).equals(normalize(PRIMARY_META_ALIAS)) ? PRIMARY_META_SUBJECT : value.trim();
    }

    private String mckoSubjectKey(String mckoSubject) {
        if (mckoSubject == null || mckoSubject.isBlank()) return "";
        return normalize(canonicalMckoSubject(mckoSubject));
    }

    private boolean mappingAppliesToLoad(MckoSubjectMapping mapping, ManualLoadEntry load) {
        return Objects.equals(mapping.getSubjectId(), load.getSubjectId())
                && gradeBandMatches(mapping.getGradeBand(), load.getClassName());
    }

    private boolean gradeBandMatches(String gradeBand, String className) {
        String band = canonicalGradeBand(gradeBand);
        if ("ALL".equals(band)) return true;
        Integer grade = gradeOfClass(className);
        if (grade == null) return false;
        if ("1-4".equals(band)) return grade >= 1 && grade <= 4;
        if ("5-11".equals(band)) return grade >= 5 && grade <= 11;
        return true;
    }

    private String canonicalGradeBand(String gradeBand) {
        String value = String.valueOf(gradeBand == null ? "" : gradeBand).trim();
        if (value.equals("1-4") || value.equals("5-11")) return value;
        return "ALL";
    }

    private MckoDtos.EligibilityRow worseEligibility(MckoDtos.EligibilityRow a, MckoDtos.EligibilityRow b) {
        int status = Integer.compare(statusRank(a.status()), statusRank(b.status()));
        if (status != 0) return status >= 0 ? a : b;
        LocalDate aDate = a.expiresAt();
        LocalDate bDate = b.expiresAt();
        if (aDate == null) return a;
        if (bDate == null) return b;
        return aDate.isBefore(bDate) ? a : b;
    }

    private int statusRank(String status) {
        if ("MISSING".equals(status)) return 3;
        if ("WARNING".equals(status)) return 2;
        return 1;
    }

    private boolean isActivePassing(MckoCertificate cert) {
        return isActiveCertificate(cert)
                && levelRank(cert.getLevel()) > 0;
    }

    private boolean isActiveCertificate(MckoCertificate cert) {
        return cert.getDiagnosticDate() != null
                && cert.getExpiresAt() != null
                && !cert.getExpiresAt().isBefore(LocalDate.now());
    }

    private int compareCertificates(MckoCertificate a, MckoCertificate b) {
        int level = Integer.compare(levelRank(a.getLevel()), levelRank(b.getLevel()));
        if (level != 0) return level;
        return Comparator.nullsFirst(LocalDate::compareTo).compare(a.getDiagnosticDate(), b.getDiagnosticDate());
    }

    private MckoDtos.CertificateRow toRow(MckoCertificate cert) {
        return new MckoDtos.CertificateRow(
                cert.getId(),
                cert.getTeacherId(),
                cert.getTeacherFioSnapshot(),
                cert.getMckoSubject(),
                cert.getExamType(),
                cert.getDiagnosticDate(),
                cert.getExpiresAt(),
                cert.getLevel(),
                cert.isPublished(),
                cert.getSource().name(),
                cert.getComment(),
                cert.getScanFileName() != null,
                certificateStatus(cert),
                certificateWarning(cert)
        );
    }

    private String certificateStatus(MckoCertificate cert) {
        if (!isActivePassing(cert)) return "MISSING";
        if (!cert.isPublished() || !cert.getExpiresAt().isAfter(LocalDate.now().plusMonths(3))) return "WARNING";
        return "OK";
    }

    private String certificateWarning(MckoCertificate cert) {
        if (!isActivePassing(cert)) return "НЕТ МЦКО";
        List<String> warnings = new ArrayList<>();
        if (!cert.isPublished()) warnings.add(cert.getLevel() + ", результат не опубликован");
        if (!cert.getExpiresAt().isAfter(LocalDate.now().plusMonths(3))) warnings.add("МЦКО до " + RU_DATE.format(cert.getExpiresAt()));
        return String.join("; ", warnings);
    }

    private String eligibilityMessage(MckoCertificate cert) {
        String date = cert.getExpiresAt() == null ? "" : RU_DATE.format(cert.getExpiresAt());
        String base = date.isBlank() ? "МЦКО есть" : "МЦКО до " + date;
        String warning = certificateWarning(cert);
        if (warning == null || warning.isBlank()) return base;
        return warning.contains("МЦКО до") ? warning : base + "; " + warning;
    }

    private MckoDtos.SubjectMappingRow toMappingRow(MckoSubjectMapping row) {
        return new MckoDtos.SubjectMappingRow(row.getId(), row.getMckoSubject(), row.getSubjectId(), row.getSubjectName(),
                row.getGradeBand() == null ? "ALL" : row.getGradeBand(), row.isIgnored());
    }

    private Set<Long> teacherIdsForMode(String academicYear, String mode) {
        if (academicYear == null || academicYear.isBlank() || mode == null || mode.isBlank() || "all".equalsIgnoreCase(mode)) {
            return Set.of();
        }
        return manualLoadRepository.findAllByAcademicYear(academicYear).stream()
                .map(ManualLoadEntry::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private HeaderIndex headerIndex(Sheet sheet) {
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 20); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> index = new HashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String value = normalize(cell(row, c));
                if (!value.isBlank()) index.put(value, c);
            }
            if ((index.containsKey("фио педагогов") || index.containsKey("фио")) && index.containsKey("дата диагностики")) {
                return new HeaderIndex(r, index);
            }
        }
        throw new IllegalArgumentException("Не найдены заголовки выгрузки МЦКО");
    }

    private String cell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return RU_DATE.format(cell.getLocalDateTimeCellValue().toLocalDate());
        }
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.BOOLEAN) return cell.getBooleanCellValue() ? "Да" : "Нет";
        return cell.getCellType() == CellType.FORMULA ? cell.toString().trim() : cell.getStringCellValue().trim();
    }

    private LocalDate dateCell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell) || value > 10_000) {
                return DateUtil.getJavaDate(value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }
        String text = cell(row, index).trim();
        if (text.isBlank()) return null;
        for (DateTimeFormatter formatter : List.of(RU_DATE, DateTimeFormatter.ISO_LOCAL_DATE)) {
            try { return LocalDate.parse(text, formatter); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private boolean parsePublished(String value) {
        String normalized = normalize(value);
        return normalized.equals("да")
                || normalized.equals("опубликован")
                || normalized.equals("опубликовано")
                || normalized.equals("true")
                || normalized.equals("1");
    }

    private int levelRank(String level) {
        String normalized = normalize(level);
        if (normalized.contains("эксперт")) return 2;
        if (normalized.contains("высок")) return 1;
        return 0;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " обязателен");
        return value.trim();
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private void addWarning(List<String> warnings, String message) {
        if (warnings.size() < 30) {
            warnings.add(message);
        }
    }

    private record HeaderIndex(int headerRow, Map<String, Integer> columns) {
        int required(String name) {
            Integer index = columns.get(name);
            if (index == null) throw new IllegalArgumentException("Нет колонки: " + name);
            return index;
        }

        int requiredAny(String... names) {
            for (String name : names) {
                Integer index = columns.get(name);
                if (index != null) return index;
            }
            throw new IllegalArgumentException("Нет колонки: " + String.join(" / ", names));
        }
    }

    private record SubjectGroup(Long subjectId, String subjectName, boolean primary) {}
}
