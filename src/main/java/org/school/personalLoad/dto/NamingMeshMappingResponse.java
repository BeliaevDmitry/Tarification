package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NamingMeshMappingResponse {
    private String subjectName;
    private String className;
    private String groupNameEducationalPlan;
    private String classNameMesh;
    private String groupNameMesh;
}
