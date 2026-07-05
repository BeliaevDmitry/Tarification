package org.school.personalLoad.repository;

import org.school.personalLoad.model.SalaryGroupCoefficientSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalaryGroupCoefficientSubjectRepository extends JpaRepository<SalaryGroupCoefficientSubject, Long> {
    Optional<SalaryGroupCoefficientSubject> findBySubjectId(Long subjectId);
    Optional<SalaryGroupCoefficientSubject> findBySubjectNameIgnoreCase(String subjectName);
}
