package org.school.personalLoad.service;

import org.school.personalLoad.dto.ContingentDtos;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface ContingentService {
    ContingentDtos.ImportResultResponse importSnapshot(String academicYear, MultipartFile file, LocalDate fallbackSnapshotDate);
    List<ContingentDtos.SnapshotResponse> listSnapshots(String academicYear);
    List<ContingentDtos.StudentResponse> listStudents(Long snapshotId, String buildingCode, Integer parallel, String className, String query);
    List<ContingentDtos.ClassSummaryResponse> classSummary(Long snapshotId);
    List<ContingentDtos.ParallelSummaryResponse> parallelSummary(Long snapshotId);
    List<ContingentDtos.BuildingSummaryResponse> buildingSummary(Long snapshotId);
    List<ContingentDtos.WarningResponse> warnings(Long snapshotId);
    List<ContingentDtos.WarningResponse> recalculateWarnings(Long snapshotId);
}

