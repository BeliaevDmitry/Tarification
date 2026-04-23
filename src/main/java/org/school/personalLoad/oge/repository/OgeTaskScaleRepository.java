package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.OgeTaskScaleEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OgeTaskScaleRepository extends JpaRepository<OgeTaskScaleEntry, Long> {
    List<OgeTaskScaleEntry> findAllByAcademicYearOrderBySubjectNameAscTaskNumberAsc(String academicYear);
    void deleteAllByAcademicYear(String academicYear);
    long countByAcademicYear(String academicYear);
}

