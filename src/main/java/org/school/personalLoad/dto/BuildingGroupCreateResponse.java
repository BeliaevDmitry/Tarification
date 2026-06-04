package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.SchoolBuilding;

@Data
@AllArgsConstructor
public class BuildingGroupCreateResponse {
    private BuildingGroup buildingGroup;
    private SchoolBuilding schoolBuilding;
}
