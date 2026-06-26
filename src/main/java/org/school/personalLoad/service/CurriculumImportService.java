package org.school.personalLoad.service;

import org.school.personalLoad.dto.CurriculumImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface CurriculumImportService {
    CurriculumImportResult importFile(MultipartFile file, String academicYear);

    byte[] exportEditableWorkbook(String academicYear) throws IOException;

    byte[] exportParallelWorkbook(String academicYear) throws IOException;

    byte[] exportDepartmentWorkbook(String academicYear) throws IOException;
}
