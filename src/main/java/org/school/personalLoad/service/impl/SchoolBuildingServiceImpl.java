package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
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
    private final BuildingGroupRepository buildingGroupRepository;
    private final AppUserRepository appUserRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final MetaGroupRepository metaGroupRepository;

    @Override
    public SchoolBuilding upsert(SchoolBuildingRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        String name = normalize(request.getName());
        String address = normalize(request.getAddress());
        if (name.isBlank()) throw new IllegalArgumentException("name is required");
        if (address.isBlank()) throw new IllegalArgumentException("address is required");

        if (request.getBuildingGroupId() == null) {
            throw new IllegalArgumentException("buildingGroupId is required for school building");
        }
        BuildingGroup buildingGroup = buildingGroupRepository.findById(request.getBuildingGroupId())
                .orElseThrow(() -> new IllegalArgumentException("BuildingGroup not found: " + request.getBuildingGroupId()));

        SchoolBuilding entity = request.getId() == null
                ? new SchoolBuilding()
                : repository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Корпус не найден"));

        boolean newEntity = entity.getId() == null;
        boolean addressChanged = !newEntity && !normalize(entity.getAddress()).equals(address);
        boolean buildingGroupChanged = !newEntity
                && (entity.getBuildingGroupId() == null || !entity.getBuildingGroupId().equals(request.getBuildingGroupId()));
        boolean physicalIdentityChanged = newEntity || addressChanged || buildingGroupChanged;

        if (physicalIdentityChanged && !newEntity && isPhysicalSiteReferenced(entity.getId())) {
            throw new IllegalStateException("Нельзя изменить адрес или основное СП используемой площадки. Сначала перенесите связанные классы/метагруппы на другую физическую площадку.");
        }

        if (physicalIdentityChanged) {
            String physicalSiteCode = buildPhysicalSiteCode(buildingGroup, address);
            boolean duplicate = repository.findAllByCodeIgnoreCase(physicalSiteCode).stream()
                    .anyMatch(existing -> entity.getId() == null || !existing.getId().equals(entity.getId()));
            if (duplicate) {
                throw new IllegalArgumentException("Физическая площадка с таким адресом уже существует в выбранном СП");
            }
            entity.setCode(physicalSiteCode);
        }
        entity.setBuildingGroup(buildingGroup);
        entity.setName(name);
        entity.setManagerFio(normalize(request.getManagerFio()));
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
                        normalizeOrganizationalCode(user.getManagedBuildingCode()),
                        normalize(user.getFullName())
                ));

        return repository.findAll().stream()
                .map(entity -> withDisplayManager(
                        entity,
                        buildingHeadByGroupCode.get(normalizeOrganizationalCode(entity.getCode()))
                ))
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        SchoolBuilding entity = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Корпус не найден"));
        if (classroomLeadershipRepository.existsBySchoolBuildingId(id)) {
            throw new IllegalStateException("Нельзя удалить площадку: к ней привязаны классы");
        }
        if (metaGroupRepository.existsBySchoolBuildingId(id)) {
            throw new IllegalStateException("Нельзя удалить площадку: к ней привязаны метагруппы");
        }
        repository.deleteById(entity.getId());
    }


    private SchoolBuilding withDisplayManager(SchoolBuilding source, String assignedManagerFio) {
        SchoolBuilding copy = new SchoolBuilding();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setBuildingGroup(source.getBuildingGroup());
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
        boolean hasUsedPhysicalSites = repository.findAll().stream()
                .map(SchoolBuilding::getId)
                .anyMatch(this::isPhysicalSiteReferenced);
        if (hasUsedPhysicalSites) {
            throw new IllegalStateException("Нельзя очистить список площадок: есть площадки, к которым привязаны классы или метагруппы");
        }
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
            header.createCell(3).setCellValue("BUILDING_GROUP_ID");

            int rowIdx = 1;
            for (SchoolBuilding building : repository.findAll()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(normalize(building.getCode()));
                row.createCell(1).setCellValue(normalize(building.getName()));
                row.createCell(2).setCellValue(normalize(building.getAddress()));
                Long buildingGroupId = building.getBuildingGroupId();
                if (buildingGroupId != null) {
                    row.createCell(3).setCellValue(buildingGroupId);
                }
            }

            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
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
                String buildingGroupIdText = normalize(formatter.formatCellValue(row.getCell(3)));

                if (name.equalsIgnoreCase("Название") || code.equalsIgnoreCase("Код")) {
                    if (!"BUILDING_GROUP_ID".equalsIgnoreCase(buildingGroupIdText)) {
                        throw new IllegalArgumentException("Файл корпусов создан в старом формате: добавьте колонку BUILDING_GROUP_ID из нового шаблона");
                    }
                    skipped++;
                    continue;
                }

                if (name.isBlank() || address.isBlank()) {
                    skipped++;
                    continue;
                }

                Long buildingGroupId = parseRequiredLong(buildingGroupIdText, "BUILDING_GROUP_ID", i + 1);
                SchoolBuildingRequest request = new SchoolBuildingRequest();
                request.setCode(code);
                request.setBuildingGroupId(buildingGroupId);
                request.setName(name);
                request.setAddress(address);
                upsert(request);
                imported++;
            }

            return java.util.Map.of("status", "ok", "imported", imported, "skipped", skipped, "total", repository.count());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать корпуса: " + e.getMessage(), e);
        }
    }

    private Long parseRequiredLong(String value, String column, int rowNumber) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Строка " + rowNumber + ": " + column + " is required for school building import");
        }
        try {
            return Long.valueOf(normalized.replace(".0", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Строка " + rowNumber + ": " + column + " must be numeric");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOrganizationalCode(String value) {
        String normalized = normalize(value)
                .replace('\u00A0', ' ')
                .trim()
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace('–', '-')
                .replace('—', '-');
        int idx = normalized.indexOf("|");
        if (idx >= 0) {
            normalized = normalized.substring(0, idx);
        }
        return normalized.replaceFirst("^СП-(\\d+)$", "СП$1");
    }

    private String buildPhysicalSiteCode(BuildingGroup group, String address) {
        String groupCode = normalizeOrganizationalCode(group == null ? null : group.getCode())
                .toLowerCase(java.util.Locale.ROOT);
        if (groupCode.isBlank()) {
            throw new IllegalArgumentException("buildingGroup code is required");
        }
        String normalizedAddress = normalize(address)
                .replace('\u00A0', ' ')
                .trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ");
        if (normalizedAddress.isBlank()) {
            throw new IllegalArgumentException("address is required");
        }
        return groupCode + "|" + normalizedAddress;
    }

    private boolean isPhysicalSiteReferenced(Long schoolBuildingId) {
        return schoolBuildingId != null
                && (classroomLeadershipRepository.existsBySchoolBuildingId(schoolBuildingId)
                || metaGroupRepository.existsBySchoolBuildingId(schoolBuildingId));
    }

}
