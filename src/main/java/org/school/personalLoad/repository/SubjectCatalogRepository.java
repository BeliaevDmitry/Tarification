package org.school.personalLoad.repository;

import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubjectCatalogRepository extends JpaRepository<SubjectCatalogEntry, Long> {
    Optional<SubjectCatalogEntry> findBySubjectNameAndSubjectType(String subjectName, SubjectType subjectType);

    boolean existsBySubjectAreaNameIgnoreCase(String subjectAreaName);

    @Modifying
    @Query("update SubjectCatalogEntry s set s.subjectAreaName = :newName where lower(s.subjectAreaName) = lower(:oldName)")
    int renameSubjectAreaEverywhere(@Param("oldName") String oldName, @Param("newName") String newName);
}
