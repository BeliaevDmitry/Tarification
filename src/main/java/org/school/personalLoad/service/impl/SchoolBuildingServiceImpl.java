package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.SchoolBuildingService;
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
        Map<String, String> buildingHeadByCode = new LinkedHashMap<>();
        appUserRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.BUILDING_HEAD)
                .filter(user -> !normalize(user.getManagedBuildingCode()).isBlank())
                .forEach(user -> buildingHeadByCode.put(normalize(user.getManagedBuildingCode()), normalize(user.getFullName())));

        return repository.findAll().stream()
                .map(entity -> withDisplayManager(entity, buildingHeadByCode.get(normalize(entity.getCode()))))
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
