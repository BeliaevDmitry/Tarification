package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class ClassroomBuildingScopeUpdateRequest {
    private Long buildingGroupId;
    private Long schoolBuildingId;
}
