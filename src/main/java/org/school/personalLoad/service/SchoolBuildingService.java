package org.school.personalLoad.service;

import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SchoolBuildingService {
    SchoolBuilding upsert(SchoolBuildingRequest request);

    List<SchoolBuilding> findAll();

    void deleteById(Long id);

    void clearAll();

    byte[] exportToExcel();

    java.util.Map<String, Object> importFromExcel(MultipartFile file);
}
