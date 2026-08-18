package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.school.personalLoad.model.ClassSizeSource;
import org.school.personalLoad.model.ContingentImportIssue;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.StudentIdentityMatchStatus;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentImportIssueRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.service.ClassSizeService;
import org.school.personalLoad.service.ContingentService;
import org.school.personalLoad.service.StudentIdentityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ContingentServiceImpl implements ContingentService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository studentRepository;
    private final ContingentImportIssueRepository importIssueRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ClassSizeService classSizeService;
    private final StudentIdentityService studentIdentityService;

    @Override
    @Transactional
    public ContingentDtos.ImportResponse importSnapshot(String academicYear, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        if (isCsvUpload(file)) {
            try {
                return importExtendedMeshCsv(academicYear, file);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Не удалось обработать расширенную CSV-выгрузку МЭШ: " + rootMessage(e), e);
            }
        }

        int imported = 0;
        int skipped = 0;
        List<ContingentStudent> parsedStudents = new ArrayList<>();
        List<ParsedImportIssue> importIssues = new ArrayList<>();
        LocalDate snapshotDate = LocalDate.now();
        String importFormat = "AIS";

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Не найден первый лист");
            }
            DataFormatter formatter = new DataFormatter();

            Row dateRow = sheet.getRow(1);
            snapshotDate = parseSnapshotDate(dateRow == null ? "" : formatter.formatCellValue(dateRow.getCell(0)), snapshotDate);

            int headerRowIndex = findHeaderRow(sheet, formatter);
            if (headerRowIndex >= 0) {
                Row header = sheet.getRow(headerRowIndex);
                Map<String, Integer> indexByHeader = extractHeaderIndices(header, formatter);

                int recordCol = resolveColumnIndex(indexByHeader, "личное дело");
                int fioCol = resolveColumnIndex(indexByHeader, "фио");
                int classCol = resolveColumnIndex(indexByHeader, "номер и буква класса", "класс");

                if (recordCol < 0 || fioCol < 0 || classCol < 0) {
                    throw new IllegalArgumentException("В файле не найдены обязательные колонки: Личное дело №, ФИО, Номер и буква класса");
                }

                for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        continue;
                    }
                    String recordNumber = normalize(getCellValueByMarker(row, indexByHeader, formatter, "личное дело"));
                    String fullName = normalize(getCellValueByMarker(row, indexByHeader, formatter, "фио"));
                    String className = normalizePlacementName(getCellValueByMarker(row, indexByHeader, formatter, "номер и буква класса", "класс"));

                    if (recordNumber.isBlank() && fullName.isBlank() && className.isBlank()) {
                        continue;
                    }
                    if (fullName.isBlank() || className.isBlank()) {
                        skipped++;
                        LinkedHashMap<String, String> rawValues = buildRawValues(indexByHeader, row, formatter);
                        importIssues.add(skippedRowIssue(
                                i + 1, fullName, className, toJson(rawValues), fullName.isBlank(), className.isBlank()
                        ));
                        continue;
                    }

                    LinkedHashMap<String, String> rawValues = buildRawValues(indexByHeader, row, formatter);
                    ContingentStudent student = createBlankStudent(academicYear, recordNumber, fullName, className, toJson(rawValues));
                    student.setEnrollmentDate(getCellValueByMarker(row, indexByHeader, formatter, "заведено"));
                    student.setGender(getCellValueByMarker(row, indexByHeader, formatter, "пол"));
                    student.setBirthDate(getCellValueByMarker(row, indexByHeader, formatter, "родился"));
                    student.setBirthCertificate(getCellValueByMarker(row, indexByHeader, formatter, "свидетельство о рождении"));
                    student.setSocialCard(getCellValueByMarker(row, indexByHeader, formatter, "социальная карта"));
                    student.setPensionInsurance(getCellValueByMarker(row, indexByHeader, formatter, "полис пенсионного страхования"));
                    student.setMedicalInsurance(getCellValueByMarker(row, indexByHeader, formatter, "полис медицинского страхования"));
                    student.setPassport(getCellValueByMarker(row, indexByHeader, formatter, "паспорт"));
                    student.setCitizenship(getCellValueByMarker(row, indexByHeader, formatter, "гражданство"));
                    student.setAdditionalInfoCode(getCellValueByMarker(row, indexByHeader, formatter, "дополнительные сведения"));
                    student.setAoopVariant(getCellValueByMarker(row, indexByHeader, formatter, "вариант аооп"));
                    student.setEducationReceivingForm(getCellValueByMarker(row, indexByHeader, formatter, "форме получения образования"));
                    student.setEducationForm(getCellValueByMarker(row, indexByHeader, formatter, "форме обучения"));
                    student.setAlphabetBookNumber(getCellValueByMarker(row, indexByHeader, formatter, "номер алфавитной книги"));
                    student.setRegistrationAddress(getCellValueByMarker(row, indexByHeader, formatter, "регистрация по месту жительства"));
                    student.setTemporaryRegistrationAddress(getCellValueByMarker(row, indexByHeader, formatter, "регистрация по месту пребывания"));
                    student.setActualAddress(getCellValueByMarker(row, indexByHeader, formatter, "адрес фактического проживания"));
                    student.setPhone(getCellValueByMarker(row, indexByHeader, formatter, "телефон"));
                    student.setEmail(getCellValueByMarker(row, indexByHeader, formatter, "email"));
                    student.setOnVshuFrom(getCellValueByMarker(row, indexByHeader, formatter, "на вшу с"));
                    student.setOnVshuReason(getCellValueByMarker(row, indexByHeader, formatter, "основание(я) постановки на вшу", "основание постановки на вшу"));
                    student.setOnKdnFrom(getCellValueByMarker(row, indexByHeader, formatter, "на учете кдн с"));
                    student.setOnKdnReason(getCellValueByMarker(row, indexByHeader, formatter, "основание(я) постановки на учет кдн", "основание постановки на учет кдн"));
                    student.setOnPdnFrom(getCellValueByMarker(row, indexByHeader, formatter, "на учете пдн с"));
                    student.setOnPdnReason(getCellValueByMarker(row, indexByHeader, formatter, "основание(я) постановки на учет пдн", "основание постановки на учет пдн"));
                    student.setRemovedFromVshu(getCellValueByMarker(row, indexByHeader, formatter, "снят с вшу"));
                    student.setRemovedFromVshuReason(getCellValueByMarker(row, indexByHeader, formatter, "основание снятия с вшу"));
                    parsedStudents.add(student);
                    imported++;
                }
            } else {
                importFormat = "COMPACT";
                CompactLayout layout = detectCompactLayout(sheet, formatter);
                if (layout == null) {
                    throw new IllegalArgumentException(
                            "Не удалось распознать файл. Ожидается полная выгрузка или два столбца: ФИО и класс/группа"
                    );
                }
                for (int i = layout.firstDataRow(); i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        continue;
                    }
                    String fullName = normalize(getCellValue(row, layout.fioColumn(), formatter));
                    String placementName = normalizePlacementName(getCellValue(row, layout.placementColumn(), formatter));
                    if (fullName.isBlank() && placementName.isBlank()) {
                        continue;
                    }
                    if (fullName.isBlank() || placementName.isBlank()) {
                        skipped++;
                        LinkedHashMap<String, String> rawValues = new LinkedHashMap<>();
                        rawValues.put("ФИО", fullName);
                        rawValues.put("Класс или группа", placementName);
                        importIssues.add(skippedRowIssue(
                                i + 1, fullName, placementName, toJson(rawValues), fullName.isBlank(), placementName.isBlank()
                        ));
                        continue;
                    }
                    LinkedHashMap<String, String> rawValues = new LinkedHashMap<>();
                    rawValues.put("ФИО", fullName);
                    rawValues.put("Класс или группа", placementName);
                    rawValues.put("Формат", "Сокращённая выгрузка МЭШ");
                    parsedStudents.add(createBlankStudent(academicYear, "", fullName, placementName, toJson(rawValues)));
                    imported++;
                }
            }
            if (parsedStudents.isEmpty()) {
                throw new IllegalArgumentException("В файле не найдено ни одной строки с ФИО и классом/группой");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось обработать файл контингента: " + rootMessage(e), e);
        }

        return saveImportedSnapshot(
                academicYear, file, snapshotDate, importFormat, parsedStudents, imported, skipped, importIssues
        );
    }

    private ContingentDtos.ImportResponse importExtendedMeshCsv(String academicYear, MultipartFile file) throws IOException {
        List<List<String>> rows;
        try (InputStream inputStream = file.getInputStream()) {
            rows = readSemicolonCsv(inputStream);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV-файл пуст");
        }

        List<String> headers = rows.get(0).stream().map(this::cleanCsvHeader).toList();
        Map<String, Integer> indexByHeader = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            if (!header.isBlank()) {
                indexByHeader.putIfAbsent(header, index);
            }
        }

        int fioColumn = resolveColumnIndex(indexByHeader, "фио ребёнка", "фио ребенка", "фио");
        int birthDateColumn = resolveColumnIndex(indexByHeader, "дата рождения");
        int placementColumn = resolveColumnIndex(indexByHeader, "класс / группа", "класс/группа", "класс");
        if (fioColumn < 0 || birthDateColumn < 0 || placementColumn < 0) {
            throw new IllegalArgumentException(
                    "В CSV нужны колонки: ФИО ребёнка, Дата рождения и Класс / группа"
            );
        }

        List<ContingentStudent> parsedStudents = new ArrayList<>();
        List<ParsedImportIssue> importIssues = new ArrayList<>();
        int skipped = 0;
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            String fullName = normalize(csvValue(row, fioColumn));
            String birthDate = normalize(csvValue(row, birthDateColumn));
            String placementName = normalizePlacementName(csvValue(row, placementColumn));
            if (fullName.isBlank() && birthDate.isBlank() && placementName.isBlank()) {
                continue;
            }
            if (fullName.isBlank() || placementName.isBlank()) {
                skipped++;
                LinkedHashMap<String, String> rawValues = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    String header = headers.get(column);
                    if (!header.isBlank()) {
                        rawValues.put(header, csvValue(row, column));
                    }
                }
                importIssues.add(skippedRowIssue(
                        rowIndex + 1, fullName, placementName, toJson(rawValues), fullName.isBlank(), placementName.isBlank()
                ));
                continue;
            }

            LinkedHashMap<String, String> rawValues = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                String header = headers.get(column);
                if (!header.isBlank()) {
                    rawValues.put(header, csvValue(row, column));
                }
            }
            ContingentStudent student = createBlankStudent(
                    academicYear, "", fullName, placementName, toJson(rawValues)
            );
            student.setBirthDate(birthDate);
            student.setGender(csvValueByMarker(row, indexByHeader, "пол"));
            student.setPhone(csvValueByMarker(row, indexByHeader, "телефон ребёнка", "телефон ребенка"));
            student.setEmail(csvValueByMarker(row, indexByHeader, "email ребёнка", "email ребенка"));
            student.setPensionInsurance(csvValueByMarker(row, indexByHeader, "снилс ребёнка", "снилс ребенка"));
            RepresentativeContact representative = firstRepresentative(row, indexByHeader);
            student.setRepresentativeName(representative.name());
            student.setRepresentativePhone(representative.phone());
            parsedStudents.add(student);
        }
        if (parsedStudents.isEmpty()) {
            throw new IllegalArgumentException("В CSV не найдено ни одной строки с ФИО и классом/группой");
        }

        return saveImportedSnapshot(
                academicYear,
                file,
                parseSnapshotDateFromFileName(file.getOriginalFilename(), LocalDate.now()),
                "MES_EXTENDED_CSV",
                parsedStudents,
                parsedStudents.size(),
                skipped,
                importIssues
        );
    }

    private ContingentDtos.ImportResponse saveImportedSnapshot(String academicYear,
                                                                MultipartFile file,
                                                                LocalDate snapshotDate,
                                                                String importFormat,
                                                                List<ContingentStudent> parsedStudents,
                                                                int imported,
                                                                int skipped,
                                                                List<ParsedImportIssue> parsedIssues) {
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setAcademicYear(academicYear);
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setSourceFileName(file.getOriginalFilename() == null ? "contingent" : file.getOriginalFilename());
        snapshot.setImportFormat(importFormat);
        snapshot.setSkippedRows(skipped);
        snapshot.setTotalStudents(imported);
        ContingentSnapshot savedSnapshot = snapshotRepository.save(snapshot);

        List<ContingentImportIssue> issues = (parsedIssues == null ? List.<ParsedImportIssue>of() : parsedIssues)
                .stream()
                .map(issue -> toEntity(savedSnapshot.getId(), issue))
                .toList();
        if (!issues.isEmpty()) {
            importIssueRepository.saveAll(issues);
        }

        parsedStudents.forEach(student -> student.setSnapshotId(savedSnapshot.getId()));
        StudentIdentityService.LinkResult linkResult = studentIdentityService.linkStudents(savedSnapshot, parsedStudents);
        studentRepository.saveAll(parsedStudents);

        ContingentDtos.ImportResponse response = new ContingentDtos.ImportResponse();
        response.setSnapshotId(savedSnapshot.getId());
        response.setSnapshotDate(savedSnapshot.getSnapshotDate());
        response.setImportFormat(importFormat);
        response.setImportedStudents(imported);
        response.setSchoolStudents((int) parsedStudents.stream().filter(student -> isSchoolClassName(student.getClassName())).count());
        response.setKindergartenStudents((int) parsedStudents.stream().filter(student -> isKindergartenPlacement(student.getClassName())).count());
        response.setUnassignedStudents(imported - response.getSchoolStudents() - response.getKindergartenStudents());
        response.setSkippedRows(skipped);
        response.setLinkedStudents(linkResult.linked());
        response.setCreatedStudentProfiles(linkResult.created());
        response.setAmbiguousStudents(linkResult.ambiguous());
        response.setProblems(getProblems(academicYear, savedSnapshot.getId()));
        response.setMismatchCount(countMismatchRows(parsedStudents, response.getProblems()) + skipped);
        return response;
    }

    private boolean isCsvUpload(MultipartFile file) {
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        return fileName.endsWith(".csv") || contentType.contains("text/csv") || contentType.contains("application/csv");
    }

    private ParsedImportIssue skippedRowIssue(int rowNumber,
                                               String fullName,
                                               String placementName,
                                               String rawPayload,
                                               boolean missingFullName,
                                               boolean missingPlacement) {
        List<String> missing = new ArrayList<>();
        if (missingFullName) missing.add("ФИО");
        if (missingPlacement) missing.add("класс или группа");
        return new ParsedImportIssue(
                rowNumber,
                "SKIPPED_ROW",
                "Строка пропущена: не заполнено " + String.join(" и ", missing),
                normalize(fullName),
                normalizePlacementName(placementName),
                normalize(rawPayload).isBlank() ? "{}" : rawPayload
        );
    }

    private ContingentImportIssue toEntity(Long snapshotId, ParsedImportIssue source) {
        ContingentImportIssue issue = new ContingentImportIssue();
        issue.setSnapshotId(snapshotId);
        issue.setSourceRowNumber(source.rowNumber());
        issue.setIssueType(source.issueType());
        issue.setMessage(source.message());
        issue.setFullName(source.fullName());
        issue.setPlacementName(source.placementName());
        issue.setRawPayload(source.rawPayload());
        return issue;
    }

    private int countMismatchRows(List<ContingentStudent> students, List<ContingentDtos.ImportProblem> problems) {
        Set<String> unknownClasses = (problems == null ? List.<ContingentDtos.ImportProblem>of() : problems)
                .stream()
                .map(ContingentDtos.ImportProblem::getClassName)
                .map(ClassNameNormalizer::normalize)
                .collect(java.util.stream.Collectors.toSet());
        return (int) (students == null ? List.<ContingentStudent>of() : students).stream()
                .filter(student -> isOutsideOrganization(student.getClassName())
                        || student.getIdentityMatchStatus() == StudentIdentityMatchStatus.AMBIGUOUS
                        || student.getStudentId() == null
                        || unknownClasses.contains(ClassNameNormalizer.normalize(student.getClassName())))
                .count();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private List<List<String>> readSemicolonCsv(InputStream inputStream) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean firstCharacter = true;
        try (PushbackReader reader = new PushbackReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8), 1)) {
            int codePoint;
            while ((codePoint = reader.read()) != -1) {
                char character = (char) codePoint;
                if (firstCharacter) {
                    firstCharacter = false;
                    if (character == '\uFEFF') {
                        continue;
                    }
                }
                if (character == '"') {
                    if (quoted) {
                        int next = reader.read();
                        if (next == '"') {
                            value.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                reader.unread(next);
                            }
                        }
                    } else if (value.length() == 0) {
                        quoted = true;
                    } else {
                        value.append(character);
                    }
                } else if (character == ';' && !quoted) {
                    row.add(value.toString());
                    value.setLength(0);
                } else if ((character == '\r' || character == '\n') && !quoted) {
                    if (character == '\r') {
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.unread(next);
                        }
                    }
                    row.add(value.toString());
                    value.setLength(0);
                    if (row.stream().anyMatch(cell -> !cell.isBlank())) {
                        rows.add(new ArrayList<>(row));
                    }
                    row.clear();
                } else {
                    value.append(character);
                }
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("В CSV не закрыта кавычка");
        }
        if (value.length() > 0 || !row.isEmpty()) {
            row.add(value.toString());
            if (row.stream().anyMatch(cell -> !cell.isBlank())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private String cleanCsvHeader(String value) {
        return normalize(value).replace("\uFEFF", "");
    }

    private String csvValue(List<String> row, int index) {
        return row == null || index < 0 || index >= row.size() ? "" : normalize(row.get(index));
    }

    private String csvValueByMarker(List<String> row,
                                    Map<String, Integer> indexByHeader,
                                    String... markers) {
        return csvValue(row, resolveColumnIndex(indexByHeader, markers));
    }

    private RepresentativeContact firstRepresentative(List<String> row, Map<String, Integer> indexByHeader) {
        RepresentativeContact firstPartial = null;
        for (int index = 1; index <= 20; index++) {
            String name = csvValueByMarker(row, indexByHeader,
                    "представитель " + index + " — фио", "представитель " + index + " - фио");
            String phone = csvValueByMarker(row, indexByHeader,
                    "представитель " + index + " — телефон", "представитель " + index + " - телефон");
            if (!name.isBlank() && !phone.isBlank()) {
                return new RepresentativeContact(name, phone);
            }
            if (firstPartial == null && (!name.isBlank() || !phone.isBlank())) {
                firstPartial = new RepresentativeContact(name, phone);
            }
        }
        return firstPartial == null ? new RepresentativeContact("", "") : firstPartial;
    }

    private LocalDate parseSnapshotDateFromFileName(String fileName, LocalDate fallback) {
        Matcher matcher = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})")
                .matcher(Optional.ofNullable(fileName).orElse(""));
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public List<ContingentDtos.SnapshotListItem> listSnapshots(String academicYear) {
        return snapshotRepository.findAllByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).stream()
                .map(snapshot -> {
                    ContingentDtos.SnapshotListItem item = new ContingentDtos.SnapshotListItem();
                    item.setId(snapshot.getId());
                    item.setSnapshotDate(snapshot.getSnapshotDate());
                    item.setImportedAt(snapshot.getImportedAt());
                    item.setSourceFileName(snapshot.getSourceFileName());
                    item.setImportFormat(snapshot.getImportFormat());
                    item.setSkippedRows(snapshot.getSkippedRows());
                    item.setTotalStudents(snapshot.getTotalStudents());
                    return item;
                })
                .toList();
    }

    @Override
    public ContingentDtos.StatsResponse getStats(String academicYear, LocalDate snapshotDate) {
        ContingentSnapshot snapshot = resolveSnapshot(academicYear, snapshotDate);
        List<String> classNames = studentRepository.findClassNamesBySnapshotId(snapshot.getId());

        record ClassPlacement(String buildingCode, String buildingName, String address) {}

        Map<String, ClassPlacement> placementByClass = new HashMap<>();
        classroomLeadershipRepository.findAllByAcademicYear(academicYear).forEach(entry -> {
            String className = ClassNameNormalizer.normalize(entry.getClassName());
            String buildingCode = normalize(entry.getNumberSchoolBuilding());
            String buildingName = schoolBuildingRepository.findByCode(buildingCode)
                    .map(b -> normalize(b.getName()))
                    .filter(name -> !name.isBlank())
                    .orElse(buildingCode);
            String address = normalize(entry.getCampusAddress());
            if (address.isBlank()) {
                address = schoolBuildingRepository.findByCode(buildingCode)
                        .map(b -> normalize(b.getAddress()))
                        .filter(a -> !a.isBlank())
                        .orElse("Адрес не указан");
            }
            placementByClass.put(className, new ClassPlacement(buildingCode, buildingName, address));
        });

        Map<String, Integer> importedCountByClass = new TreeMap<>();
        Map<String, Integer> kindergartenCountByGroup = new TreeMap<>((left, right) ->
                left.compareToIgnoreCase(right));
        int unassignedChildren = 0;
        for (String rawClassName : classNames) {
            String className = normalizePlacementName(rawClassName);
            if (isSchoolClassName(className)) {
                importedCountByClass.merge(className, 1, Integer::sum);
            } else if (isKindergartenPlacement(className)) {
                kindergartenCountByGroup.merge(className, 1, Integer::sum);
            } else {
                unassignedChildren++;
            }
        }

        Set<String> allClassNames = new TreeSet<>(this::compareClassNames);
        allClassNames.addAll(placementByClass.keySet());
        allClassNames.addAll(importedCountByClass.keySet());

        Map<Integer, Integer> totalByParallel = new TreeMap<>();
        Map<Integer, Integer> classCountByParallel = new TreeMap<>();
        Map<String, Integer> totalByAddress = new LinkedHashMap<>();
        Map<String, List<ContingentDtos.ClassTotal>> classesByAddress = new LinkedHashMap<>();

        for (String className : allClassNames) {
            int parallel = extractParallel(className);
            if (parallel < 0) {
                continue;
            }

            ClassPlacement placement = placementByClass.getOrDefault(className, new ClassPlacement("НЕОПР", "НЕОПР", "Адрес не указан"));
            String addressKey = placement.buildingCode + "|" + placement.buildingName + "|" + placement.address;
            int students = importedCountByClass.getOrDefault(className, 30);

            List<ContingentDtos.ClassTotal> classTotals = classesByAddress.computeIfAbsent(addressKey, k -> new ArrayList<>());
            ContingentDtos.ClassTotal created = new ContingentDtos.ClassTotal();
            created.setParallel(parallel);
            created.setClassName(className);
            created.setStudents(students);
            classTotals.add(created);

            totalByParallel.merge(parallel, students, Integer::sum);
            classCountByParallel.merge(parallel, 1, Integer::sum);
            totalByAddress.merge(addressKey, students, Integer::sum);
        }

        List<String> sortedAddressKeys = new ArrayList<>(classesByAddress.keySet());
        sortedAddressKeys.sort(Comparator
                .comparing((String key) -> key.split("\\|")[0])
                .thenComparing(key -> key.split("\\|")[2]));

        Map<String, List<ContingentDtos.AddressColumn>> addressesByBuilding = new LinkedHashMap<>();
        Map<String, Integer> totalByBuilding = new LinkedHashMap<>();

        for (String addressKey : sortedAddressKeys) {
            String[] split = addressKey.split("\\|", 3);
            String buildingCode = split[0];
            String buildingName = split[1];
            String address = split[2];

            List<ContingentDtos.ClassTotal> classTotals = classesByAddress.get(addressKey);
            classTotals.sort(Comparator.comparing(ContingentDtos.ClassTotal::getParallel)
                    .thenComparing(ContingentDtos.ClassTotal::getClassName));

            ContingentDtos.AddressColumn addressColumn = new ContingentDtos.AddressColumn();
            addressColumn.setAddress(address);
            addressColumn.setClasses(classTotals);
            addressColumn.setTotalStudents(totalByAddress.getOrDefault(addressKey, 0));

            String buildingKey = buildingCode + "|" + buildingName;
            addressesByBuilding.computeIfAbsent(buildingKey, k -> new ArrayList<>()).add(addressColumn);
            totalByBuilding.merge(buildingKey, addressColumn.getTotalStudents(), Integer::sum);
        }

        List<ContingentDtos.BuildingColumn> columns = new ArrayList<>();
        addressesByBuilding.forEach((buildingKey, addressColumns) -> {
            String[] split = buildingKey.split("\\|", 2);
            ContingentDtos.BuildingColumn column = new ContingentDtos.BuildingColumn();
            column.setBuildingCode(split[0]);
            column.setBuildingName(split[1]);
            column.setAddresses(addressColumns);
            column.setTotalStudents(totalByBuilding.getOrDefault(buildingKey, 0));
            columns.add(column);
        });

        List<ContingentDtos.ParallelTotal> parallelTotals = totalByParallel.entrySet().stream().map(entry -> {
            ContingentDtos.ParallelTotal total = new ContingentDtos.ParallelTotal();
            total.setParallel(entry.getKey());
            total.setTotalStudents(entry.getValue());
            total.setTotalClasses(classCountByParallel.getOrDefault(entry.getKey(), 0));
            return total;
        }).toList();

        ContingentDtos.StatsResponse response = new ContingentDtos.StatsResponse();
        response.setSnapshotId(snapshot.getId());
        response.setSnapshotDate(snapshot.getSnapshotDate());
        response.setTotalImportedChildren(classNames.size());
        response.setTotalSchoolChildren(importedCountByClass.values().stream().mapToInt(Integer::intValue).sum());
        response.setTotalKindergartenChildren(kindergartenCountByGroup.values().stream().mapToInt(Integer::intValue).sum());
        response.setTotalUnassignedChildren(unassignedChildren);
        response.setTotalStudents(totalByParallel.values().stream().mapToInt(Integer::intValue).sum());
        response.setTotalClassesNoo(classCountByStage(classCountByParallel, 1, 4));
        response.setTotalClassesOoo(classCountByStage(classCountByParallel, 5, 9));
        response.setTotalClassesSoo(classCountByStage(classCountByParallel, 10, 11));
        response.setParallels(new ArrayList<>(totalByParallel.keySet()));
        response.setColumns(columns);
        response.setParallelTotals(parallelTotals);
        response.setKindergartenGroups(kindergartenCountByGroup.entrySet().stream().map(entry -> {
            ContingentDtos.KindergartenGroupTotal group = new ContingentDtos.KindergartenGroupTotal();
            group.setGroupName(entry.getKey());
            group.setStudents(entry.getValue());
            return group;
        }).toList());
        return response;
    }

    private int classCountByStage(Map<Integer, Integer> classCountByParallel, int from, int to) {
        if (classCountByParallel == null) {
            return 0;
        }
        return classCountByParallel.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey() >= from && entry.getKey() <= to)
                .mapToInt(entry -> entry.getValue() == null ? 0 : entry.getValue())
                .sum();
    }


    private int compareClassNames(String first, String second) {
        int firstParallel = extractParallel(first);
        int secondParallel = extractParallel(second);
        int parallelCompare = Integer.compare(firstParallel, secondParallel);
        return parallelCompare != 0 ? parallelCompare : String.valueOf(first).compareTo(String.valueOf(second));
    }


    @Override
    public byte[] exportStats(String academicYear, LocalDate snapshotDate) {
        ContingentDtos.StatsResponse stats = getStats(academicYear, snapshotDate);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ContingentWorkbookStyles styles = createContingentStyles(workbook);
            writeBuildingStatsSheet(workbook, stats, styles);
            writeAddressStatsSheet(workbook, stats, styles);
            writeKindergartenStatsSheet(workbook, stats, styles);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось экспортировать численность", e);
        }
    }

    private void writeBuildingStatsSheet(Workbook workbook, ContingentDtos.StatsResponse stats, ContingentWorkbookStyles styles) {
        Sheet sheet = workbook.createSheet("По СП");
        int lastCol = Math.max(4, 2 + stats.getColumns().stream()
                .mapToInt(building -> Math.max(0, building.getAddresses().size()) * 2)
                .sum());

        int rowIdx = 0;
        Row title = sheet.createRow(rowIdx++);
        createCell(title, 0, "Численность по СП на " + stats.getSnapshotDate(), styles.title());
        merge(sheet, new CellRangeAddress(0, 0, 0, lastCol));

        Row h1 = sheet.createRow(rowIdx++);
        Row h2 = sheet.createRow(rowIdx++);
        createCell(h1, 0, "Параллель", styles.header());
        createCell(h1, 1, "Всего детей", styles.header());
        createCell(h2, 0, "", styles.header());
        createCell(h2, 1, "", styles.header());
        createCell(h1, 2, "Всего классов", styles.header());
        createCell(h2, 2, "", styles.header());
        merge(sheet, new CellRangeAddress(1, 2, 0, 0));
        merge(sheet, new CellRangeAddress(1, 2, 1, 1));
        merge(sheet, new CellRangeAddress(1, 2, 2, 2));

        int col = 3;
        for (ContingentDtos.BuildingColumn building : stats.getColumns()) {
            int start = col;
            for (ContingentDtos.AddressColumn address : building.getAddresses()) {
                createCell(h2, col, address.getAddress(), styles.subHeader());
                createCell(h2, col + 1, "", styles.subHeader());
                merge(sheet, new CellRangeAddress(2, 2, col, col + 1));
                col += 2;
            }
            if (col - 1 >= start) {
                createCell(h1, start, building.getBuildingName(), styles.header());
                for (int c = start + 1; c < col; c++) {
                    createCell(h1, c, "", styles.header());
                }
                merge(sheet, new CellRangeAddress(1, 1, start, col - 1));
            }
        }

        Map<Integer, Integer> totalByParallel = stats.getParallelTotals().stream()
                .collect(java.util.stream.Collectors.toMap(ContingentDtos.ParallelTotal::getParallel, ContingentDtos.ParallelTotal::getTotalStudents));
        Map<Integer, Integer> classCountByParallel = stats.getParallelTotals().stream()
                .collect(java.util.stream.Collectors.toMap(ContingentDtos.ParallelTotal::getParallel, total -> total.getTotalClasses() == null ? 0 : total.getTotalClasses()));

        for (Integer parallel : stats.getParallels()) {
            List<List<ContingentDtos.ClassTotal>> perAddress = new ArrayList<>();
            for (ContingentDtos.BuildingColumn building : stats.getColumns()) {
                for (ContingentDtos.AddressColumn address : building.getAddresses()) {
                    perAddress.add(classesForParallel(address.getClasses(), parallel));
                }
            }
            rowIdx = writeParallelRows(sheet, rowIdx, parallel, totalByParallel.getOrDefault(parallel, 0), classCountByParallel.getOrDefault(parallel, 0), perAddress, styles);
        }

        Row totalRow = sheet.createRow(rowIdx);
        createCell(totalRow, 0, "ИТОГО", styles.total());
        createCell(totalRow, 1, stats.getTotalStudents(), styles.total());
        createCell(totalRow, 2, stats.getParallelTotals().stream().mapToInt(total -> total.getTotalClasses() == null ? 0 : total.getTotalClasses()).sum(), styles.total());
        int totalCol = 3;
        for (ContingentDtos.BuildingColumn building : stats.getColumns()) {
            for (ContingentDtos.AddressColumn address : building.getAddresses()) {
                createCell(totalRow, totalCol, "", styles.total());
                createCell(totalRow, totalCol + 1, address.getTotalStudents(), styles.total());
                totalCol += 2;
            }
        }

        writeStageClassSummaryRow(sheet, rowIdx + 1, totalCol, stats, styles);
        finishContingentSheet(sheet, Math.max(totalCol, 4), 3);
    }

    private void writeAddressStatsSheet(Workbook workbook, ContingentDtos.StatsResponse stats, ContingentWorkbookStyles styles) {
        Sheet sheet = workbook.createSheet("По адресам");
        List<AddressStatsColumn> addresses = addressStatsColumns(stats);
        int lastCol = Math.max(4, 2 + addresses.size() * 2);

        int rowIdx = 0;
        Row title = sheet.createRow(rowIdx++);
        createCell(title, 0, "Численность по адресам на " + stats.getSnapshotDate(), styles.title());
        merge(sheet, new CellRangeAddress(0, 0, 0, lastCol));

        Row header = sheet.createRow(rowIdx++);
        createCell(header, 0, "Параллель", styles.header());
        createCell(header, 1, "Всего детей", styles.header());
        createCell(header, 2, "Всего классов", styles.header());
        int col = 3;
        for (AddressStatsColumn address : addresses) {
            createCell(header, col, address.address(), styles.header());
            createCell(header, col + 1, "", styles.header());
            merge(sheet, new CellRangeAddress(1, 1, col, col + 1));
            col += 2;
        }

        Map<Integer, Integer> totalByParallel = stats.getParallelTotals().stream()
                .collect(java.util.stream.Collectors.toMap(ContingentDtos.ParallelTotal::getParallel, ContingentDtos.ParallelTotal::getTotalStudents));
        Map<Integer, Integer> classCountByParallel = stats.getParallelTotals().stream()
                .collect(java.util.stream.Collectors.toMap(ContingentDtos.ParallelTotal::getParallel, total -> total.getTotalClasses() == null ? 0 : total.getTotalClasses()));

        for (Integer parallel : stats.getParallels()) {
            List<List<ContingentDtos.ClassTotal>> perAddress = addresses.stream()
                    .map(address -> classesForParallel(address.classes(), parallel))
                    .toList();
            rowIdx = writeParallelRows(sheet, rowIdx, parallel, totalByParallel.getOrDefault(parallel, 0), classCountByParallel.getOrDefault(parallel, 0), perAddress, styles);
        }

        Row totalRow = sheet.createRow(rowIdx);
        createCell(totalRow, 0, "ИТОГО", styles.total());
        createCell(totalRow, 1, stats.getTotalStudents(), styles.total());
        createCell(totalRow, 2, stats.getParallelTotals().stream().mapToInt(total -> total.getTotalClasses() == null ? 0 : total.getTotalClasses()).sum(), styles.total());
        int totalCol = 3;
        for (AddressStatsColumn address : addresses) {
            createCell(totalRow, totalCol, "", styles.total());
            createCell(totalRow, totalCol + 1, address.totalStudents(), styles.total());
            totalCol += 2;
        }

        writeStageClassSummaryRow(sheet, rowIdx + 1, totalCol, stats, styles);
        finishContingentSheet(sheet, Math.max(totalCol, 4), 2);
    }

    private void writeKindergartenStatsSheet(Workbook workbook,
                                             ContingentDtos.StatsResponse stats,
                                             ContingentWorkbookStyles styles) {
        Sheet sheet = workbook.createSheet("Детский сад");
        Row title = sheet.createRow(0);
        createCell(title, 0, "Дошкольный контингент на " + stats.getSnapshotDate(), styles.title());
        createCell(title, 1, "", styles.title());
        merge(sheet, new CellRangeAddress(0, 0, 0, 1));

        Row header = sheet.createRow(1);
        createCell(header, 0, "Группа / форма", styles.header());
        createCell(header, 1, "Детей", styles.header());

        int rowIndex = 2;
        for (ContingentDtos.KindergartenGroupTotal group : stats.getKindergartenGroups()) {
            Row row = sheet.createRow(rowIndex++);
            createCell(row, 0, group.getGroupName(), styles.text());
            createCell(row, 1, group.getStudents(), styles.number());
        }

        Row total = sheet.createRow(rowIndex++);
        createCell(total, 0, "ИТОГО ДЕТСКИЙ САД", styles.total());
        createCell(total, 1, stats.getTotalKindergartenChildren(), styles.total());

        if (zeroIfNull(stats.getTotalUnassignedChildren()) > 0) {
            Row unassigned = sheet.createRow(rowIndex++);
            createCell(unassigned, 0, "Вне класса/детского сада", styles.total());
            createCell(unassigned, 1, stats.getTotalUnassignedChildren(), styles.total());
        }

        Row imported = sheet.createRow(rowIndex);
        createCell(imported, 0, "ВСЕГО В ФАЙЛЕ", styles.total());
        createCell(imported, 1, stats.getTotalImportedChildren(), styles.total());

        sheet.createFreezePane(0, 2);
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.setColumnWidth(0, Math.min(Math.max(sheet.getColumnWidth(0), 28 * 256), 48 * 256));
        sheet.setColumnWidth(1, Math.min(Math.max(sheet.getColumnWidth(1), 12 * 256), 18 * 256));
    }

    private void writeStageClassSummaryRow(Sheet sheet,
                                           int rowIdx,
                                           int totalCol,
                                           ContingentDtos.StatsResponse stats,
                                           ContingentWorkbookStyles styles) {
        Row stageRow = sheet.createRow(rowIdx);
        createCell(stageRow, 0, "Классов по уровням", styles.total());
        createCell(stageRow, 1, "", styles.total());
        createCell(stageRow, 2, stageClassSummary(stats), styles.total());
        for (int col = 3; col < totalCol; col++) {
            createCell(stageRow, col, "", styles.total());
        }
    }

    private String stageClassSummary(ContingentDtos.StatsResponse stats) {
        return "НОО: " + zeroIfNull(stats.getTotalClassesNoo())
                + "; ООО: " + zeroIfNull(stats.getTotalClassesOoo())
                + "; СОО: " + zeroIfNull(stats.getTotalClassesSoo());
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private int writeParallelRows(Sheet sheet,
                                  int rowIdx,
                                  Integer parallel,
                                  Integer totalStudents,
                                  Integer totalClasses,
                                  List<List<ContingentDtos.ClassTotal>> groupedClasses,
                                  ContingentWorkbookStyles styles) {
        int lines = Math.max(1, groupedClasses.stream().mapToInt(List::size).max().orElse(1));
        int startRow = rowIdx;
        for (int i = 0; i < lines; i++) {
            Row row = sheet.createRow(rowIdx++);
            if (i == 0) {
                createCell(row, 0, parallel, styles.number());
                createCell(row, 1, totalStudents, styles.number());
                createCell(row, 2, totalClasses, styles.number());
            } else {
                createCell(row, 0, "", styles.number());
                createCell(row, 1, "", styles.number());
                createCell(row, 2, "", styles.number());
            }
            int dataCol = 3;
            for (List<ContingentDtos.ClassTotal> rows : groupedClasses) {
                ContingentDtos.ClassTotal item = i < rows.size() ? rows.get(i) : null;
                createCell(row, dataCol, item == null ? "" : item.getClassName(), styles.text());
                createCell(row, dataCol + 1, item == null ? "" : item.getStudents(), styles.number());
                dataCol += 2;
            }
            row.setHeightInPoints(Math.max(row.getHeightInPoints(), 24));
        }
        if (lines > 1) {
            merge(sheet, new CellRangeAddress(startRow, rowIdx - 1, 0, 0));
            merge(sheet, new CellRangeAddress(startRow, rowIdx - 1, 1, 1));
            merge(sheet, new CellRangeAddress(startRow, rowIdx - 1, 2, 2));
        }
        return rowIdx;
    }

    private List<ContingentDtos.ClassTotal> classesForParallel(List<ContingentDtos.ClassTotal> classes, Integer parallel) {
        return classes.stream()
                .filter(c -> Objects.equals(c.getParallel(), parallel))
                .sorted(Comparator.comparing(ContingentDtos.ClassTotal::getParallel)
                        .thenComparing(ContingentDtos.ClassTotal::getClassName, this::compareClassNames))
                .toList();
    }

    private List<AddressStatsColumn> addressStatsColumns(ContingentDtos.StatsResponse stats) {
        Map<String, AddressStatsAccumulator> byAddress = new LinkedHashMap<>();
        for (ContingentDtos.BuildingColumn building : stats.getColumns()) {
            for (ContingentDtos.AddressColumn address : building.getAddresses()) {
                String addressName = normalize(address.getAddress()).isBlank() ? "Адрес не указан" : normalize(address.getAddress());
                String key = addressName.toLowerCase(Locale.ROOT);
                AddressStatsAccumulator accumulator = byAddress.computeIfAbsent(key, k -> new AddressStatsAccumulator(addressName));
                accumulator.classes().addAll(address.getClasses());
                accumulator.addTotal(address.getTotalStudents());
            }
        }
        return byAddress.values().stream()
                .map(accumulator -> new AddressStatsColumn(
                        accumulator.address(),
                        accumulator.classes().stream()
                                .sorted(Comparator.comparing(ContingentDtos.ClassTotal::getParallel)
                                        .thenComparing(ContingentDtos.ClassTotal::getClassName, this::compareClassNames))
                                .toList(),
                        accumulator.totalStudents()
                ))
                .sorted(Comparator.comparing(AddressStatsColumn::address, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void finishContingentSheet(Sheet sheet, int columns, int headerRows) {
        sheet.createFreezePane(3, headerRows);
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            int minWidth = i < 3 ? 14 * 256 : 12 * 256;
            int maxWidth = i < 3 ? 18 * 256 : 28 * 256;
            sheet.setColumnWidth(i, Math.min(Math.max(width + 512, minWidth), maxWidth));
        }
    }

    private Cell createCell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value == null ? "" : value));
        }
        cell.setCellStyle(style);
        return cell;
    }

    private void merge(Sheet sheet, CellRangeAddress region) {
        sheet.addMergedRegion(region);
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
    }

    private ContingentWorkbookStyles createContingentStyles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        CellStyle title = baseContingentStyle(workbook);
        title.setFont(titleFont);
        title.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle header = baseContingentStyle(workbook);
        header.setFont(boldFont);
        header.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle subHeader = baseContingentStyle(workbook);
        subHeader.setFont(boldFont);
        subHeader.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        subHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle text = baseContingentStyle(workbook);
        CellStyle number = baseContingentStyle(workbook);

        CellStyle total = baseContingentStyle(workbook);
        total.setFont(boldFont);
        total.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        total.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        return new ContingentWorkbookStyles(title, header, subHeader, text, number, total);
    }

    private CellStyle baseContingentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private record ContingentWorkbookStyles(CellStyle title,
                                            CellStyle header,
                                            CellStyle subHeader,
                                            CellStyle text,
                                            CellStyle number,
                                            CellStyle total) {
    }

    private record AddressStatsColumn(String address, List<ContingentDtos.ClassTotal> classes, int totalStudents) {
    }

    private static class AddressStatsAccumulator {
        private final String address;
        private final List<ContingentDtos.ClassTotal> classes = new ArrayList<>();
        private int totalStudents;

        private AddressStatsAccumulator(String address) {
            this.address = address;
        }

        private String address() {
            return address;
        }

        private List<ContingentDtos.ClassTotal> classes() {
            return classes;
        }

        private int totalStudents() {
            return totalStudents;
        }

        private void addTotal(Integer value) {
            totalStudents += value == null ? 0 : value;
        }
    }

    @Override
    public List<ContingentDtos.ImportProblem> getProblems(String academicYear, Long snapshotId) {
        ContingentSnapshot snapshot = snapshotId == null
                ? snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).orElse(null)
                : snapshotRepository.findById(snapshotId).orElse(null);

        if (snapshot == null) {
            return List.of();
        }

        Set<String> planClasses = curriculumPlanEntryRepository.findAll().stream()
                .filter(entry -> academicYear.equals(entry.getAcademicYear()))
                .map(CurriculumPlanEntry::getClassName)
                .map(ClassNameNormalizer::normalize)
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        Map<String, Integer> studentCountByClass = new TreeMap<>();
        studentRepository.findClassNamesBySnapshotId(snapshot.getId())
                .stream()
                .map(this::normalizePlacementName)
                .filter(this::isSchoolClassName)
                .forEach(className -> studentCountByClass.merge(className, 1, Integer::sum));

        List<ContingentDtos.ImportProblem> problems = new ArrayList<>();
        studentCountByClass.forEach((className, count) -> {
            if (planClasses.contains(className)) {
                return;
            }
            ContingentDtos.ImportProblem problem = new ContingentDtos.ImportProblem();
            problem.setClassName(className);
            problem.setStudentsCount(count);
            problem.setDescription("Класс отсутствует в учебном плане выбранного учебного года");
            problems.add(problem);
        });
        return problems;
    }

    @Override
    @Transactional(readOnly = true)
    public ContingentDtos.ImportMismatchResponse getImportMismatches(String academicYear, Long snapshotId) {
        ContingentSnapshot snapshot = snapshotId == null
                ? snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear).orElse(null)
                : snapshotRepository.findById(snapshotId).orElse(null);
        if (snapshot == null || !academicYear.equals(snapshot.getAcademicYear())) {
            return emptyMismatchResponse();
        }

        List<ContingentStudent> students = studentRepository.findAllBySnapshotId(snapshot.getId());
        Set<String> planClasses = planClassNames(academicYear);
        List<ContingentDtos.ImportMismatchRow> rows = new ArrayList<>();
        int outsideCount = 0;
        int ambiguousCount = 0;
        int unknownClassCount = 0;

        for (ContingentStudent student : students) {
            boolean outside = isOutsideOrganization(student.getClassName());
            boolean ambiguous = student.getIdentityMatchStatus() == StudentIdentityMatchStatus.AMBIGUOUS
                    || student.getStudentId() == null;
            boolean unknownClass = isSchoolClassName(student.getClassName())
                    && !planClasses.contains(ClassNameNormalizer.normalize(student.getClassName()));
            if (!outside && !ambiguous && !unknownClass) {
                continue;
            }
            if (outside) outsideCount++;
            if (ambiguous) ambiguousCount++;
            if (unknownClass) unknownClassCount++;

            List<String> messages = new ArrayList<>();
            if (outside) messages.add("Ребёнок находится в «Вне ОО» — выберите класс или группу");
            if (ambiguous) messages.add("Карточка ребёнка не определена однозначно");
            if (unknownClass) messages.add("Класс отсутствует в учебном плане выбранного года");

            ContingentDtos.ImportMismatchRow row = new ContingentDtos.ImportMismatchRow();
            row.setKey("student:" + student.getId());
            row.setType(outside ? "OUTSIDE_ORGANIZATION" : (ambiguous ? "AMBIGUOUS_IDENTITY" : "UNKNOWN_CLASS"));
            row.setContingentStudentId(student.getId());
            row.setCurrentStudentId(student.getStudentId());
            row.setFullName(student.getFullName());
            row.setBirthDate(parseBirthDateOrNull(student.getBirthDate()));
            row.setCurrentPlacement(student.getClassName());
            row.setMessage(String.join(". ", messages));
            row.setRawPayload(student.getRawPayload());
            row.setCanResolve(true);
            row.setRequiresStudent(ambiguous);
            row.setRequiresPlacement(outside || unknownClass);
            rows.add(row);
        }

        List<ContingentImportIssue> storedIssues = importIssueRepository
                .findAllBySnapshotIdOrderBySourceRowNumberAscIdAsc(snapshot.getId());
        for (ContingentImportIssue issue : storedIssues) {
            ContingentDtos.ImportMismatchRow row = new ContingentDtos.ImportMismatchRow();
            row.setKey("issue:" + issue.getId());
            row.setType(issue.getIssueType());
            row.setSourceRowNumber(issue.getSourceRowNumber());
            row.setFullName(issue.getFullName());
            row.setCurrentPlacement(issue.getPlacementName());
            row.setMessage(issue.getMessage() + ". Исправьте исходный файл и загрузите его повторно.");
            row.setRawPayload(issue.getRawPayload());
            row.setCanResolve(false);
            rows.add(row);
        }
        rows.sort(Comparator
                .comparingInt((ContingentDtos.ImportMismatchRow row) -> mismatchOrder(row.getType()))
                .thenComparing(row -> normalize(row.getFullName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.getSourceRowNumber() == null ? Integer.MAX_VALUE : row.getSourceRowNumber()));

        Map<Long, String> placementByStudent = new HashMap<>();
        students.stream()
                .filter(student -> student.getStudentId() != null)
                .sorted(Comparator.comparing(student -> isOutsideOrganization(student.getClassName())))
                .forEach(student -> placementByStudent.putIfAbsent(student.getStudentId(), student.getClassName()));
        List<ContingentDtos.StudentOption> studentOptions = studentProfileRepository.findAll().stream()
                .sorted(Comparator.comparing(StudentProfile::getCurrentFullName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(StudentProfile::getBirthDate, Comparator.nullsLast(LocalDate::compareTo)))
                .map(profile -> {
                    ContingentDtos.StudentOption option = new ContingentDtos.StudentOption();
                    option.setId(profile.getId());
                    option.setFullName(profile.getCurrentFullName());
                    option.setBirthDate(profile.getBirthDate());
                    option.setCurrentPlacement(placementByStudent.getOrDefault(profile.getId(), ""));
                    return option;
                })
                .toList();

        Set<String> placements = new TreeSet<>(this::compareClassNames);
        classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .map(entry -> normalizePlacementName(entry.getClassName()))
                .filter(value -> !value.isBlank())
                .forEach(placements::add);
        curriculumPlanEntryRepository.findAll().stream()
                .filter(entry -> academicYear.equals(entry.getAcademicYear()))
                .map(entry -> normalizePlacementName(entry.getClassName()))
                .filter(value -> !value.isBlank())
                .forEach(placements::add);
        students.stream()
                .map(ContingentStudent::getClassName)
                .map(this::normalizePlacementName)
                .filter(value -> !value.isBlank() && !isOutsideOrganization(value))
                .filter(value -> isSchoolClassName(value) || isKindergartenPlacement(value))
                .forEach(placements::add);

        ContingentDtos.ImportMismatchResponse response = new ContingentDtos.ImportMismatchResponse();
        response.setSnapshotId(snapshot.getId());
        response.setSnapshotDate(snapshot.getSnapshotDate());
        response.setSourceFileName(snapshot.getSourceFileName());
        response.setImportFormat(snapshot.getImportFormat());
        response.setRows(rows);
        response.setTotal(rows.size());
        response.setOutsideOrganization(outsideCount);
        response.setAmbiguousIdentity(ambiguousCount);
        response.setSkippedRows(storedIssues.size());
        response.setUnknownClasses(unknownClassCount);
        response.setStudentOptions(studentOptions);
        response.setPlacementOptions(new ArrayList<>(placements));
        return response;
    }

    @Override
    @Transactional
    public ContingentDtos.ImportMismatchResponse resolveImportMismatch(
            String academicYear,
            ContingentDtos.ResolveImportMismatchRequest request) {
        if (request == null || request.getContingentStudentId() == null) {
            throw new IllegalArgumentException("Не выбрана строка выгрузки");
        }
        ContingentStudent row = studentRepository.findById(request.getContingentStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Строка выгрузки не найдена"));
        ContingentSnapshot snapshot = snapshotRepository.findById(row.getSnapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Снимок контингента не найден"));
        if (!academicYear.equals(snapshot.getAcademicYear())) {
            throw new IllegalArgumentException("Строка относится к другому учебному году");
        }

        boolean outside = isOutsideOrganization(row.getClassName());
        boolean unknownClass = isSchoolClassName(row.getClassName())
                && !planClassNames(academicYear).contains(ClassNameNormalizer.normalize(row.getClassName()));
        String requestedPlacement = normalizePlacementName(request.getClassName());
        if (outside || unknownClass) {
            if (requestedPlacement.isBlank()) {
                throw new IllegalArgumentException("Выберите класс или группу");
            }
            if (outside && isOutsideOrganization(requestedPlacement)) {
                throw new IllegalArgumentException("Для ребёнка «Вне ОО» нужно выбрать класс или группу организации");
            }
            row.setClassName(requestedPlacement);
        }

        Long targetStudentId = row.getStudentId() == null ? request.getStudentId() : row.getStudentId();
        if (targetStudentId == null) {
            throw new IllegalArgumentException("Выберите карточку ребёнка");
        }
        studentIdentityService.resolveManually(snapshot, row, targetStudentId);
        studentRepository.save(row);
        return getImportMismatches(academicYear, snapshot.getId());
    }

    private ContingentDtos.ImportMismatchResponse emptyMismatchResponse() {
        ContingentDtos.ImportMismatchResponse response = new ContingentDtos.ImportMismatchResponse();
        response.setRows(List.of());
        response.setStudentOptions(List.of());
        response.setPlacementOptions(List.of());
        return response;
    }

    private Set<String> planClassNames(String academicYear) {
        return curriculumPlanEntryRepository.findAll().stream()
                .filter(entry -> academicYear.equals(entry.getAcademicYear()))
                .map(CurriculumPlanEntry::getClassName)
                .map(ClassNameNormalizer::normalize)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private int mismatchOrder(String type) {
        return switch (normalize(type)) {
            case "OUTSIDE_ORGANIZATION" -> 0;
            case "AMBIGUOUS_IDENTITY" -> 1;
            case "UNKNOWN_CLASS" -> 2;
            default -> 3;
        };
    }

    @Override
    public ContingentDtos.ManualClassSizeResponse getManualClassSizes(String academicYear) {
        return manualClassSizeResponse(academicYear);
    }

    @Override
    public ContingentDtos.ManualClassSizeResponse saveManualClassSizes(String academicYear, ContingentDtos.ManualClassSizeSaveRequest request) {
        List<ClassSizeService.ManualClassSizeUpdate> rows = request == null || request.getRows() == null
                ? List.of()
                : request.getRows().stream()
                .map(row -> new ClassSizeService.ManualClassSizeUpdate(row.getClassName(), row.getManualStudents()))
                .toList();
        classSizeService.saveManualRows(academicYear, rows);
        return manualClassSizeResponse(academicYear);
    }

    @Override
    public ContingentDtos.ManualClassSizeResponse importManualClassSizes(String academicYear, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }
        List<ClassSizeService.ManualClassSizeUpdate> updates = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Не найден первый лист");
            }
            DataFormatter formatter = new DataFormatter();
            int headerRowIndex = findManualClassSizeHeaderRow(sheet, formatter);
            Row header = sheet.getRow(headerRowIndex);
            Map<String, Integer> indices = extractHeaderIndices(header, formatter);
            int classColumn = resolveColumnIndex(indices, "класс");
            int manualColumn = resolveColumnIndex(indices, "ручной ввод", "ручная численность", "manual");
            if (classColumn < 0 || manualColumn < 0) {
                throw new IllegalArgumentException("В файле нужны колонки: Класс и Ручной ввод");
            }
            for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                String className = ClassNameNormalizer.normalize(getCellValue(row, classColumn, formatter));
                if (className.isBlank()) {
                    continue;
                }
                Integer manual = parseInteger(getCellValue(row, manualColumn, formatter));
                updates.add(new ClassSizeService.ManualClassSizeUpdate(className, manual));
            }
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать ручную численность", e);
        }
        classSizeService.saveManualRows(academicYear, updates);
        return manualClassSizeResponse(academicYear);
    }

    @Override
    public byte[] exportManualClassSizes(String academicYear) {
        ContingentDtos.ManualClassSizeResponse response = manualClassSizeResponse(academicYear);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = baseContingentStyle(workbook);
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle base = baseContingentStyle(workbook);
            CellStyle green = baseContingentStyle(workbook);
            green.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            green.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle red = baseContingentStyle(workbook);
            red.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            red.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sheet = workbook.createSheet("Ручная численность");
            Row title = sheet.createRow(0);
            createCell(title, 0, "Источник численности", header);
            createCell(title, 1, response.getSource() == ClassSizeSource.MANUAL ? "Ручной ввод" : "АИС", base);
            Row headerRow = sheet.createRow(2);
            List<String> headers = List.of("Класс", "Численность по АИС", "Ручной ввод", "Статус");
            for (int i = 0; i < headers.size(); i++) {
                createCell(headerRow, i, headers.get(i), header);
            }
            int rowIndex = 3;
            for (ContingentDtos.ManualClassSizeRow item : response.getRows()) {
                Row row = sheet.createRow(rowIndex++);
                createCell(row, 0, item.getClassName(), base);
                createCell(row, 1, item.getAisStudents() == null ? "" : item.getAisStudents(), base);
                createCell(row, 2, item.getManualStudents() == null ? "" : item.getManualStudents(), base);
                boolean matches = Boolean.TRUE.equals(item.getMatches());
                createCell(row, 3, matches ? "Совпадает" : "Не совпадает", matches ? green : red);
            }
            sheet.createFreezePane(0, 3);
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось экспортировать ручную численность", e);
        }
    }

    @Override
    public ContingentDtos.ManualClassSizeResponse setClassSizeSource(String academicYear, ClassSizeSource source) {
        classSizeService.setSource(academicYear, source);
        return manualClassSizeResponse(academicYear);
    }

    private ContingentDtos.ManualClassSizeResponse manualClassSizeResponse(String academicYear) {
        ContingentDtos.ManualClassSizeResponse response = new ContingentDtos.ManualClassSizeResponse();
        response.setSource(classSizeService.source(academicYear));
        response.setRows(classSizeService.manualRows(academicYear).stream().map(row -> {
            ContingentDtos.ManualClassSizeRow dto = new ContingentDtos.ManualClassSizeRow();
            dto.setClassName(row.className());
            dto.setAisStudents(row.aisStudents());
            dto.setManualStudents(row.manualStudents());
            dto.setMatches(row.matches());
            return dto;
        }).toList());
        return response;
    }

    private Map<String, Integer> extractHeaderIndices(Row header, DataFormatter formatter) {
        Map<String, Integer> indexByHeader = new LinkedHashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String headerText = normalize(formatter.formatCellValue(header.getCell(i)));
            if (!headerText.isBlank()) {
                indexByHeader.put(headerText, i);
            }
        }
        return indexByHeader;
    }

    private LinkedHashMap<String, String> buildRawValues(Map<String, Integer> indexByHeader, Row row, DataFormatter formatter) {
        LinkedHashMap<String, String> raw = new LinkedHashMap<>();
        indexByHeader.forEach((header, index) -> raw.put(header, getCellValue(row, index, formatter)));
        return raw;
    }

    private ContingentStudent createBlankStudent(String academicYear,
                                                  String recordNumber,
                                                  String fullName,
                                                  String placementName,
                                                  String rawPayload) {
        ContingentStudent student = new ContingentStudent();
        student.setAcademicYear(academicYear);
        student.setRecordNumber(normalize(recordNumber).isBlank() ? UUID.randomUUID().toString() : normalize(recordNumber));
        student.setEnrollmentDate("");
        student.setFullName(normalize(fullName));
        student.setGender("");
        student.setBirthDate("");
        student.setBirthCertificate("");
        student.setSocialCard("");
        student.setPensionInsurance("");
        student.setMedicalInsurance("");
        student.setPassport("");
        student.setCitizenship("");
        student.setAdditionalInfoCode("");
        student.setAoopVariant("");
        student.setEducationReceivingForm("");
        student.setEducationForm("");
        student.setClassName(normalizePlacementName(placementName));
        student.setAlphabetBookNumber("");
        student.setRegistrationAddress("");
        student.setTemporaryRegistrationAddress("");
        student.setActualAddress("");
        student.setPhone("");
        student.setEmail("");
        student.setRepresentativeName("");
        student.setRepresentativePhone("");
        student.setOnVshuFrom("");
        student.setOnVshuReason("");
        student.setOnKdnFrom("");
        student.setOnKdnReason("");
        student.setOnPdnFrom("");
        student.setOnPdnReason("");
        student.setRemovedFromVshu("");
        student.setRemovedFromVshuReason("");
        student.setRawPayload(normalize(rawPayload).isBlank() ? "{}" : rawPayload);
        return student;
    }

    private CompactLayout detectCompactLayout(Sheet sheet, DataFormatter formatter) {
        int firstRowIndex = firstNonEmptyRow(sheet, formatter);
        if (firstRowIndex < 0) {
            return null;
        }
        Row firstRow = sheet.getRow(firstRowIndex);
        Map<String, Integer> possibleHeaders = extractHeaderIndices(firstRow, formatter);
        int fioColumn = resolveColumnIndex(possibleHeaders, "фио", "ф.и.о", "учащийся", "обучающийся");
        int placementColumn = resolveColumnIndex(possibleHeaders, "класс", "группа");
        if (fioColumn >= 0 && placementColumn >= 0 && fioColumn != placementColumn) {
            return new CompactLayout(firstRowIndex + 1, fioColumn, placementColumn);
        }

        int pairedRows = 0;
        int personRows = 0;
        int recognizedPlacements = 0;
        int lastProbeRow = Math.min(sheet.getLastRowNum(), firstRowIndex + 49);
        for (int i = firstRowIndex; i <= lastProbeRow; i++) {
            Row row = sheet.getRow(i);
            String fullName = getCellValue(row, 0, formatter);
            String placement = getCellValue(row, 1, formatter);
            if (fullName.isBlank() && placement.isBlank()) {
                continue;
            }
            if (fullName.isBlank() || placement.isBlank()) {
                continue;
            }
            pairedRows++;
            if (looksLikePersonName(fullName)) {
                personRows++;
            }
            if (isSchoolClassName(placement) || isKindergartenPlacement(placement) || isOutsideOrganization(placement)) {
                recognizedPlacements++;
            }
        }
        if (pairedRows == 0 || personRows * 10 < pairedRows * 7 || recognizedPlacements == 0) {
            return null;
        }
        return new CompactLayout(firstRowIndex, 0, 1);
    }

    private int firstNonEmptyRow(Sheet sheet, DataFormatter formatter) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            int lastCell = Math.max(2, row.getLastCellNum());
            for (int column = 0; column < lastCell; column++) {
                if (!getCellValue(row, column, formatter).isBlank()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean looksLikePersonName(String value) {
        String normalized = normalize(value);
        return normalized.split("\\s+").length >= 2
                && normalized.chars().anyMatch(Character::isLetter);
    }

    private boolean isSchoolClassName(String value) {
        int parallel = extractParallel(normalize(value));
        return parallel >= 1 && parallel <= 11;
    }

    private boolean isKindergartenPlacement(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT).replace('ё', 'е');
        return normalized.contains("групп")
                || normalized.matches("^гкп(?:\\s|$).*")
                || normalized.contains("детский сад")
                || normalized.contains("дошкол");
    }

    private boolean isOutsideOrganization(String value) {
        return normalize(value).toLowerCase(Locale.ROOT).replace('ё', 'е').matches("^вне\\s+оо(?:\\s|$).*");
    }

    private String normalizePlacementName(String value) {
        String normalized = normalize(value).replaceAll("\\s+", " ");
        return isSchoolClassName(normalized) ? ClassNameNormalizer.normalize(normalized) : normalized;
    }

    private record CompactLayout(int firstDataRow, int fioColumn, int placementColumn) {
    }

    private record RepresentativeContact(String name, String phone) {
    }


    private int resolveColumnIndex(Map<String, Integer> indexByHeader, String... markers) {
        if (indexByHeader == null || indexByHeader.isEmpty()) {
            return -1;
        }
        for (String marker : markers) {
            if (marker == null || marker.isBlank()) {
                continue;
            }
            String needle = normalize(marker).toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Integer> entry : indexByHeader.entrySet()) {
                String header = normalize(entry.getKey()).toLowerCase(Locale.ROOT);
                if (header.contains(needle)) {
                    return entry.getValue();
                }
            }
        }
        return -1;
    }

    private String getCellValueByMarker(Row row, Map<String, Integer> indexByHeader, DataFormatter formatter, String... markers) {
        int index = resolveColumnIndex(indexByHeader, markers);
        return getCellValue(row, index, formatter);
    }

    private String getCellValue(Row row, Integer index, DataFormatter formatter) {
        if (row == null || index == null || index < 0) {
            return "";
        }
        return normalize(formatter.formatCellValue(row.getCell(index)));
    }

    private String toJson(Map<String, String> rawValues) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : rawValues.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append('"').append(escapeJson(entry.getKey())).append('"').append(':')
                    .append('"').append(escapeJson(entry.getValue())).append('"');
            first = false;
        }
        json.append('}');
        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int max = Math.min(sheet.getLastRowNum(), 30);
        for (int i = 0; i <= max; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String first = normalize(formatter.formatCellValue(row.getCell(0))).toLowerCase(Locale.ROOT);
            if (first.contains("личное дело")) {
                return i;
            }
        }
        return -1;
    }

    private int findManualClassSizeHeaderRow(Sheet sheet, DataFormatter formatter) {
        int max = Math.min(sheet.getLastRowNum(), 30);
        for (int i = 0; i <= max; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Map<String, Integer> headers = extractHeaderIndices(row, formatter);
            if (resolveColumnIndex(headers, "класс") >= 0
                    && resolveColumnIndex(headers, "ручной ввод", "ручная численность", "manual") >= 0) {
                return i;
            }
        }
        return 0;
    }

    private Integer parseInteger(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(normalized.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseSnapshotDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        Pattern pattern = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})");
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return LocalDate.parse(matcher.group(1), DATE_FORMATTER);
        }
        return fallback;
    }

    private LocalDate parseBirthDateOrNull(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
                DATE_FORMATTER,
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (Exception ignored) {
                // Try the next supported source format.
            }
        }
        return null;
    }

    private int extractParallel(String className) {
        if (className == null) {
            return -1;
        }
        Matcher matcher = Pattern.compile("^(\\d{1,2})").matcher(className);
        if (!matcher.find()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private ContingentSnapshot resolveSnapshot(String academicYear, LocalDate snapshotDate) {
        if (snapshotDate != null) {
            return snapshotRepository.findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc(academicYear, snapshotDate)
                    .orElseThrow(() -> new IllegalArgumentException("Снимок на выбранную дату не найден"));
        }
        return snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .orElseThrow(() -> new IllegalArgumentException("Нет загруженных данных контингента"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record ParsedImportIssue(
            int rowNumber,
            String issueType,
            String message,
            String fullName,
            String placementName,
            String rawPayload
    ) {
    }
}
