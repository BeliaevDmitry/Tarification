package org.school.personalLoad.service;

import org.school.personalLoad.model.AcademicYearConfig;

import java.util.List;

public interface AcademicYearService {
    List<AcademicYearConfig> findAll();
    AcademicYearConfig create(String code);
    void delete(Long id);
    String resolveRequestedOrDefault(String requestedCode);
    String currentByDate();
    AcademicYearConfig markContinuityApplied(String code);
}
