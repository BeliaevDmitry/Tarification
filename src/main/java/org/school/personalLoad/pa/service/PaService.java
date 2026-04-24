package org.school.personalLoad.pa.service;

import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaWorkType;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface PaService {
    List<PaDtos.ImportResult> importSpecifications(String academicYear, List<MultipartFile> files);
    List<PaDtos.SpecificationRow> specifications(String academicYear);
    List<PaDtos.SpecificationTaskRow> specificationTasks(Long specificationId);
    PaDtos.SummaryResponse summary(String academicYear);
    List<PaDtos.ReportVersionRow> reportVersions(String academicYear, String subjectName, PaScopeType scopeType, String scopeValue, PaLevel level, PaWorkType workType, LocalDate workDate);
    List<PaDtos.ReportUploadResult> uploadReports(String academicYear, List<MultipartFile> files);
}
