package org.school.personalLoad.service;

import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ClassroomLeadershipService {
    List<ClassroomLeadershipEntry> replaceAll(String academicYear, List<ClassroomLeadershipEntryRequest> requests);

    List<ClassroomLeadershipEntry> findAll(String academicYear);

    void deleteOne(String academicYear, String numberSchoolBuilding, String className);

    void clearAll(String academicYear);

    Map<String, Object> importFromExcel(String academicYear, MultipartFile file);

    Resource buildImportTemplate(String academicYear);

    default List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests) { return replaceAll(null, requests); }
    default List<ClassroomLeadershipEntry> findAll() { return findAll(null); }
    default void deleteOne(String numberSchoolBuilding, String className) { deleteOne(null, numberSchoolBuilding, className); }
    default void clearAll() { clearAll(null); }
    default Map<String, Object> importFromExcel(MultipartFile file) { return importFromExcel(null, file); }
    default Resource buildImportTemplate() { return buildImportTemplate(null); }
}
