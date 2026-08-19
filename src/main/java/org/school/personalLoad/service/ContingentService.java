package org.school.personalLoad.service;

import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.school.personalLoad.model.ClassSizeSource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;



public interface ContingentService {
    ContingentDtos.ImportResponse importSnapshot(String academicYear, MultipartFile file);

    List<ContingentDtos.SnapshotListItem> listSnapshots(String academicYear);

    ContingentDtos.StatsResponse getStats(String academicYear, LocalDate snapshotDate);

    List<ContingentDtos.ClassStudentView> getClassStudents(String academicYear, LocalDate snapshotDate, String className);

    byte[] exportClassStudents(String academicYear, LocalDate snapshotDate, String className);

    byte[] exportStats(String academicYear, LocalDate snapshotDate);

    List<ContingentDtos.ImportProblem> getProblems(String academicYear, Long snapshotId);

    ContingentDtos.ImportMismatchResponse getImportMismatches(String academicYear, Long snapshotId);

    ContingentDtos.ImportMismatchResponse resolveImportMismatch(
            String academicYear,
            ContingentDtos.ResolveImportMismatchRequest request
    );

    ContingentDtos.ManualClassSizeResponse getManualClassSizes(String academicYear);

    ContingentDtos.ManualClassSizeResponse saveManualClassSizes(String academicYear, ContingentDtos.ManualClassSizeSaveRequest request);

    ContingentDtos.ManualClassSizeResponse importManualClassSizes(String academicYear, MultipartFile file);

    byte[] exportManualClassSizes(String academicYear);

    ContingentDtos.ManualClassSizeResponse setClassSizeSource(String academicYear, ClassSizeSource source);
}
