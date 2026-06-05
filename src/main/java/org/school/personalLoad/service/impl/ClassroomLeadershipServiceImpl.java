package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.ClassroomBuildingScopeUpdateRequest;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClassroomLeadershipServiceImpl implements ClassroomLeadershipService {

    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;

    @Override
    @Transactional
    public List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests) {
        List<ClassroomLeadershipEntryRequest> safeRequests = requests == null ? List.of() : new ArrayList<>(requests);
        String academicYear = safeRequests.stream()
                .map(ClassroomLeadershipEntryRequest::getAcademicYear)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("academicYear is required"));

        List<ClassroomLeadershipEntry> existingRows = classroomLeadershipRepository.findAllByAcademicYear(academicYear);
        Map<Long, ClassroomLeadershipEntry> existingById = new LinkedHashMap<>();
        Map<String, ClassroomLeadershipEntry> existingByBuildingAndClassName = new LinkedHashMap<>();
        for (ClassroomLeadershipEntry existing : existingRows) {
            if (existing.getId() != null) {
                existingById.put(existing.getId(), existing);
            }
            existingByBuildingAndClassName.put(classScopeKey(existing.getNumberSchoolBuilding(), existing.getClassName()), existing);
        }

        Map<String, ClassroomLeadershipEntryRequest> normalized = new LinkedHashMap<>();
        for (ClassroomLeadershipEntryRequest request : safeRequests) {
            if (request == null) continue;
            String building = normalizeBuildingCode(request.getNumberSchoolBuilding());
            String className = ClassNameNormalizer.normalize(request.getClassName());
            String classDirection = normalize(request.getClassDirection());
            String classType = normalizeClassType(request.getClassType());
            if (building.isBlank() || className.isBlank() || classDirection.isBlank()
                    || (request.getTeacherId() == null && normalize(request.getFioTeacher()).isBlank())) continue;

            TeacherDirectoryEntry teacher = resolveRequiredTeacher(request);
            SchoolBuilding schoolBuilding = resolveSchoolBuilding(request, building);

            request.setNumberSchoolBuilding(building);
            request.setSchoolBuildingId(schoolBuilding.getId());
            request.setClassName(className);
            request.setClassDirection(classDirection);
            request.setTeacherId(teacher.getId());
            request.setFioTeacher(normalize(teacher.getFioTeacher()));
            request.setCampusAddress(normalize(schoolBuilding.getAddress()));
            request.setClassType(classType);
            request.setAcademicYear(academicYear);

            String key = request.getId() != null ? "id:" + request.getId() : "class:" + classScopeKey(building, className);
            normalized.put(key, request);
        }

        List<ClassroomLeadershipEntry> toSave = new ArrayList<>();
        Set<Long> touchedIds = new LinkedHashSet<>();
        for (ClassroomLeadershipEntryRequest request : normalized.values()) {
            ClassroomLeadershipEntry entry = request.getId() == null ? null : existingById.get(request.getId());
            if (entry == null) {
                entry = existingByBuildingAndClassName.get(classScopeKey(request.getNumberSchoolBuilding(), request.getClassName()));
            }
            if (entry == null) {
                entry = new ClassroomLeadershipEntry();
                entry.setAcademicYear(academicYear);
            }

            entry.setNumberSchoolBuilding(normalizeBuildingCode(request.getNumberSchoolBuilding()));
            entry.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
            entry.setClassDirection(normalize(request.getClassDirection()));
            TeacherDirectoryEntry teacher = resolveRequiredTeacher(request);
            entry.setTeacher(teacher);
            entry.setFioTeacher(normalize(teacher.getFioTeacher()));
            SchoolBuilding schoolBuilding = resolveSchoolBuilding(request, entry.getNumberSchoolBuilding());
            entry.setSchoolBuilding(schoolBuilding);
            entry.setCampusAddress(normalize(schoolBuilding.getAddress()));
            entry.setClassType(normalizeClassType(request.getClassType()));
            toSave.add(entry);

            if (entry.getId() != null) {
                touchedIds.add(entry.getId());
            }
        }

        List<ClassroomLeadershipEntry> saved = classroomLeadershipRepository.saveAll(toSave);
        syncClassroomBuildingGroups(saved);
        saved.stream().map(ClassroomLeadershipEntry::getId).filter(java.util.Objects::nonNull).forEach(touchedIds::add);

        for (ClassroomLeadershipEntry existing : existingRows) {
            if (existing.getId() != null && !touchedIds.contains(existing.getId())) {
                deleteClassTails(academicYear, existing);
                classroomLeadershipRepository.delete(existing);
            }
        }

        return saved;
    }

    @Override
    @Transactional
    public ClassroomLeadershipEntry updateOne(Long id, ClassroomLeadershipEntryRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String academicYear = normalize(request.getAcademicYear());
        if (academicYear.isBlank()) {
            throw new IllegalArgumentException("academicYear is required");
        }
        ClassroomLeadershipEntry entry = classroomLeadershipRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Класс не найден"));
        if (!academicYear.equals(entry.getAcademicYear())) {
            throw new IllegalArgumentException("Класс относится к другому учебному году");
        }

        String building = normalizeBuildingCode(request.getNumberSchoolBuilding());
        String className = ClassNameNormalizer.normalize(request.getClassName());
        String classDirection = normalize(request.getClassDirection());
        String classType = normalizeClassType(request.getClassType());
        if (building.isBlank() || className.isBlank() || classDirection.isBlank()
                || (request.getTeacherId() == null && normalize(request.getFioTeacher()).isBlank())) {
            throw new IllegalArgumentException("numberSchoolBuilding, className, classDirection and teacherId are required");
        }

        TeacherDirectoryEntry teacher = resolveRequiredTeacher(request);
        ensureBuildingExists(building);
        SchoolBuilding schoolBuilding = resolveSchoolBuilding(request, building);

        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setClassDirection(classDirection);
        entry.setTeacher(teacher);
        entry.setFioTeacher(normalize(teacher.getFioTeacher()));
        entry.setSchoolBuilding(schoolBuilding);
        entry.setCampusAddress(normalize(schoolBuilding.getAddress()));
        entry.setClassType(classType);
        ClassroomLeadershipEntry saved = classroomLeadershipRepository.save(entry);
        syncClassroomBuildingGroups(List.of(saved));
        saved = classroomLeadershipRepository.findById(id).orElse(saved);

        return saved;
    }

    @Override
    @Transactional
    public ClassroomLeadershipEntry updateBuildingScope(Long id, ClassroomBuildingScopeUpdateRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("classId is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getSchoolBuildingId() == null) {
            throw new IllegalArgumentException("schoolBuildingId is required");
        }

        ClassroomLeadershipEntry entry = classroomLeadershipRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Класс не найден"));
        SchoolBuilding targetSchoolBuilding = schoolBuildingRepository.findById(request.getSchoolBuildingId())
                .orElseThrow(() -> new IllegalArgumentException("Площадка не найдена: " + request.getSchoolBuildingId()));
        BuildingGroup targetBuildingGroup = targetSchoolBuilding.getBuildingGroup();
        if (targetBuildingGroup == null || targetBuildingGroup.getId() == null) {
            throw new IllegalArgumentException("Основной корпус целевой площадки не найден");
        }
        if (request.getBuildingGroupId() != null && !request.getBuildingGroupId().equals(targetBuildingGroup.getId())) {
            throw new IllegalArgumentException("Основной корпус не соответствует выбранной площадке");
        }

        String targetCode = normalizeBuildingCode(targetBuildingGroup.getCode());
        if (targetCode.isBlank()) {
            throw new IllegalArgumentException("Код основного корпуса целевой площадки пуст");
        }
        String targetAddress = normalize(targetSchoolBuilding.getAddress());
        String className = ClassNameNormalizer.normalize(entry.getClassName());

        entry.setNumberSchoolBuilding(targetCode);
        entry.setSchoolBuilding(targetSchoolBuilding);
        entry.setCampusAddress(targetAddress);
        entry.setClassName(className);
        ClassroomLeadershipEntry saved = classroomLeadershipRepository.save(entry);

        classroomLeadershipRepository.updateBuildingScopeById(
                saved.getId(),
                targetCode,
                targetBuildingGroup.getId(),
                targetSchoolBuilding.getId(),
                targetAddress
        );
        curriculumPlanEntryRepository.updateClassBuildingScope(
                saved.getAcademicYear(),
                saved.getId(),
                targetCode,
                targetBuildingGroup.getId(),
                className
        );
        manualLoadEntryRepository.updateClassBuildingScope(
                saved.getAcademicYear(),
                saved.getId(),
                targetCode,
                targetBuildingGroup.getId(),
                className
        );

        return classroomLeadershipRepository.findById(saved.getId()).orElse(saved);
    }

    @Override
    public Map<String, Object> importFromExcel(String academicYear, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");

        int imported = 0;
        int skipped = 0;
        Map<String, ClassroomLeadershipEntryRequest> merged = new LinkedHashMap<>();
        Map<String, String> classToKey = new HashMap<>();
        findAll(academicYear).forEach(existing -> {
            ClassroomLeadershipEntryRequest req = new ClassroomLeadershipEntryRequest();
            req.setAcademicYear(academicYear);
            req.setNumberSchoolBuilding(existing.getNumberSchoolBuilding());
            req.setClassName(existing.getClassName());
            req.setClassDirection(existing.getClassDirection());
            req.setTeacherId(existing.getTeacherId());
            req.setFioTeacher(existing.getFioTeacher());
            req.setSchoolBuildingId(existing.getSchoolBuildingId());
            req.setCampusAddress(existing.getCampusAddress());
            req.setClassType(normalizeClassType(existing.getClassType()));
            String key = existing.getNumberSchoolBuilding() + "|" + existing.getClassName();
            merged.put(key, req);
            classToKey.put(classScopeKey(existing.getNumberSchoolBuilding(), existing.getClassName()), key);
        });

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) throw new IllegalArgumentException("Лист с классами не найден");

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String building = normalizeBuildingCode(cellValue(row.getCell(0)));
                String className = ClassNameNormalizer.normalize(cellValue(row.getCell(1)));
                String direction = normalize(cellValue(row.getCell(2)));
                String teacher = normalize(cellValue(row.getCell(3)));
                String campusAddress = normalize(cellValue(row.getCell(4)));
                String classType = normalizeClassType(cellValue(row.getCell(5)));

                if (building.equalsIgnoreCase("КОРПУС") || className.equalsIgnoreCase("КЛАСС")) {
                    skipped++;
                    continue;
                }

                if (building.isBlank() || className.isBlank() || direction.isBlank() || teacher.isBlank()) {
                    skipped++;
                    continue;
                }

                // Архитектурное правило: при импорте классов автоматически создаём отсутствующие сущности справочников.
                ensureBuildingExists(building);
                TeacherDirectoryEntry teacherEntry = resolveOrCreateTeacherForImport(teacher);

                ClassroomLeadershipEntryRequest req = new ClassroomLeadershipEntryRequest();
                req.setNumberSchoolBuilding(building);
                req.setClassName(className);
                req.setClassDirection(direction);
                req.setTeacherId(teacherEntry.getId());
                req.setFioTeacher(normalize(teacherEntry.getFioTeacher()));
                SchoolBuilding schoolBuilding = resolveSchoolBuildingByAddressOrDefault(building, campusAddress);
                req.setSchoolBuildingId(schoolBuilding.getId());
                req.setCampusAddress(normalize(schoolBuilding.getAddress()));
                req.setClassType(classType);
                req.setAcademicYear(academicYear);
                String newKey = building + "|" + className;
                String scopedClassKey = classScopeKey(building, className);
                String previousKey = classToKey.get(scopedClassKey);
                if (previousKey != null && !previousKey.equals(newKey)) {
                    merged.remove(previousKey);
                }
                merged.put(newKey, req);
                classToKey.put(scopedClassKey, newKey);
                imported++;
            }

            List<ClassroomLeadershipEntry> saved = replaceAll(new ArrayList<>(merged.values()));
            return Map.of("status", "ok", "imported", imported, "skipped", skipped, "total", saved.size());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать классы", e);
        }
    }

    @Override
    public Resource buildImportTemplate(String academicYear) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Классы");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Корпус");
            header.createCell(1).setCellValue("Класс");
            header.createCell(2).setCellValue("Направление класса");
            header.createCell(3).setCellValue("Классный руководитель");
            header.createCell(4).setCellValue("Адрес площадки (если отличается)");
            header.createCell(5).setCellValue("Тип класса (Норма/АООП УО)");

            List<ClassroomLeadershipEntry> rows = findAll(academicYear);
            if (rows.isEmpty()) {
                Row ex = sheet.createRow(1);
                ex.createCell(0).setCellValue("СП1");
                ex.createCell(1).setCellValue("7-А");
                ex.createCell(2).setCellValue("Универсальный");
                ex.createCell(3).setCellValue("Иванов И.И.");
                ex.createCell(4).setCellValue("ул. Крупской, д. 13");
                ex.createCell(5).setCellValue("Норма");
            } else {
                int index = 1;
                for (ClassroomLeadershipEntry entry : rows) {
                    Row row = sheet.createRow(index++);
                    row.createCell(0).setCellValue(entry.getNumberSchoolBuilding());
                    row.createCell(1).setCellValue(entry.getClassName());
                    row.createCell(2).setCellValue(entry.getClassDirection());
                    row.createCell(3).setCellValue(entry.getFioTeacher());
                    row.createCell(4).setCellValue(entry.getCampusAddress());
                    row.createCell(5).setCellValue("AOOP_UO".equalsIgnoreCase(normalize(entry.getClassType())) ? "АООП УО" : "Норма");
                }
            }

            for (int i = 0; i < 6; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return new ByteArrayResource(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать шаблон классов", e);
        }
    }

    @Override
    @Transactional
    public List<ClassroomLeadershipEntry> findAll(String academicYear) {
        List<ClassroomLeadershipEntry> rows = classroomLeadershipRepository.findAllByAcademicYear(academicYear);
        if (!rows.isEmpty()) {
            return rows;
        }
        List<ClassroomLeadershipEntry> promoted = promoteFromPreviousYear(academicYear);
        if (promoted.isEmpty()) {
            return List.of();
        }
        return classroomLeadershipRepository.saveAll(promoted);
    }

    private List<ClassroomLeadershipEntry> promoteFromPreviousYear(String academicYear) {
        String previousYear = previousAcademicYear(academicYear);
        if (previousYear.isBlank()) {
            return List.of();
        }
        List<ClassroomLeadershipEntry> previousRows = classroomLeadershipRepository.findAllByAcademicYear(previousYear);
        if (previousRows.isEmpty()) {
            return List.of();
        }
        List<ClassroomLeadershipEntry> promoted = new ArrayList<>();
        for (ClassroomLeadershipEntry previous : previousRows) {
            String nextClass = nextClassName(previous.getClassName());
            if (nextClass == null || nextClass.isBlank()) {
                continue;
            }
            ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
            entry.setAcademicYear(academicYear);
            entry.setNumberSchoolBuilding(normalizeBuildingCode(previous.getNumberSchoolBuilding()));
            entry.setClassName(nextClass);
            entry.setClassDirection(normalize(previous.getClassDirection()));
            TeacherDirectoryEntry teacher = previous.getTeacher();
            if (teacher == null && !normalize(previous.getFioTeacher()).isBlank()) {
                ClassroomLeadershipEntryRequest teacherRequest = new ClassroomLeadershipEntryRequest();
                teacherRequest.setFioTeacher(previous.getFioTeacher());
                teacher = resolveRequiredTeacher(teacherRequest);
            }
            if (teacher == null) {
                continue;
            }
            entry.setTeacher(teacher);
            entry.setFioTeacher(normalize(teacher.getFioTeacher()));
            SchoolBuilding schoolBuilding = previous.getSchoolBuilding() != null
                    ? previous.getSchoolBuilding()
                    : resolveSchoolBuildingByAddressOrDefault(entry.getNumberSchoolBuilding(), previous.getCampusAddress());
            entry.setSchoolBuilding(schoolBuilding);
            entry.setCampusAddress(normalize(schoolBuilding.getAddress()));
            entry.setClassType(normalizeClassType(previous.getClassType()));
            promoted.add(entry);
        }
        Map<String, ClassroomLeadershipEntry> uniqueByClass = new LinkedHashMap<>();
        promoted.forEach(item -> uniqueByClass.put(ClassNameNormalizer.normalize(item.getClassName()), item));
        return new ArrayList<>(uniqueByClass.values());
    }

    private String previousAcademicYear(String academicYear) {
        String value = normalize(academicYear).replace('\\', '/');
        if (!value.matches("\\d{4}/\\d{4}")) {
            return "";
        }
        int from = Integer.parseInt(value.substring(0, 4));
        return (from - 1) + "/" + from;
    }

    private String nextClassName(String className) {
        String normalized = ClassNameNormalizer.normalize(className).toUpperCase(Locale.ROOT).replace('–', '-').replace('—', '-');
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{1,2})-([А-ЯA-Z])$").matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        int parallel = Integer.parseInt(matcher.group(1));
        if (parallel == 4 || parallel == 9 || parallel == 11) {
            return null;
        }
        if (parallel >= 11) {
            return null;
        }
        return (parallel + 1) + "-" + matcher.group(2);
    }

    @Override
    @Transactional
    public void deleteOne(Long id, String academicYear) {
        ClassroomLeadershipEntry existing = findClassByIdAndYear(id, academicYear);
        deleteClassTails(academicYear, existing);
        classroomLeadershipRepository.delete(existing);
    }

    @Override
    @Transactional
    public void deleteOne(String academicYear, String numberSchoolBuilding, String className) {
        String building = normalizeBuildingCode(numberSchoolBuilding);
        String normalizedClassName = ClassNameNormalizer.normalize(className);
        if (building.isBlank() || normalizedClassName.isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding and className are required");
        }
        // TODO remove after FK cutover: legacy string endpoint resolves the class once,
        // then deletes dependent curriculum/manual-load rows only through class_id.
        classroomLeadershipRepository
                .findByAcademicYearAndNumberSchoolBuildingAndClassName(academicYear, building, normalizedClassName)
                .ifPresent(entry -> {
                    deleteClassTails(academicYear, entry);
                    classroomLeadershipRepository.delete(entry);
                });
    }

    @Override
    public Map<String, Object> dependencySummary(Long id, String academicYear) {
        ClassroomLeadershipEntry entry = findClassByIdAndYear(id, academicYear);
        return dependencySummaryForClass(academicYear, entry);
    }

    @Override
    public Map<String, Object> dependencySummary(String academicYear, String numberSchoolBuilding, String className) {
        String building = normalizeBuildingCode(numberSchoolBuilding);
        String normalizedClassName = ClassNameNormalizer.normalize(className);
        if (building.isBlank() || normalizedClassName.isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding and className are required");
        }
        // TODO remove after FK cutover: legacy string endpoint resolves the class once,
        // then counts dependent curriculum/manual-load rows only through class_id.
        ClassroomLeadershipEntry entry = classroomLeadershipRepository
                .findByAcademicYearAndNumberSchoolBuildingAndClassName(academicYear, building, normalizedClassName)
                .orElseThrow(() -> new IllegalArgumentException("Класс не найден"));
        return dependencySummaryForClass(academicYear, entry);
    }

    @Override
    @Transactional
    public void clearAll(String academicYear) {
        curriculumPlanEntryRepository.deleteAllByAcademicYear(academicYear);
        manualLoadEntryRepository.deleteAllByAcademicYear(academicYear);
        classroomLeadershipRepository.findAllByAcademicYear(academicYear).forEach(classroomLeadershipRepository::delete);
    }


    private void deleteClassTails(String academicYear, ClassroomLeadershipEntry entry) {
        if (entry == null || entry.getId() == null) {
            return;
        }
        curriculumPlanEntryRepository.deleteByAcademicYearAndClassId(academicYear, entry.getId());
        manualLoadEntryRepository.deleteByAcademicYearAndClassIds(academicYear, List.of(entry.getId()));
    }

    private Map<String, Object> dependencySummaryForClass(String academicYear, ClassroomLeadershipEntry entry) {
        long curriculumRows = curriculumPlanEntryRepository.countClassTails(academicYear, entry.getId());
        long manualLoadRows = manualLoadEntryRepository.countClassTails(academicYear, entry.getId());
        return Map.of(
                "academicYear", academicYear,
                "classId", entry.getId(),
                "numberSchoolBuilding", normalizeBuildingCode(entry.getNumberSchoolBuilding()),
                "className", ClassNameNormalizer.normalize(entry.getClassName()),
                "curriculumRows", curriculumRows,
                "manualLoadRows", manualLoadRows,
                "totalRows", curriculumRows + manualLoadRows
        );
    }

    private ClassroomLeadershipEntry findClassByIdAndYear(Long id, String academicYear) {
        if (id == null) {
            throw new IllegalArgumentException("classId is required");
        }
        ClassroomLeadershipEntry entry = classroomLeadershipRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Класс не найден"));
        if (!normalize(academicYear).equals(entry.getAcademicYear())) {
            throw new IllegalArgumentException("Класс относится к другому учебному году");
        }
        return entry;
    }

    private void syncClassroomBuildingGroups(List<ClassroomLeadershipEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (ClassroomLeadershipEntry entry : entries) {
            if (entry.getId() == null || normalize(entry.getNumberSchoolBuilding()).isBlank()) {
                continue;
            }
            String building = normalizeBuildingCode(entry.getNumberSchoolBuilding());
            classroomLeadershipRepository.updateBuildingGroupById(entry.getId(), building);
            entry.setNumberSchoolBuilding(building);
        }
    }

    private void ensureBuildingExists(String code) {
        if (findKnownBuilding(code).isEmpty()) {
            throw new IllegalArgumentException("Корпус не найден: " + code);
        }
    }

    private TeacherDirectoryEntry resolveRequiredTeacher(ClassroomLeadershipEntryRequest request) {
        if (request.getTeacherId() != null) {
            return teacherDirectoryRepository.findById(request.getTeacherId())
                    .orElseThrow(() -> new IllegalArgumentException("Педагог не найден: " + request.getTeacherId()));
        }

        String fio = normalize(request.getFioTeacher());
        if (fio.isBlank()) {
            throw new IllegalArgumentException("teacherId is required");
        }

        List<TeacherDirectoryEntry> matches = teacherDirectoryRepository.findAll().stream()
                .filter(teacher -> normalize(teacher.getFioTeacher()).equalsIgnoreCase(fio))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Педагог не найден: " + fio);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Педагог неоднозначен: " + fio);
        }
        return matches.get(0);
    }

    private TeacherDirectoryEntry resolveOrCreateTeacherForImport(String fio) {
        String normalized = normalize(fio);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("teacherId is required");
        }
        List<TeacherDirectoryEntry> matches = teacherDirectoryRepository.findAll().stream()
                .filter(teacher -> normalize(teacher.getFioTeacher()).equalsIgnoreCase(normalized))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Педагог неоднозначен: " + normalized);
        }
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setFioTeacher(normalized);
        return teacherDirectoryRepository.save(teacher);
    }

    private SchoolBuilding resolveSchoolBuilding(ClassroomLeadershipEntryRequest request, String buildingCode) {
        if (request.getSchoolBuildingId() != null) {
            return schoolBuildingRepository.findById(request.getSchoolBuildingId())
                    .orElseThrow(() -> new IllegalArgumentException("Площадка не найдена: " + request.getSchoolBuildingId()));
        }
        return resolveSchoolBuildingByAddressOrDefault(buildingCode, request.getCampusAddress());
    }

    private SchoolBuilding resolveSchoolBuildingByAddressOrDefault(String buildingCode, String requestedAddress) {
        String normalizedAddress = normalizeAddress(requestedAddress);
        if (!normalizedAddress.isBlank()) {
            List<SchoolBuilding> matches = schoolBuildingRepository.findAll().stream()
                    .filter(building -> normalizeAddress(building.getAddress()).equals(normalizedAddress))
                    .toList();
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("Площадка не найдена по адресу: " + requestedAddress);
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Адрес площадки неоднозначен: " + requestedAddress);
            }
            return matches.get(0);
        }
        return findKnownBuilding(buildingCode)
                .orElseThrow(() -> new IllegalArgumentException("Площадка для корпуса не найдена: " + buildingCode));
    }

    private String normalizeAddress(String value) {
        return normalize(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Optional<SchoolBuilding> findKnownBuilding(String code) {
        String normalizedCode = normalizeBuildingCode(code);
        return schoolBuildingRepository.findByCode(normalizedCode)
                .or(() -> schoolBuildingRepository.findAll().stream()
                        .filter(building -> normalizeBuildingCode(building.getCode()).equals(normalizedCode))
                        .findFirst());
    }

    private String normalizeBuildingCode(String value) {
        String normalized = normalize(value)
                .toUpperCase(Locale.ROOT)
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("[CС][ПPР]", "СП")
                .replaceAll("\\s*\\|\\s*", "|")
                .replaceAll("\\s+", "");
        int addressSeparator = normalized.indexOf("|");
        if (addressSeparator >= 0) {
            normalized = normalized.substring(0, addressSeparator);
        }
        return normalized.replaceFirst("^СП-(\\d+)$", "СП$1");
    }

    private String classScopeKey(String building, String className) {
        return normalizeBuildingCode(building) + "|" + ClassNameNormalizer.normalize(className);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeClassType(String value) {
        String normalized = normalize(value).toUpperCase(Locale.ROOT).replace('Ё', 'Е');
        if (normalized.contains("АООП") || normalized.contains("УО") || normalized.contains("AOOP")) {
            return "AOOP_UO";
        }
        return "NORMAL";
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue()).trim();
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception ignored) { yield ""; }
            }
            default -> "";
        };
    }
}
