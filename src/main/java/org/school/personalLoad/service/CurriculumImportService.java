package org.school.personalLoad.service;

import org.school.personalLoad.dto.CurriculumImportResult;
import org.springframework.web.multipart.MultipartFile;

public interface CurriculumImportService {
    CurriculumImportResult importFile(MultipartFile file);
}
