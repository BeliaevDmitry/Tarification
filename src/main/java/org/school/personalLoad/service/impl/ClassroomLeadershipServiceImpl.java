package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
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

    @Override
    public List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests) {
        List<ClassroomLeadershipEntryRequest> safeRequests = requests == null ? List.of() : requests;

        Map<String, ClassroomLeadershipEntryRequest> normalized = new LinkedHashMap<>();
        for (ClassroomLeadershipEntryRequest request : safeRequests) {
            if (request == null) continue;
            String building = normalizeBuildingCode(request.getNumberSchoolBuilding());
            String className = ClassNameNormalizer.normalize(request.getClassName());
            String classDirection = normalize(request.getClassDirection());
            String fioTeacher = normalize(request.getFioTeacher());
            if (building.isBlank() || className.isBlank() || classDirection.isBlank() || fioTeacher.isBlank()) continue;

            // Не блокируем сохранение: при отсутствии педагога создаём его автоматически.
            ensureTeacherExists(fioTeacher);

            request.setClassName(className);
            normalized.put(building + "|" + className, request);
        }

        classroomLeadershipRepository.deleteAll();
        List<ClassroomLeadershipEntry> toSave = new ArrayList<>();
        normalized.values().forEach((request) -> {
            ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
            entry.setNumberSchoolBuilding(normalizeBuildingCode(request.getNumberSchoolBuilding()));
            entry.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
            entry.setClassDirection(normalize(request.getClassDirection()));
            entry.setFioTeacher(normalize(request.getFioTeacher()));
            toSave.add(entry);
        });

        List<ClassroomLeadershipEntry> saved = classroomLeadershipRepository.saveAll(toSave);
        syncCurriculumBuildingByClass(saved);
        return saved;
    }

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");

        int imported = 0;
        int skipped = 0;
        Map<String, ClassroomLeadershipEntryRequest> merged = new LinkedHashMap<>();
        findAll().forEach(existing -> {
            ClassroomLeadershipEntryRequest req = new ClassroomLeadershipEntryRequest();
            req.setNumberSchoolBuilding(existing.getNumberSchoolBuilding());
            req.setClassName(existing.getClassName());
            req.setClassDirection(existing.getClassDirection());
            req.setFioTeacher(existing.getFioTeacher());
            merged.put(existing.getNumberSchoolBuilding() + "|" + existing.getClassName(), req);
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
                ensureTeacherExists(teacher);

                ClassroomLeadershipEntryRequest req = new ClassroomLeadershipEntryRequest();
                req.setNumberSchoolBuilding(building);
                req.setClassName(className);
                req.setClassDirection(direction);
                req.setFioTeacher(teacher);
                merged.put(building + "|" + className, req);
                imported++;
            }

            List<ClassroomLeadershipEntry> saved = replaceAll(new ArrayList<>(merged.values()));
            return Map.of("status", "ok", "imported", imported, "skipped", skipped, "total", saved.size());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать классы", e);
        }
    }

    @Override
    public Resource buildImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Классы");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Корпус");
            header.createCell(1).setCellValue("Класс");
            header.createCell(2).setCellValue("Направление класса");
            header.createCell(3).setCellValue("Классный руководитель");

            List<ClassroomLeadershipEntry> rows = classroomLeadershipRepository.findAll();
            if (rows.isEmpty()) {
                Row ex = sheet.createRow(1);
                ex.createCell(0).setCellValue("СП1");
                ex.createCell(1).setCellValue("7-А");
                ex.createCell(2).setCellValue("Универсальный");
                ex.createCell(3).setCellValue("Иванов И.И.");
            } else {
                int index = 1;
                for (ClassroomLeadershipEntry entry : rows) {
                    Row row = sheet.createRow(index++);
                    row.createCell(0).setCellValue(entry.getNumberSchoolBuilding());
                    row.createCell(1).setCellValue(entry.getClassName());
                    row.createCell(2).setCellValue(entry.getClassDirection());
                    row.createCell(3).setCellValue(entry.getFioTeacher());
                }
            }

            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return new ByteArrayResource(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать шаблон классов", e);
        }
    }

    @Override
    public List<ClassroomLeadershipEntry> findAll() {
        return classroomLeadershipRepository.findAll();
    }

    @Override
    @Transactional
    public void deleteOne(String numberSchoolBuilding, String className) {
        String building = normalizeBuildingCode(numberSchoolBuilding);
        String normalizedClassName = ClassNameNormalizer.normalize(className);
        if (building.isBlank() || normalizedClassName.isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding and className are required");
        }
        classroomLeadershipRepository.deleteByNumberSchoolBuildingAndClassName(building, normalizedClassName);
    }

    @Override
    public void clearAll() {
        classroomLeadershipRepository.deleteAll();
    }



    /**
     * Ключевая синхронизация: если класс перенесли из СП0 в реальный корпус,
     * обновляем numberSchoolBuilding в учебном плане для этого className.
     * Это предотвращает "пропадание" предметов во вкладке "Нагрузка по корпусам".
     */
    private void syncCurriculumBuildingByClass(List<ClassroomLeadershipEntry> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }

        Map<String, String> buildingByClass = new LinkedHashMap<>();
        classes.forEach(c -> {
            String className = ClassNameNormalizer.normalize(c.getClassName());
            String building = normalizeBuildingCode(c.getNumberSchoolBuilding());
            if (!className.isBlank() && !building.isBlank()) {
                buildingByClass.put(className, building);
            }
        });

        if (buildingByClass.isEmpty()) {
            return;
        }

        List<CurriculumPlanEntry> entries = curriculumPlanEntryRepository.findAll();
        boolean changed = false;
        for (CurriculumPlanEntry entry : entries) {
            String className = ClassNameNormalizer.normalize(entry.getClassName());
            String targetBuilding = buildingByClass.get(className);
            if (targetBuilding == null || targetBuilding.isBlank()) {
                continue;
            }
            if (!targetBuilding.equalsIgnoreCase(String.valueOf(entry.getNumberSchoolBuilding()))) {
                entry.setNumberSchoolBuilding(targetBuilding);
                changed = true;
            }
        }

        if (changed) {
            curriculumPlanEntryRepository.saveAll(entries);
        }
    }

    private void ensureBuildingExists(String code) {
        schoolBuildingRepository.findByCode(code).orElseGet(() -> {
            org.school.personalLoad.model.SchoolBuilding b = new org.school.personalLoad.model.SchoolBuilding();
            b.setCode(code);
            b.setName(code);
            b.setAddress("Не указан");
            b.setManagerFio("Не назначен");
            return schoolBuildingRepository.save(b);
        });
    }

    private void ensureTeacherExists(String fio) {
        teacherDirectoryRepository.findByFioTeacher(fio).orElseGet(() -> {
            TeacherDirectoryEntry t = new TeacherDirectoryEntry();
            t.setFioTeacher(fio);
            return teacherDirectoryRepository.save(t);
        });
    }

    private String normalizeBuildingCode(String value) {
        return normalize(value).replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
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
