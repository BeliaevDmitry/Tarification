package org.school.personalLoad.service;

import org.school.personalLoad.dto.CurriculumImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface CurriculumImportService {
    CurriculumImportResult importFile(String academicYear, MultipartFile file, boolean confirmLargeReduction);

    byte[] exportEditableWorkbook(String academicYear) throws IOException;
}
