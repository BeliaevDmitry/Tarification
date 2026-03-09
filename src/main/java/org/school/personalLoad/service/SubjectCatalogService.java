package org.school.personalLoad.service;

import org.school.personalLoad.dto.SubjectCreateRequest;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface SubjectCatalogService {
    SubjectCatalogEntry create(SubjectCreateRequest request);
    SubjectCatalogEntry update(Long id, SubjectCreateRequest request);
    void deleteById(Long id);
    List<SubjectCatalogEntry> findAll();
    void clearAll();
    Map<String, Object> importFromExcel(MultipartFile file);
    Resource buildImportTemplate();
}
