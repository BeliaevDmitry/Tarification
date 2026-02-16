package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.service.SchoolBuildingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchoolBuildingServiceImpl implements SchoolBuildingService {

    private final SchoolBuildingRepository repository;

    @Override
    public SchoolBuilding upsert(SchoolBuildingRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        String code = normalize(request.getCode());
        String name = normalize(request.getName());
        if (code.isBlank()) throw new IllegalArgumentException("code is required");
        if (name.isBlank()) throw new IllegalArgumentException("name is required");

        SchoolBuilding entity = repository.findByCode(code).orElseGet(SchoolBuilding::new);
        entity.setCode(code);
        entity.setName(name);
        return repository.save(entity);
    }

    @Override
    public List<SchoolBuilding> findAll() {
        return repository.findAll();
    }

    @Override
    public void clearAll() {
        repository.deleteAll();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
