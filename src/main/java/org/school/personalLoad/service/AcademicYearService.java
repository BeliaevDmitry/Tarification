package org.school.personalLoad.service;

import org.school.personalLoad.model.AcademicYear;

import java.util.List;

public interface AcademicYearService {
    List<AcademicYear> findAll();
    AcademicYear resolveCurrent();
    AcademicYear resolveByNameOrCurrent(String name);
    AcademicYear create(Integer startYear);
    void delete(Long id);
    String formatName(int startYear);
}

