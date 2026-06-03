package org.school.personalLoad.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class ManualLoadBulkRequest {
    private String academicYear;
    private String scopeType;
    private String numberSchoolBuilding;
    private String campusAddress;
    private Long schoolBuildingId;
    private Set<Long> classIds = new LinkedHashSet<>();
    private List<ManualLoadEntryRequest> rows = new ArrayList<>();
}
