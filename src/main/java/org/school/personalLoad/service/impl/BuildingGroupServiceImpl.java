package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.BuildingGroupCreateRequest;
import org.school.personalLoad.dto.BuildingGroupCreateResponse;
import org.school.personalLoad.dto.BuildingGroupUpdateRequest;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.BuildingGroupService;
import org.school.personalLoad.service.SchoolBuildingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BuildingGroupServiceImpl implements BuildingGroupService {

    private final BuildingGroupRepository buildingGroupRepository;
    private final SchoolBuildingService schoolBuildingService;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final AppUserRepository appUserRepository;

    @Override
    public List<BuildingGroup> findAll() {
        Map<String, String> managerByGroupCode = buildingHeadByGroupCode();
        return buildingGroupRepository.findAll().stream()
                .peek(group -> group.setManagerFio(managerByGroupCode.get(normalizeOrganizationalCode(group.getCode()))))
                .toList();
    }

    @Override
    @Transactional
    public BuildingGroupCreateResponse createWithInitialSite(BuildingGroupCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String code = normalizeCode(request.getCode());
        String name = normalizeRequired(request.getName(), "name is required");
        boolean createInitialSite = request.getCreateInitialSite() == null || Boolean.TRUE.equals(request.getCreateInitialSite());
        String initialAddress = createInitialSite ? normalizeRequired(request.getInitialAddress(), "initialAddress is required") : "";
        String initialSiteName = normalize(request.getInitialSiteName());
        if (createInitialSite && initialSiteName.isBlank()) {
            initialSiteName = name;
        }
        SchoolBuilding baseSchoolBuilding = null;
        if (!createInitialSite && request.getBaseSchoolBuildingId() != null) {
            baseSchoolBuilding = schoolBuildingRepository.findById(request.getBaseSchoolBuildingId())
                    .orElseThrow(() -> new IllegalArgumentException("Базовая физическая площадка не найдена: " + request.getBaseSchoolBuildingId()));
        }

        buildingGroupRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Основной корпус с кодом «" + code + "» уже существует. Выберите его в форме «Добавить адрес к существующему корпусу».");
        });

        BuildingGroup group = new BuildingGroup();
        group.setCode(code);
        group.setName(name);
        BuildingGroup savedGroup = buildingGroupRepository.saveAndFlush(group);

        if (!createInitialSite) {
            return new BuildingGroupCreateResponse(savedGroup, null, baseSchoolBuilding);
        }

        SchoolBuildingRequest siteRequest = new SchoolBuildingRequest();
        siteRequest.setBuildingGroupId(savedGroup.getId());
        siteRequest.setName(initialSiteName);
        siteRequest.setAddress(initialAddress);
        SchoolBuilding savedSite = schoolBuildingService.upsert(siteRequest);

        return new BuildingGroupCreateResponse(savedGroup, savedSite, null);
    }


    @Override
    @Transactional
    public BuildingGroup update(Long id, BuildingGroupUpdateRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        BuildingGroup group = buildingGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Основной корпус не найден: " + id));

        String requestedCode = normalize(request.getCode());
        if (!requestedCode.isBlank()) {
            String normalizedRequestedCode = requestedCode.toUpperCase(Locale.ROOT);
            if (!normalize(group.getCode()).equalsIgnoreCase(normalizedRequestedCode)) {
                throw new IllegalArgumentException("Изменение кода основного корпуса временно запрещено: код используется в правах руководителей, классах и нагрузке");
            }
        }

        String name = normalizeRequired(request.getName(), "name is required");
        group.setName(name);
        BuildingGroup saved = buildingGroupRepository.save(group);
        saved.setManagerFio(buildingHeadByGroupCode().get(normalizeOrganizationalCode(saved.getCode())));
        return saved;
    }

    private String normalizeCode(String value) {
        String normalized = normalizeRequired(value, "code is required")
                .toUpperCase(Locale.ROOT);
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private Map<String, String> buildingHeadByGroupCode() {
        Map<String, String> managerByGroupCode = new LinkedHashMap<>();
        appUserRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.BUILDING_HEAD)
                .filter(user -> !normalize(user.getManagedBuildingCode()).isBlank())
                .forEach(user -> managerByGroupCode.put(
                        normalizeOrganizationalCode(user.getManagedBuildingCode()),
                        normalize(user.getFullName())
                ));
        return managerByGroupCode;
    }

    private String normalizeOrganizationalCode(String value) {
        String normalized = normalize(value)
                .replace('\u00A0', ' ')
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace('–', '-')
                .replace('—', '-');
        int idx = normalized.indexOf("|");
        if (idx >= 0) {
            normalized = normalized.substring(0, idx);
        }
        return normalized.replaceFirst("^СП-(\\d+)$", "СП$1");
    }
}
