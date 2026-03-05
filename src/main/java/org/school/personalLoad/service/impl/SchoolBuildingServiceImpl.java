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
        String name = normalize(request.getName());
        String managerFio = normalize(request.getManagerFio());
        String address = normalize(request.getAddress());
        if (name.isBlank()) throw new IllegalArgumentException("name is required");
        if (managerFio.isBlank()) throw new IllegalArgumentException("managerFio is required");
        if (address.isBlank()) throw new IllegalArgumentException("address is required");

        String code = normalize(request.getCode());
        if (code.isBlank()) {
            code = (name + "|" + address).toLowerCase();
        }

        SchoolBuilding entity = repository.findByCode(code).orElseGet(SchoolBuilding::new);
        entity.setCode(code);
        entity.setName(name);
        entity.setManagerFio(managerFio);
        entity.setAddress(address);
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
