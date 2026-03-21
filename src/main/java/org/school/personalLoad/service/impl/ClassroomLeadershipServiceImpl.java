package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.school.personalLoad.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClassroomLeadershipServiceImpl implements ClassroomLeadershipService {

    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final SchoolBuildingRepository buildingRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Override
    public List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests) {
        List<ClassroomLeadershipEntryRequest> safeRequests = requests == null ? List.of() : requests;

        Map<String, ClassroomLeadershipEntryRequest> normalized = new LinkedHashMap<>();
        for (ClassroomLeadershipEntryRequest request : safeRequests) {
            if (request == null) continue;
            String building = normalize(request.getNumberSchoolBuilding());
            String className = ClassNameNormalizer.normalize(request.getClassName());
            String classDirection = normalize(request.getClassDirection());
            String fioTeacher = normalize(request.getFioTeacher());
            if (building.isBlank() || className.isBlank() || classDirection.isBlank() || fioTeacher.isBlank()) continue;

            if (teacherDirectoryRepository.findByFioTeacher(fioTeacher).isEmpty()) {
                throw new IllegalArgumentException("Teacher not found in directory: " + fioTeacher);
            }

            request.setClassName(className);
            normalized.put(building + "|" + className, request);
        }

        List<ClassroomLeadershipEntry> oldValue = classroomLeadershipRepository.findAll();
        classroomLeadershipRepository.deleteAll();
        List<ClassroomLeadershipEntry> toSave = new ArrayList<>();
        normalized.values().forEach((request) -> {
            ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
            entry.setNumberSchoolBuilding(normalize(request.getNumberSchoolBuilding()));
            entry.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
            entry.setClassDirection(normalize(request.getClassDirection()));
            entry.setFioTeacher(normalize(request.getFioTeacher()));
            toSave.add(entry);
        });

        List<ClassroomLeadershipEntry> saved = classroomLeadershipRepository.saveAll(toSave);
        auditService.log(ActionType.UPDATE, "ClassroomLeadership", null, oldValue, saved, "Classroom leadership replaced");
        return saved;
    }

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findSheet(workbook, "класс");
            if (sheet == null) {
                throw new IllegalArgumentException("Не найден лист с классами");
            }

            HeaderLookup headers = detectHeaders(sheet, Map.of(
                    "numberSchoolBuilding", List.of("корпус", "здание", "building", "numberschoolbuilding"),
                    "className", List.of("класс", "class", "classname"),
                    "classDirection", List.of("направление", "профиль", "classdirection", "параллель/профиль"),
                    "fioTeacher", List.of("классный руководитель", "руководитель", "фио педагога", "педагог", "fioteacher")
            ));

            List<ClassroomLeadershipEntryRequest> requests = new ArrayList<>();
            int skipped = 0;
            int headerRowIndex = headers.rowIndex();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String building = normalize(readCell(row, headers.index("numberSchoolBuilding")));
                String className = normalize(readCell(row, headers.index("className")));
                String classDirection = normalize(readCell(row, headers.index("classDirection")));
                String fioTeacher = normalize(readCell(row, headers.index("fioTeacher")));

                if (building.isBlank() && className.isBlank() && classDirection.isBlank() && fioTeacher.isBlank()) {
                    continue;
                }
                if (building.isBlank() || className.isBlank() || classDirection.isBlank() || fioTeacher.isBlank()) {
                    skipped++;
                    continue;
                }

                ClassroomLeadershipEntryRequest request = new ClassroomLeadershipEntryRequest();
                request.setNumberSchoolBuilding(building);
                request.setClassName(className);
                request.setClassDirection(classDirection);
                request.setFioTeacher(fioTeacher);
                requests.add(request);
            }

            List<ClassroomLeadershipEntry> saved = replaceAll(requests);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("sheet", sheet.getSheetName());
            result.put("imported", saved.size());
            result.put("skipped", skipped);
            return result;
        } catch (Exception e) {
            log.error("Ошибка импорта классов из Excel", e);
            throw new RuntimeException("Не удалось импортировать классы из Excel", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomLeadershipEntry> findAll() {
        if (currentUserService.hasRole(RoleName.BUILDING_HEAD)) {
            Long userId = currentUserService.requireCurrentUser().getId();
            return buildingRepository.findByHeadUserId(userId)
                    .map(building -> classroomLeadershipRepository.findAllByNumberSchoolBuilding(building.getCode()))
                    .orElse(List.of());
        }
        return classroomLeadershipRepository.findAll();
    }

    @Override
    public void clearAll() {
        List<ClassroomLeadershipEntry> oldValue = classroomLeadershipRepository.findAll();
        classroomLeadershipRepository.deleteAll();
        auditService.log(ActionType.DELETE, "ClassroomLeadership", null, oldValue, null, "Classroom leadership cleared");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Sheet findSheet(Workbook workbook, String token) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getSheetName().toLowerCase().contains(token)) {
                return sheet;
            }
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private HeaderLookup detectHeaders(Sheet sheet, Map<String, List<String>> aliases) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 10); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, Integer> indexes = new HashMap<>();
            for (Cell cell : row) {
                String value = normalize(readCell(cell)).toLowerCase().replace("ё", "е");
                if (value.isBlank()) {
                    continue;
                }
                aliases.forEach((key, variants) -> {
                    if (indexes.containsKey(key)) {
                        return;
                    }
                    if (variants.stream().anyMatch(value::contains)) {
                        indexes.put(key, cell.getColumnIndex());
                    }
                });
            }

            if (indexes.keySet().containsAll(aliases.keySet())) {
                return new HeaderLookup(rowIndex, indexes);
            }
        }
        throw new IllegalArgumentException("Не удалось определить обязательные колонки Excel-файла классов");
    }

    private String readCell(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return "";
        }
        return readCell(row.getCell(columnIndex));
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private record HeaderLookup(int rowIndex, Map<String, Integer> indexes) {
        Integer index(String key) {
            return indexes.get(key);
        }
    }
}
