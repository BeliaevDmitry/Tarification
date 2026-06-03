package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class SchoolBuildingRequest {
    private Long id;
    private String code;
    private Long buildingGroupId;
    private String name;
    private String managerFio;
    private String address;
}
