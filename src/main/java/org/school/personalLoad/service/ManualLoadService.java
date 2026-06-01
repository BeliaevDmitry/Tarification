package org.school.personalLoad.service;

import org.school.personalLoad.dto.ManualLoadBulkRequest;
import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadHealthResponse;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.dto.ManualLoadStatsResponse;
import org.school.personalLoad.model.ManualLoadEntry;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ManualLoadService {
    ManualLoadEntry create(ManualLoadEntryRequest request);

    List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests);

    default List<ManualLoadEntry> createBulk(ManualLoadBulkRequest request) {
        return createBulk(request == null ? List.of() : request.getRows());
    }

    List<ManualLoadEntry> findAll(String academicYear);

    default List<ManualLoadEntry> findAll(String academicYear, String numberSchoolBuilding) {
        return findAll(academicYear);
    }

    default List<ManualLoadEntry> findAll(String academicYear, String numberSchoolBuilding, String campusAddress) {
        return findAll(academicYear, numberSchoolBuilding);
    }

    void clearAll(String academicYear);

    default void clearByBuilding(String academicYear, String numberSchoolBuilding) {
        throw new UnsupportedOperationException("clearByBuilding is not implemented");
    }

    default void clearByBuildingAddress(String academicYear, String numberSchoolBuilding, String campusAddress) {
        clearByBuilding(academicYear, numberSchoolBuilding);
    }

    ManualLoadProcessResult processCurrentManualLoad(String academicYear);

    byte[] exportWorkbook(String academicYear) throws IOException;
    byte[] exportFullWorkbook(String academicYear) throws IOException;
    byte[] exportFullWorkbookWithSalary(String academicYear) throws IOException;

    List<ManualLoadEntry> importWorkbook(String academicYear, MultipartFile file);

    ManualLoadStatsResponse buildStats(String academicYear, String numberSchoolBuilding, int page, int pageSize);

    ManualLoadHealthResponse buildHealth(String academicYear, String numberSchoolBuilding);
}
