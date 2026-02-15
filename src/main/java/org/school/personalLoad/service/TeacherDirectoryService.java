package org.school.personalLoad.service;

import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface TeacherDirectoryService {
    Map<String, Object> importFromExcel(MultipartFile file);

    List<TeacherDirectoryEntry> findAll();

    void clearAll();
}
