package org.school.personalLoad.service;

import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;

import java.util.List;

public interface SchoolBuildingService {
    SchoolBuilding upsert(SchoolBuildingRequest request);

    List<SchoolBuilding> findAll();

    void clearAll();
}
