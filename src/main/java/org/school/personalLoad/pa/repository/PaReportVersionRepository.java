package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PaReportVersionRepository extends JpaRepository<PaReportVersion, Long> {

    List<PaReportVersion> findTop10ByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDateOrderByCreatedAtDesc(
            String academicYear,
            String subjectName,
            PaScopeType scopeType,
            String scopeValue,
            PaLevel level,
            PaWorkType workType,
            LocalDate workDate
    );

    List<PaReportVersion> findAllByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDate(
            String academicYear,
            String subjectName,
            PaScopeType scopeType,
            String scopeValue,
            PaLevel level,
            PaWorkType workType,
            LocalDate workDate
    );

    @Query("select coalesce(max(r.versionNo), 0) from PaReportVersion r where r.academicYear = :academicYear and r.subjectName = :subjectName and r.scopeType = :scopeType and r.scopeValue = :scopeValue and r.level = :level and r.workType = :workType and ((r.workDate is null and :workDate is null) or r.workDate = :workDate)")
    int findMaxVersion(@Param("academicYear") String academicYear,
                       @Param("subjectName") String subjectName,
                       @Param("scopeType") PaScopeType scopeType,
                       @Param("scopeValue") String scopeValue,
                       @Param("level") PaLevel level,
                       @Param("workType") PaWorkType workType,
                       @Param("workDate") LocalDate workDate);
}
