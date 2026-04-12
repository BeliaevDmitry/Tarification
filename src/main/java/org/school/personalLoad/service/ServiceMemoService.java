package org.school.personalLoad.service;

import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.ServiceMemo;

import java.util.List;

public interface ServiceMemoService {
    List<ServiceMemoDtos.PendingTeacher> findPendingTeachers(String academicYear);

    List<ServiceMemoDtos.ProcessedMemo> findProcessed(String academicYear);

    List<ServiceMemoDtos.ProcessedMemo> findArchived(String academicYear);

    List<ServiceMemoDtos.ProcessedMemo> generateForTeachers(String academicYear, List<String> fioTeachers, String createdBy);

    default List<ServiceMemoDtos.PendingTeacher> findPendingTeachers() {
        return findPendingTeachers(currentAcademicYear());
    }

    default List<ServiceMemoDtos.ProcessedMemo> findProcessed() {
        return findProcessed(null);
    }

    default List<ServiceMemoDtos.ProcessedMemo> findArchived() {
        return findArchived(null);
    }

    default List<ServiceMemoDtos.ProcessedMemo> generateForTeachers(List<String> fioTeachers, String createdBy) {
        return generateForTeachers(currentAcademicYear(), fioTeachers, createdBy);
    }

    ServiceMemo getById(Long id);

    ServiceMemo archive(Long id);

    ServiceMemo uploadCorrected(Long id, String filename, byte[] content);

    private static String currentAcademicYear() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int start = now.getMonthValue() >= 7 ? now.getYear() : now.getYear() - 1;
        return start + "/" + (start + 1);
    }
}
