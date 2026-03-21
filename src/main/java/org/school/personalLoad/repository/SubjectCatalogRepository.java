package org.school.personalLoad.repository;

import org.school.personalLoad.model.SubjectCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubjectCatalogRepository extends JpaRepository<SubjectCatalogEntry, Long> {
    Optional<SubjectCatalogEntry> findBySubjectNameIgnoreCase(String subjectName);
}
