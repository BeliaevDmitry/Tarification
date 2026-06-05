package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class BuildingGroupCreateRequest {
    private String code;
    private String name;
    private Boolean createInitialSite;
    private String initialSiteName;
    private String initialAddress;
    private Long baseSchoolBuildingId;
}
