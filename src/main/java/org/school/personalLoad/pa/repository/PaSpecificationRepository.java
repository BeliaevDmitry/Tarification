package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaSpecification;
import org.school.personalLoad.pa.model.PaWorkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PaSpecificationRepository extends JpaRepository<PaSpecification, Long> {

    List<PaSpecification> findAllByAcademicYearOrderBySubjectNameAscScopeTypeAscScopeValueAscLevelAscWorkTypeAsc(String academicYear);

    @Query("select coalesce(max(s.versionNo), 0) from PaSpecification s where s.academicYear = :academicYear and s.subjectName = :subjectName and s.scopeType = :scopeType and s.scopeValue = :scopeValue and s.level = :level and s.workType = :workType and ((s.workDate is null and :workDate is null) or s.workDate = :workDate)")
    int findMaxVersion(@Param("academicYear") String academicYear,
                       @Param("subjectName") String subjectName,
                       @Param("scopeType") PaScopeType scopeType,
                       @Param("scopeValue") String scopeValue,
                       @Param("level") PaLevel level,
                       @Param("workType") PaWorkType workType,
                       @Param("workDate") LocalDate workDate);
}
