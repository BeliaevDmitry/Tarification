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
                cert.setMckoSubject(subject.trim());
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
        cert.setMckoSubject(required(mckoSubject, "Предмет МЦКО"));
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
                        .thenComparing(MckoSubjectMapping::getSubjectName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toMappingRow)
                .toList();
    }

    @Override
    @Transactional
    public MckoDtos.SubjectMappingRow createMapping(String mckoSubject, Long subjectId) {
        SubjectCatalogEntry subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Предмет не найден"));
        String mcko = required(mckoSubject, "Предмет МЦКО");
        if (mappingRepository.existsByMckoSubjectIgnoreCaseAndSubjectId(mcko, subjectId)) {
            throw new IllegalArgumentException("Такое соответствие уже есть");
        }
        MckoSubjectMapping row = new MckoSubjectMapping();
        row.setMckoSubject(mcko);
        row.setSubjectId(subjectId);
        row.setSubjectName(subject.getSubjectName());
        return toMappingRow(mappingRepository.save(row));
    }

    @Override
    public void deleteMapping(Long id) {
        mappingRepository.deleteById(id);
    }

    @Override
    public List<MckoDtos.EligibilityRow> eligibility(String academicYear) {
        Map<String, List<MckoCertificate>> byTeacherSubject = certificateRepository.findAll().stream()
                .collect(Collectors.groupingBy(row -> row.getTeacherId() + "|" + normalize(row.getMckoSubject())));
        List<MckoSubjectMapping> mappings = mappingRepository.findAll();
        Map<Long, MckoSubjectMapping> uniqueLoadSubjects = mappings.stream()
                .filter(row -> row.getSubjectId() != null)
                .collect(Collectors.toMap(
                        MckoSubjectMapping::getSubjectId,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        return teacherRepository.findAll().stream()
                .filter(teacher -> teacher.getDismissalDate() == null)
                .flatMap(teacher -> uniqueLoadSubjects.values().stream()
                        .map(mapping -> eligibilityFor(teacher.getId(), teacher.getFioTeacher(), mapping.getSubjectId(),
                                mapping.getSubjectName(), byTeacherSubject, mappings)))
                .filter(Objects::nonNull)
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

    private MckoDtos.EligibilityRow eligibilityFor(Long teacherId, String teacherFio, Long subjectId, String subjectName,
                                                   Map<String, List<MckoCertificate>> byTeacherSubject,
                                                   List<MckoSubjectMapping> mappings) {
        if (teacherId == null) return null;
        List<String> mckoSubjects = mappings.stream()
                .filter(row -> Objects.equals(row.getSubjectId(), subjectId))
                .map(MckoSubjectMapping::getMckoSubject)
                .toList();
        if (mckoSubjects.isEmpty()) {
            return null;
        }
        Optional<MckoCertificate> best = mckoSubjects.stream()
                .flatMap(mcko -> byTeacherSubject.getOrDefault(teacherId + "|" + normalize(mcko), List.of()).stream())
                .filter(this::isActivePassing)
                .max(this::compareCertificates);
        if (best.isEmpty()) {
            return new MckoDtos.EligibilityRow(teacherId, teacherFio, subjectId, subjectName, "MISSING", "НЕТ МЦКО", null, null, null);
        }
        MckoCertificate cert = best.get();
        String status = certificateStatus(cert);
        String message = certificateWarning(cert);
        return new MckoDtos.EligibilityRow(teacherId, teacherFio, subjectId, subjectName, status, message,
                cert.getLevel(), cert.getDiagnosticDate(), cert.getExpiresAt());
    }

    private boolean isActivePassing(MckoCertificate cert) {
        return cert.getDiagnosticDate() != null
                && !cert.getExpiresAt().isBefore(LocalDate.now())
                && levelRank(cert.getLevel()) > 0;
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

    private MckoDtos.SubjectMappingRow toMappingRow(MckoSubjectMapping row) {
        return new MckoDtos.SubjectMappingRow(row.getId(), row.getMckoSubject(), row.getSubjectId(), row.getSubjectName());
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
}
