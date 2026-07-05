package org.school.personalLoad.repository;

import org.school.personalLoad.model.MckoSubjectMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MckoSubjectMappingRepository extends JpaRepository<MckoSubjectMapping, Long> {
    List<MckoSubjectMapping> findAllByMckoSubjectIgnoreCase(String mckoSubject);
    boolean existsByMckoSubjectIgnoreCaseAndSubjectId(String mckoSubject, Long subjectId);
}
