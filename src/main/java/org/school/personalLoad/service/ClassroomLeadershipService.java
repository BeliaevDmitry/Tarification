package org.school.personalLoad.service;

import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ClassroomLeadershipService {
    List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests);

    Map<String, Object> importFromExcel(MultipartFile file);

    List<ClassroomLeadershipEntry> findAll();

    void clearAll();
}
