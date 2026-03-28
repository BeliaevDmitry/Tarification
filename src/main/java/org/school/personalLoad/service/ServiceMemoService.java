package org.school.personalLoad.service;

import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.ServiceMemo;

import java.util.List;

public interface ServiceMemoService {
    List<ServiceMemoDtos.PendingTeacher> findPendingTeachers();

    List<ServiceMemoDtos.ProcessedMemo> findProcessed();

    List<ServiceMemoDtos.ProcessedMemo> findArchived();

    List<ServiceMemoDtos.ProcessedMemo> generateForTeachers(List<String> fioTeachers, String createdBy);

    ServiceMemo getById(Long id);

    ServiceMemo archive(Long id);

    ServiceMemo uploadCorrected(Long id, String filename, byte[] content);
}
