package org.school.personalLoad.repository;

import org.school.personalLoad.model.CorrectionScheduleGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CorrectionScheduleGroupRepository extends JpaRepository<CorrectionScheduleGroup, Long> {
    List<CorrectionScheduleGroup> findAllByAcademicYearAndStaff_IdOrderByWeekdayAscStartTimeAsc(
            String academicYear, Long staffId);

    long countByAcademicYearAndStaff_Id(String academicYear, Long staffId);

    @Query("select coalesce(max(g.sequenceNumber), 0) from CorrectionScheduleGroup g " +
            "where g.academicYear = :academicYear and g.staff.id = :staffId")
    int maxSequenceNumber(@Param("academicYear") String academicYear, @Param("staffId") Long staffId);
}
