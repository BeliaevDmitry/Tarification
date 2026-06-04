package org.school.personalLoad.service;

import org.school.personalLoad.dto.BuildingGroupCreateRequest;
import org.school.personalLoad.dto.BuildingGroupCreateResponse;
import org.school.personalLoad.model.BuildingGroup;

import java.util.List;

public interface BuildingGroupService {
    List<BuildingGroup> findAll();

    BuildingGroupCreateResponse createWithInitialSite(BuildingGroupCreateRequest request);
}
