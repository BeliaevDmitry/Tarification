package org.school.personalLoad.service;

import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherOneCImportDtos;
import org.school.personalLoad.dto.TeacherUpdateRequest;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TeacherDirectoryService {
    Map<String, Object> importFromExcel(MultipartFile file);
    TeacherOneCImportDtos.Preview previewOneCImport(MultipartFile file);
    Map<String, Object> applyOneCImport(MultipartFile file,
                                        TeacherOneCImportDtos.ApplyRequest request,
                                        String processedBy);
    Resource buildImportTemplate();

    TeacherDirectoryEntry create(TeacherCreateRequest request);
    TeacherDirectoryEntry update(Long teacherId, TeacherUpdateRequest request);

    TeacherDirectoryEntry markForDismissal(Long teacherId, LocalDate dismissalDate, String markedBy);
    TeacherDirectoryEntry markPlannedDismissal(Long teacherId, LocalDate plannedDismissalDate, String comment, String markedBy);
    TeacherDirectoryEntry cancelPlannedDismissal(Long teacherId);

    TeacherDirectoryEntry restore(Long teacherId);
    TeacherDirectoryEntry archive(Long teacherId);
    TeacherDirectoryEntry unarchive(Long teacherId);

    void deleteById(Long teacherId);

    List<TeacherDirectoryEntry> findAll();
    List<TeacherDirectoryEntry> findArchived();

    void clearAll();
}
