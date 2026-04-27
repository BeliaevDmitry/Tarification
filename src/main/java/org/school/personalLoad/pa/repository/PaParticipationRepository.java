package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.PaParticipation;
import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaParticipationRepository extends JpaRepository<PaParticipation, Long> {
    List<PaParticipation> findAllByAcademicYear(String academicYear);
    Optional<PaParticipation> findFirstByAcademicYearAndSubjectCatalogIdAndScopeTypeAndSchoolClassIdAndLevel(
            String academicYear, Long subjectCatalogId, PaScopeType scopeType, Long schoolClassId, PaLevel level
    );
    Optional<PaParticipation> findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevel(
            String academicYear, String subjectName, PaScopeType scopeType, String scopeValue, PaLevel level
    );
}
