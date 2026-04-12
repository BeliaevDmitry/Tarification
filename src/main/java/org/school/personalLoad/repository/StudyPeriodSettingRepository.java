package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyPeriodSettingRepository extends JpaRepository<StudyPeriodSetting, Long> {
    Optional<StudyPeriodSetting> findByCode(String code);
    List<StudyPeriodSetting> findByParallelFromLessThanEqualAndParallelToGreaterThanEqualOrderByDefaultRuleDescIdAsc(Integer parallelFrom, Integer parallelTo);
    List<StudyPeriodSetting> findByParallelFromLessThanEqualAndParallelToGreaterThanEqualAndStudyPeriodOrderByDefaultRuleDescIdAsc(Integer parallelFrom, Integer parallelTo, StudyPeriod studyPeriod);
    Optional<StudyPeriodSetting> findByAcademicYearAndCode(String academicYear, String code);
    List<StudyPeriodSetting> findByAcademicYearAndParallelFromLessThanEqualAndParallelToGreaterThanEqualOrderByDefaultRuleDescIdAsc(String academicYear, Integer parallelFrom, Integer parallelTo);
    List<StudyPeriodSetting> findByAcademicYearAndParallelFromLessThanEqualAndParallelToGreaterThanEqualAndStudyPeriodOrderByDefaultRuleDescIdAsc(String academicYear, Integer parallelFrom, Integer parallelTo, StudyPeriod studyPeriod);
    List<StudyPeriodSetting> findAllByAcademicYearOrderByIdAsc(String academicYear);
}
