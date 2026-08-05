package org.school.personalLoad.repository;

import org.school.personalLoad.model.CurriculumMeshMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CurriculumMeshMappingRepository extends JpaRepository<CurriculumMeshMapping, Long> {
    List<CurriculumMeshMapping> findAllByAcademicYear(String academicYear);

    Optional<CurriculumMeshMapping> findByAcademicYearAndCurriculumEntryIdAndGroupNameUp(
            String academicYear,
            Long curriculumEntryId,
            String groupNameUp
    );
}
