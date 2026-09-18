package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaWorkMaterialRepository extends JpaRepository<PaWorkMaterial, Long> {

    List<PaWorkMaterial> findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc(String academicYear);

    Optional<PaWorkMaterial> findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndVariantCountOrderByUpdatedAtDesc(
            String academicYear, String subjectName, PaScopeType scopeType, String scopeValue,
            PaLevel level, PaWorkType workType, Integer variantCount);

    @Query("select distinct m.academicYear from PaWorkMaterial m order by m.academicYear desc")
    List<String> findDistinctAcademicYears();
}
