package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.dto.ContingentDtos;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.contingent.*;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.contingent.ContingentSnapshotRepository;
import org.school.personalLoad.repository.contingent.ContingentStudentEntryRepository;
import org.school.personalLoad.repository.contingent.ContingentWarningRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.ContingentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class ContingentServiceImpl implements ContingentService {

    private static final Pattern SNAPSHOT_DATE_PATTERN = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final DateTimeFormatter SNAPSHOT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String CLASS_COLUMN_NAME = "Номер и буква класса";

    private final AcademicYearService academicYearService;
    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentEntryRepository studentRepository;
    private final ContingentWarningRepository warningRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ContingentDtos.ImportResultResponse importSnapshot(String academicYear, MultipartFile file, LocalDate fallbackSnapshotDate) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");
        String year = resolveAcademicYear(academicYear);
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet("Данные");
            if (sheet == null) {
                throw new IllegalArgumentException("Лист «Данные» не найден");
            }

            LocalDate snapshotDate = extractSnapshotDate(sheet);
            if (snapshotDate == null) snapshotDate = fallbackSnapshotDate;
            if (snapshotDate == null) {
                throw new IllegalArgumentException("Не удалось распознать дату снимка из A2. Передайте snapshotDate вручную.");
            }

            snapshotRepository.findByAcademicYearAndSnapshotDate(year, snapshotDate).ifPresent(existing -> {
                warningRepository.deleteAllBySnapshot(existing);
                studentRepository.deleteAllBySnapshot(existing);
                snapshotRepository.delete(existing);
            });

            ContingentSnapshot snapshot = new ContingentSnapshot();
            snapshot.setAcademicYear(year);
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setImportedAt(LocalDateTime.now());
            snapshot.setSourceFileName(Optional.ofNullable(file.getOriginalFilename()).orElse("contingent.xlsx"));
            snapshot = snapshotRepository.save(snapshot);

            Row headersRow = sheet.getRow(2);
            if (headersRow == null) throw new IllegalArgumentException("Не найдены заголовки (строка 3)");
            Map<Integer, String> headers = readHeaders(headersRow);
            Integer classColumn = findColumn(headers, CLASS_COLUMN_NAME);
            if (classColumn == null) throw new IllegalArgumentException("Не найдена колонка «" + CLASS_COLUMN_NAME + "».");

            Map<String, String> buildingByClass = new HashMap<>();
            classroomLeadershipRepository.findAllByAcademicYear(year).forEach(c -> buildingByClass.put(c.getClassName(), c.getNumberSchoolBuilding()));

            Set<String> duplicateTracker = new HashSet<>();
            List<ContingentWarning> warnings = new ArrayList<>();
            List<ContingentStudentEntry> toSave = new ArrayList<>();
            for (int i = 3; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> fields = extractFields(row, headers);
                String fio = normalize(fields.getOrDefault("ФИО", ""));
                if (fio.isBlank()) continue;
                LocalDate birthDate = parseDate(fields.getOrDefault("дата рождения", fields.getOrDefault("Дата рождения", "")));
                String classRaw = normalize(fields.getOrDefault(CLASS_COLUMN_NAME, ""));
                String classNormalized = normalizeClass(classRaw);
                Integer parallel = ClassNameNormalizer.extractParallel(classNormalized);
                if (parallel == null) parallel = 0;

                String duplicateKey = fio.toLowerCase(Locale.ROOT) + "|" + String.valueOf(birthDate);
                if (!duplicateTracker.add(duplicateKey)) {
                    warnings.add(buildWarning(snapshot, ContingentWarningType.DUPLICATE_STUDENT_IN_FILE, classNormalized, "Дубликат ученика в файле: " + fio));
                }

                if (classNormalized.isBlank() || parallel == 0) {
                    warnings.add(buildWarning(snapshot, ContingentWarningType.CLASS_RECOGNITION_ERROR, classRaw, "Не удалось распознать класс: " + classRaw));
                }

                ContingentStudentEntry entry = new ContingentStudentEntry();
                entry.setSnapshot(snapshot);
                entry.setFullName(fio);
                entry.setBirthDate(birthDate);
                entry.setClassNameRaw(classRaw);
                entry.setClassNameNormalized(classNormalized);
                entry.setParallel(parallel);
                entry.setBuildingCode(buildingByClass.get(classNormalized));
                entry.setRawDataJson(objectMapper.writeValueAsString(fields));
                toSave.add(entry);
            }
            studentRepository.saveAll(toSave);
            warningRepository.saveAll(warnings);

            List<ContingentDtos.WarningResponse> recalculated = recalculateWarnings(snapshot.getId());
            return ContingentDtos.ImportResultResponse.builder()
                    .snapshot(snapshotDto(snapshot))
                    .warnings(recalculated)
                    .build();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось импортировать контингент: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContingentDtos.SnapshotResponse> listSnapshots(String academicYear) {
        String year = resolveAcademicYear(academicYear);
        return snapshotRepository.findAllByAcademicYearOrderBySnapshotDateDescImportedAtDesc(year).stream()
                .map(this::snapshotDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContingentDtos.StudentResponse> listStudents(Long snapshotId, String buildingCode, Integer parallel, String className, String query) {
        ContingentSnapshot snapshot = requireSnapshot(snapshotId);
        return studentRepository.findAllBySnapshot(snapshot).stream()
                .filter(s -> buildingCode == null || buildingCode.isBlank() || Objects.equals(normalize(buildingCode), normalize(s.getBuildingCode())))
                .filter(s -> parallel == null || Objects.equals(parallel, s.getParallel()))
                .filter(s -> className == null || className.isBlank() || normalize(s.getClassNameNormalized()).equals(normalize(ClassNameNormalizer.normalize(className))))
                .filter(s -> query == null || query.isBlank() || s.getFullName().toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT)))
                .map(this::studentDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContingentDtos.ClassSummaryResponse> classSummary(Long snapshotId) {
        ContingentSnapshot snapshot = requireSnapshot(snapshotId);
        Set<String> curriculumClasses = curriculumPlanEntryRepository.findAllByAcademicYear(snapshot.getAcademicYear()).stream()
                .map(CurriculumPlanEntry::getClassName)
                .map(ClassNameNormalizer::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<ContingentStudentEntry>> byClass = studentRepository.findAllBySnapshot(snapshot).stream()
                .collect(java.util.stream.Collectors.groupingBy(ContingentStudentEntry::getClassNameNormalized, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<ContingentDtos.ClassSummaryResponse> result = new ArrayList<>();
        byClass.forEach((className, rows) -> result.add(ContingentDtos.ClassSummaryResponse.builder()
                .className(className)
                .parallel(rows.stream().map(ContingentStudentEntry::getParallel).filter(Objects::nonNull).findFirst().orElse(0))
                .buildingCode(rows.stream().map(ContingentStudentEntry::getBuildingCode).filter(Objects::nonNull).findFirst().orElse(null))
                .studentsCount(rows.size())
                .curriculumMatched(curriculumClasses.contains(className))
                .build()));
        return result.stream().sorted(Comparator.comparing(ContingentDtos.ClassSummaryResponse::getClassName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContingentDtos.ParallelSummaryResponse> parallelSummary(Long snapshotId) {
        Map<Integer, List<ContingentDtos.ClassSummaryResponse>> byParallel = classSummary(snapshotId).stream()
                .collect(java.util.stream.Collectors.groupingBy(ContingentDtos.ClassSummaryResponse::getParallel));
        return byParallel.entrySet().stream()
                .map(entry -> ContingentDtos.ParallelSummaryResponse.builder()
                        .parallel(entry.getKey())
                        .classesCount(entry.getValue().size())
                        .studentsCount(entry.getValue().stream().mapToInt(ContingentDtos.ClassSummaryResponse::getStudentsCount).sum())
                        .build())
                .sorted(Comparator.comparing(ContingentDtos.ParallelSummaryResponse::getParallel))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContingentDtos.BuildingSummaryResponse> buildingSummary(Long snapshotId) {
        Map<String, List<ContingentDtos.ClassSummaryResponse>> byBuilding = classSummary(snapshotId).stream()
                .collect(java.util.stream.Collectors.groupingBy(row -> normalize(row.getBuildingCode())));
        return byBuilding.entrySet().stream()
                .map(entry -> ContingentDtos.BuildingSummaryResponse.builder()
                        .buildingCode(entry.getKey())
                        .classesCount(entry.getValue().size())
                        .studentsCount(entry.getValue().stream().mapToInt(ContingentDtos.ClassSummaryResponse::getStudentsCount).sum())
                        .build())
                .sorted(Comparator.comparing(ContingentDtos.BuildingSummaryResponse::getBuildingCode, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContingentDtos.WarningResponse> warnings(Long snapshotId) {
        return warningRepository.findAllBySnapshot(requireSnapshot(snapshotId)).stream()
                .map(this::warningDto)
                .toList();
    }

    @Override
    public List<ContingentDtos.WarningResponse> recalculateWarnings(Long snapshotId) {
        ContingentSnapshot snapshot = requireSnapshot(snapshotId);
        List<ContingentWarning> existing = warningRepository.findAllBySnapshot(snapshot).stream()
                .filter(row -> row.getType() == ContingentWarningType.DUPLICATE_STUDENT_IN_FILE || row.getType() == ContingentWarningType.CLASS_RECOGNITION_ERROR)
                .toList();
        warningRepository.deleteAllBySnapshot(snapshot);
        warningRepository.saveAll(existing);

        Set<String> contingentClasses = studentRepository.findAllBySnapshot(snapshot).stream()
                .map(ContingentStudentEntry::getClassNameNormalized)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> curriculumClasses = curriculumPlanEntryRepository.findAllByAcademicYear(snapshot.getAcademicYear()).stream()
                .map(CurriculumPlanEntry::getClassName)
                .map(ClassNameNormalizer::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<ContingentWarning> generated = new ArrayList<>();
        contingentClasses.stream().filter(cls -> !curriculumClasses.contains(cls))
                .forEach(cls -> generated.add(buildWarning(snapshot, ContingentWarningType.CLASS_WITHOUT_CURRICULUM, cls, "Класс есть в контингенте, но отсутствует в УП")));
        curriculumClasses.stream().filter(cls -> !contingentClasses.contains(cls))
                .forEach(cls -> generated.add(buildWarning(snapshot, ContingentWarningType.CURRICULUM_CLASS_WITHOUT_STUDENTS, cls, "Класс есть в УП, но отсутствует в контингенте")));
        warningRepository.saveAll(generated);
        return warningRepository.findAllBySnapshot(snapshot).stream().map(this::warningDto).toList();
    }

    private ContingentSnapshot requireSnapshot(Long snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Снимок контингента не найден: " + snapshotId));
    }

    private String resolveAcademicYear(String requestedYear) {
        return academicYearService.resolveByNameOrCurrent(requestedYear).getName();
    }

    private LocalDate extractSnapshotDate(Sheet sheet) {
        Row row = sheet.getRow(1);
        if (row == null) return null;
        Cell cell = row.getCell(0);
        String value = cell == null ? "" : cell.toString();
        Matcher matcher = SNAPSHOT_DATE_PATTERN.matcher(value);
        if (!matcher.find()) return null;
        return parseDate(matcher.group(1));
    }

    private LocalDate parseDate(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            return LocalDate.parse(value.trim(), SNAPSHOT_DATE_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<Integer, String> readHeaders(Row row) {
        Map<Integer, String> headers = new LinkedHashMap<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c == null) continue;
            String text = normalize(c.toString());
            if (!text.isBlank()) headers.put(i, text);
        }
        return headers;
    }

    private Integer findColumn(Map<Integer, String> headers, String expected) {
        return headers.entrySet().stream()
                .filter(entry -> normalize(entry.getValue()).equalsIgnoreCase(normalize(expected)))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private Map<String, String> extractFields(Row row, Map<Integer, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((idx, name) -> result.put(name, normalize(cellValue(row.getCell(idx)))));
        return result;
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? SNAPSHOT_DATE_FORMATTER.format(cell.getLocalDateTimeCellValue().toLocalDate()) : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.toString();
            default -> "";
        };
    }

    private String normalizeClass(String raw) {
        try {
            return ClassNameNormalizer.normalize(raw);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ContingentWarning buildWarning(ContingentSnapshot snapshot, ContingentWarningType type, String className, String message) {
        ContingentWarning warning = new ContingentWarning();
        warning.setSnapshot(snapshot);
        warning.setType(type);
        warning.setClassName(className);
        warning.setMessage(message);
        return warning;
    }

    private ContingentDtos.SnapshotResponse snapshotDto(ContingentSnapshot snapshot) {
        int students = studentRepository.findAllBySnapshot(snapshot).size();
        int warnings = warningRepository.findAllBySnapshot(snapshot).size();
        return ContingentDtos.SnapshotResponse.builder()
                .id(snapshot.getId())
                .academicYear(snapshot.getAcademicYear())
                .snapshotDate(snapshot.getSnapshotDate())
                .importedAt(snapshot.getImportedAt())
                .sourceFileName(snapshot.getSourceFileName())
                .studentsCount(students)
                .warningsCount(warnings)
                .build();
    }

    private ContingentDtos.StudentResponse studentDto(ContingentStudentEntry row) {
        Map<String, String> fields;
        try {
            fields = objectMapper.readValue(row.getRawDataJson(), new TypeReference<>() {});
        } catch (Exception ex) {
            fields = Map.of();
        }
        return ContingentDtos.StudentResponse.builder()
                .id(row.getId())
                .fullName(row.getFullName())
                .birthDate(row.getBirthDate())
                .classNameRaw(row.getClassNameRaw())
                .classNameNormalized(row.getClassNameNormalized())
                .parallel(row.getParallel())
                .buildingCode(row.getBuildingCode())
                .rawFields(fields)
                .build();
    }

    private ContingentDtos.WarningResponse warningDto(ContingentWarning row) {
        return ContingentDtos.WarningResponse.builder()
                .type(row.getType())
                .className(row.getClassName())
                .message(row.getMessage())
                .build();
    }
}

