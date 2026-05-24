package org.school.personalLoad.service;

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

    List<ManualLoadEntry> findAll(String academicYear);

    default List<ManualLoadEntry> findAll(String academicYear, String numberSchoolBuilding) {
        return findAll(academicYear);
    }

    void clearAll(String academicYear);

    default void clearByBuilding(String academicYear, String numberSchoolBuilding) {
        throw new UnsupportedOperationException("clearByBuilding is not implemented");
    }

    ManualLoadProcessResult processCurrentManualLoad(String academicYear);

    byte[] exportWorkbook(String academicYear) throws IOException;
    byte[] exportFullWorkbook(String academicYear) throws IOException;

    List<ManualLoadEntry> importWorkbook(String academicYear, MultipartFile file);

    ManualLoadStatsResponse buildStats(String academicYear, String numberSchoolBuilding, int page, int pageSize);

    ManualLoadHealthResponse buildHealth(String academicYear, String numberSchoolBuilding);
}
