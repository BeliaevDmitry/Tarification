package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<PaReportVersion> findAllByAcademicYearAndLevelAndWorkType(String academicYear, PaLevel level, PaWorkType workType);

    PaReportVersion findTopByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDateOrderByVersionNoDesc(
            String academicYear,
            String subjectName,
            PaScopeType scopeType,
            String scopeValue,
            PaLevel level,
            PaWorkType workType,
            LocalDate workDate
    );

    PaReportVersion findTopByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDateIsNullOrderByVersionNoDesc(
            String academicYear,
            String subjectName,
            PaScopeType scopeType,
            String scopeValue,
            PaLevel level,
            PaWorkType workType
    );
}
