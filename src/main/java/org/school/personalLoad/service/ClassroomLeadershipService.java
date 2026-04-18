package org.school.personalLoad.service;

import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ClassroomLeadershipService {
    List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests);

    List<ClassroomLeadershipEntry> findAll(String academicYear);

    void deleteOne(String academicYear, String numberSchoolBuilding, String className);

    void clearAll(String academicYear);

    Map<String, Object> importFromExcel(String academicYear, MultipartFile file);

    Resource buildImportTemplate(String academicYear);
}
