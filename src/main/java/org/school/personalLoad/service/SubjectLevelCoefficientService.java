package org.school.personalLoad.service;

import org.school.personalLoad.dto.SubjectLevelCoefficientRequest;
import org.school.personalLoad.model.SubjectLevelCoefficientEntry;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface SubjectLevelCoefficientService {
    SubjectLevelCoefficientEntry save(SubjectLevelCoefficientRequest request);
    void deleteById(Long id);
    List<SubjectLevelCoefficientEntry> findAll();
    Map<String, Object> importFromExcel(MultipartFile file);
    Resource exportWorkbook();
}
