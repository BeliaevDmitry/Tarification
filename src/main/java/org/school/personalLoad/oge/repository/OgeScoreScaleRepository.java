package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.OgeScoreScaleEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OgeScoreScaleRepository extends JpaRepository<OgeScoreScaleEntry, Long> {
    List<OgeScoreScaleEntry> findAllByAcademicYearOrderByScoreAscSubjectNameAsc(String academicYear);

    long countByAcademicYear(String academicYear);

    void deleteAllByAcademicYear(String academicYear);
}
