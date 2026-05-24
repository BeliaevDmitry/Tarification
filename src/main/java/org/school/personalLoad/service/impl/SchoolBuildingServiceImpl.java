package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.SchoolBuildingService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchoolBuildingServiceImpl implements SchoolBuildingService {

    private final SchoolBuildingRepository repository;
    private final AppUserRepository appUserRepository;

    @Override
    public SchoolBuilding upsert(SchoolBuildingRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        String name = normalize(request.getName());
        String address = normalize(request.getAddress());
        if (name.isBlank()) throw new IllegalArgumentException("name is required");
        if (address.isBlank()) throw new IllegalArgumentException("address is required");

        String code = normalize(request.getCode());
        if (code.isBlank()) {
            code = (name + "|" + address).toLowerCase();
        }

        SchoolBuilding entity = repository.findByCode(code).orElseGet(SchoolBuilding::new);
        entity.setCode(code);
        entity.setName(name);
        entity.setManagerFio(normalize(entity.getManagerFio()));
        entity.setAddress(address);
        return repository.save(entity);
    }

    @Override
    public List<SchoolBuilding> findAll() {
        Map<String, String> buildingHeadByGroupCode = new LinkedHashMap<>();
        appUserRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.BUILDING_HEAD)
                .filter(user -> !normalize(user.getManagedBuildingCode()).isBlank())
                .forEach(user -> buildingHeadByGroupCode.put(
                        normalizeBuildingGroupCode(user.getManagedBuildingCode()),
                        normalize(user.getFullName())
                ));

        return repository.findAll().stream()
                .map(entity -> withDisplayManager(
                        entity,
                        buildingHeadByGroupCode.get(normalizeBuildingGroupCode(entity.getCode()))
                ))
                .toList();
    }

    @Override
    @Transactional
    public void deleteByCode(String code) {
        String normalizedCode = normalize(code);
        if (normalizedCode.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        repository.deleteByCode(normalizedCode);
    }


    private SchoolBuilding withDisplayManager(SchoolBuilding source, String assignedManagerFio) {
        SchoolBuilding copy = new SchoolBuilding();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setAddress(source.getAddress());
        copy.setCreatedAt(source.getCreatedAt());
        String displayManager = normalize(assignedManagerFio);
        if (displayManager.isBlank()) {
            displayManager = normalize(source.getManagerFio());
        }
        copy.setManagerFio(displayManager.isBlank() ? "Не назначен" : displayManager);
        return copy;
    }

    @Override
    public void clearAll() {
        repository.deleteAll();
    }


    @Override
    public byte[] exportToExcel() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Корпуса");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Код");
            header.createCell(1).setCellValue("Название");
            header.createCell(2).setCellValue("Адрес");

            int rowIdx = 1;
            for (SchoolBuilding building : repository.findAll()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(normalize(building.getCode()));
                row.createCell(1).setCellValue(normalize(building.getName()));
                row.createCell(2).setCellValue(normalize(building.getAddress()));
            }

            for (int i = 0; i < 3; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось экспортировать корпуса", e);
        }
    }

    @Override
    public java.util.Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");
        int imported = 0;
        int skipped = 0;

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) throw new IllegalArgumentException("Лист с корпусами не найден");
            DataFormatter formatter = new DataFormatter();

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String code = normalize(formatter.formatCellValue(row.getCell(0)));
                String name = normalize(formatter.formatCellValue(row.getCell(1)));
                String address = normalize(formatter.formatCellValue(row.getCell(2)));

                if (name.equalsIgnoreCase("Название") || code.equalsIgnoreCase("Код")) {
                    skipped++;
                    continue;
                }

                if (name.isBlank() || address.isBlank()) {
                    skipped++;
                    continue;
                }

                SchoolBuildingRequest request = new SchoolBuildingRequest();
                request.setCode(code);
                request.setName(name);
                request.setAddress(address);
                upsert(request);
                imported++;
            }

            return java.util.Map.of("status", "ok", "imported", imported, "skipped", skipped, "total", repository.count());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать корпуса", e);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeBuildingGroupCode(String value) {
        String normalized = normalize(value).replace(" ", "").toUpperCase();
        int idx = normalized.indexOf("|");
        return idx >= 0 ? normalized.substring(0, idx) : normalized;
    }
}
