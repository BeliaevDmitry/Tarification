package org.school.personalLoad.service;

import org.school.personalLoad.dto.contingent.ContingentDtos;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface ContingentService {
    ContingentDtos.ImportResponse importSnapshot(String academicYear, MultipartFile file);

    List<ContingentDtos.SnapshotListItem> listSnapshots(String academicYear);

    ContingentDtos.StatsResponse getStats(String academicYear, LocalDate snapshotDate);

    List<ContingentDtos.ImportProblem> getProblems(String academicYear, Long snapshotId);
}
