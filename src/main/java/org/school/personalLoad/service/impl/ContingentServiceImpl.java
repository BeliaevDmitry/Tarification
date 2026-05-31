package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.service.ContingentService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;

    @Override
    public ContingentDtos.ImportResponse importSnapshot(String academicYear, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        int imported = 0;
        int skipped = 0;
        List<ContingentStudent> parsedStudents = new ArrayList<>();
        LocalDate snapshotDate = LocalDate.now();

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Не найден первый лист");
            }
            DataFormatter formatter = new DataFormatter();

            Row dateRow = sheet.getRow(1);
            snapshotDate = parseSnapshotDate(dateRow == null ? "" : formatter.formatCellValue(dateRow.getCell(0)), snapshotDate);

            int headerRowIndex = findHeaderRow(sheet, formatter);
            if (headerRowIndex < 0) {
                throw new IllegalArgumentException("Не удалось найти строку заголовков");
            }
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
                String className = ClassNameNormalizer.normalize(getCellValueByMarker(row, indexByHeader, formatter, "номер и буква класса", "класс"));

                if (recordNumber.isBlank() && fullName.isBlank() && className.isBlank()) {
                    continue;
                }
                if (fullName.isBlank() || className.isBlank()) {
                    skipped++;
                    continue;
                }

                LinkedHashMap<String, String> rawValues = buildRawValues(indexByHeader, row, formatter);

                ContingentStudent student = new ContingentStudent();
                student.setAcademicYear(academicYear);
                student.setRecordNumber(recordNumber.isBlank() ? UUID.randomUUID().toString() : recordNumber);
                student.setEnrollmentDate(getCellValueByMarker(row, indexByHeader, formatter, "заведено"));
                student.setFullName(fullName);
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
                student.setClassName(className);
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
                student.setRawPayload(toJson(rawValues));

                parsedStudents.add(student);
                imported++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Не удалось обработать файл контингента", e);
        }

        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setAcademicYear(academicYear);
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setSourceFileName(file.getOriginalFilename() == null ? "contingent.xlsx" : file.getOriginalFilename());
        snapshot.setTotalStudents(imported);
        ContingentSnapshot savedSnapshot = snapshotRepository.save(snapshot);

        parsedStudents.forEach(student -> student.setSnapshotId(savedSnapshot.getId()));
        studentRepository.saveAll(parsedStudents);

        ContingentDtos.ImportResponse response = new ContingentDtos.ImportResponse();
        response.setSnapshotId(savedSnapshot.getId());
        response.setSnapshotDate(savedSnapshot.getSnapshotDate());
        response.setImportedStudents(imported);
        response.setSkippedRows(skipped);
        response.setProblems(getProblems(academicYear, savedSnapshot.getId()));
        return response;
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
        for (String rawClassName : classNames) {
            String className = ClassNameNormalizer.normalize(rawClassName);
            if (extractParallel(className) >= 0) {
                importedCountByClass.merge(className, 1, Integer::sum);
            }
        }

        Set<String> allClassNames = new TreeSet<>(this::compareClassNames);
        allClassNames.addAll(placementByClass.keySet());
        allClassNames.addAll(importedCountByClass.keySet());

        Map<Integer, Integer> totalByParallel = new TreeMap<>();
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
            return total;
        }).toList();

        ContingentDtos.StatsResponse response = new ContingentDtos.StatsResponse();
        response.setSnapshotId(snapshot.getId());
        response.setSnapshotDate(snapshot.getSnapshotDate());
        response.setTotalStudents(totalByParallel.values().stream().mapToInt(Integer::intValue).sum());
        response.setParallels(new ArrayList<>(totalByParallel.keySet()));
        response.setColumns(columns);
        response.setParallelTotals(parallelTotals);
        return response;
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
            Sheet sheet = workbook.createSheet("Численность");

            int rowIdx = 0;
            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue("Численность по состоянию на " + stats.getSnapshotDate());

            Row h1 = sheet.createRow(rowIdx++);
            Row h2 = sheet.createRow(rowIdx++);
            h1.createCell(0).setCellValue("Параллель");
            h1.createCell(1).setCellValue("Всего детей");
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 1, 1));

            int col = 2;
            for (ContingentDtos.BuildingColumn building : stats.getColumns()) {
                int start = col;
                for (ContingentDtos.AddressColumn address : building.getAddresses()) {
                    h2.createCell(col).setCellValue(address.getAddress());
                    sheet.addMergedRegion(new CellRangeAddress(2, 2, col, col + 1));
                    col += 2;
                }
                if (col - 1 >= start) {
                    h1.createCell(start).setCellValue(building.getBuildingName());
                    sheet.addMergedRegion(new CellRangeAddress(1, 1, start, col - 1));
                }
            }

            Map<Integer, Integer> totalByParallel = stats.getParallelTotals().stream()
                    .collect(java.util.stream.Collectors.toMap(ContingentDtos.ParallelTotal::getParallel, ContingentDtos.ParallelTotal::getTotalStudents));

            for (Integer parallel : stats.getParallels()) {
                List<List<ContingentDtos.ClassTotal>> perAddress = new ArrayList<>();
                for (ContingentDtos.BuildingColumn building : stats.getColumns()) {
                    for (ContingentDtos.AddressColumn address : building.getAddresses()) {
                        List<ContingentDtos.ClassTotal> rows = address.getClasses().stream()
                                .filter(c -> Objects.equals(c.getParallel(), parallel))
                                .toList();
                        perAddress.add(rows);
                    }
                }

                int lines = Math.max(1, perAddress.stream().mapToInt(List::size).max().orElse(1));
                int startRow = rowIdx;
                for (int i = 0; i < lines; i++) {
                    Row row = sheet.createRow(rowIdx++);
                    if (i == 0) {
                        row.createCell(0).setCellValue(parallel);
                        row.createCell(1).setCellValue(totalByParallel.getOrDefault(parallel, 0));
                    }
                    int dataCol = 2;
                    for (List<ContingentDtos.ClassTotal> rows : perAddress) {
                        ContingentDtos.ClassTotal item = i < rows.size() ? rows.get(i) : null;
                        row.createCell(dataCol).setCellValue(item == null ? "" : item.getClassName());
                        row.createCell(dataCol + 1).setCellValue(item == null ? "" : String.valueOf(item.getStudents()));
                        dataCol += 2;
                    }
                }
                if (lines > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(startRow, rowIdx - 1, 0, 0));
                    sheet.addMergedRegion(new CellRangeAddress(startRow, rowIdx - 1, 1, 1));
                }
            }

            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(0).setCellValue("ИТОГО");
            totalRow.createCell(1).setCellValue(stats.getTotalStudents());
            int totalCol = 2;
            for (ContingentDtos.BuildingColumn building : stats.getColumns()) {
                for (ContingentDtos.AddressColumn address : building.getAddresses()) {
                    totalRow.createCell(totalCol).setCellValue("");
                    totalRow.createCell(totalCol + 1).setCellValue(address.getTotalStudents());
                    totalCol += 2;
                }
            }

            for (int i = 0; i < Math.max(totalCol, 4); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось экспортировать численность", e);
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
                .forEach(className -> studentCountByClass.merge(ClassNameNormalizer.normalize(className), 1, Integer::sum));

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
}
