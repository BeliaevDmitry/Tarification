package org.school.personalLoad.repository;

import org.school.personalLoad.model.EducationStage;
import org.school.personalLoad.model.SubjectLevelCoefficientEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectLevelCoefficientRepository extends JpaRepository<SubjectLevelCoefficientEntry, Long> {
    Optional<SubjectLevelCoefficientEntry> findBySubjectNameIgnoreCaseAndEducationStage(String subjectName, EducationStage educationStage);
}
