package org.school.personalLoad.repository;

import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectCatalogRepository extends JpaRepository<SubjectCatalogEntry, Long> {
    Optional<SubjectCatalogEntry> findBySubjectNameAndSubjectType(String subjectName, SubjectType subjectType);
}
