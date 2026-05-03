package org.school.personalLoad.pa.service;

import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaWorkType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface PaService {
    List<PaDtos.ImportResult> importSpecifications(String academicYear, List<MultipartFile> files, String username);
    List<PaDtos.ImportLogRow> specificationImportLog(String academicYear, String username, boolean admin);
    byte[] loadSpecificationImportLogFile(String academicYear, Long importLogId) throws IOException;
    String specificationImportLogFileName(String academicYear, Long importLogId);
    List<PaDtos.SpecificationRow> specifications(String academicYear);
    List<PaDtos.SpecificationTaskRow> specificationTasks(Long specificationId);
    void deleteSpecification(String academicYear, Long specificationId) throws IOException;
    List<PaDtos.ClassLevelAssignmentRow> classLevelAssignments(String academicYear);
    void saveClassLevelAssignments(String academicYear, List<PaDtos.ClassLevelAssignmentRow> rows);
    PaDtos.SummaryResponse summary(String academicYear);
    List<PaDtos.ReportVersionRow> reportVersions(String academicYear, String subjectName, PaScopeType scopeType, String scopeValue, PaLevel level, PaWorkType workType, LocalDate workDate);
    List<PaDtos.ReportWorkflowSummaryItem> reportWorkflowSummary(String academicYear, PaLevel level, PaWorkType workType, String subjectName);
    List<PaDtos.ReportUploadResult> uploadReports(String academicYear, List<MultipartFile> files);
    void setParticipation(String academicYear, String subjectName, PaScopeType scopeType, String scopeValue, PaLevel level, boolean participates);
    PaDtos.ReportUploadResult generateReportTemplate(String academicYear, String subjectName, String className, PaLevel level, PaWorkType workType, LocalDate workDate, boolean force);
    List<PaDtos.ReportUploadResult> generateReportTemplatesByParallel(String academicYear, String subjectName, String parallel, PaLevel level, PaWorkType workType, LocalDate workDate, boolean force);
    List<PaDtos.ReportUploadResult> generateAllReportTemplates(String academicYear, String subjectName, PaLevel level, PaWorkType workType, LocalDate workDate, boolean force);
    int deleteGeneratedReports(String academicYear, String subjectName, String scopeValue, boolean byParallel, PaLevel level, PaWorkType workType, LocalDate workDate);
    List<PaDtos.ReportFolderItem> reportFolderItems(String academicYear, PaWorkType workType);
    byte[] loadReportFile(Long reportVersionId) throws IOException;
    String reportFileName(Long reportVersionId);
    byte[] loadSpecificationFile(String academicYear, Long specificationId) throws IOException;
    String specificationFileName(String academicYear, Long specificationId);
}
