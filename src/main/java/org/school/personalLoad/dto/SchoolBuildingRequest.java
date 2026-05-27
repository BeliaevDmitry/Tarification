package org.school.personalLoad.dto;

import lombok.Data;

@Data
public class SchoolBuildingRequest {
    private Long id;
    private String code;
    private String name;
    private String managerFio;
    private String address;
}
