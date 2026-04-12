package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyPeriodSettingRepository extends JpaRepository<StudyPeriodSetting, Long> {
    Optional<StudyPeriodSetting> findByCodeAndAcademicYear(String code, String academicYear);
    List<StudyPeriodSetting> findAllByAcademicYearOrderByParallelFromAscParallelToAscIdAsc(String academicYear);
    List<StudyPeriodSetting> findByAcademicYearAndParallelFromLessThanEqualAndParallelToGreaterThanEqualOrderByDefaultRuleDescIdAsc(String academicYear, Integer parallelFrom, Integer parallelTo);
    List<StudyPeriodSetting> findByAcademicYearAndParallelFromLessThanEqualAndParallelToGreaterThanEqualAndStudyPeriodOrderByDefaultRuleDescIdAsc(String academicYear, Integer parallelFrom, Integer parallelTo, StudyPeriod studyPeriod);

    @Deprecated
    default Optional<StudyPeriodSetting> findByCode(String code) {
        return findByCodeAndAcademicYear(code, "");
    }

    @Deprecated
    default List<StudyPeriodSetting> findByParallelFromLessThanEqualAndParallelToGreaterThanEqualOrderByDefaultRuleDescIdAsc(Integer parallelFrom, Integer parallelTo) {
        return findByAcademicYearAndParallelFromLessThanEqualAndParallelToGreaterThanEqualOrderByDefaultRuleDescIdAsc("", parallelFrom, parallelTo);
    }

    @Deprecated
    default List<StudyPeriodSetting> findByParallelFromLessThanEqualAndParallelToGreaterThanEqualAndStudyPeriodOrderByDefaultRuleDescIdAsc(Integer parallelFrom, Integer parallelTo, StudyPeriod studyPeriod) {
        return findByAcademicYearAndParallelFromLessThanEqualAndParallelToGreaterThanEqualAndStudyPeriodOrderByDefaultRuleDescIdAsc("", parallelFrom, parallelTo, studyPeriod);
    }
}
