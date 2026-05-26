package org.school.personalLoad.service;

import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherUpdateRequest;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TeacherDirectoryService {
    Map<String, Object> importFromExcel(MultipartFile file);
    Resource buildImportTemplate();

    TeacherDirectoryEntry create(TeacherCreateRequest request);
    TeacherDirectoryEntry update(Long teacherId, TeacherUpdateRequest request);

    TeacherDirectoryEntry markForDismissal(Long teacherId, LocalDate dismissalDate, String markedBy);
    TeacherDirectoryEntry markPlannedDismissal(Long teacherId, LocalDate plannedDismissalDate, String comment, String markedBy);

    TeacherDirectoryEntry restore(Long teacherId);

    void deleteById(Long teacherId);

    List<TeacherDirectoryEntry> findAll();

    void clearAll();
}
