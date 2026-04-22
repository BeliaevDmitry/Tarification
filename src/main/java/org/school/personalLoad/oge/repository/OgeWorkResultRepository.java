package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.OgeWorkResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OgeWorkResultRepository extends JpaRepository<OgeWorkResult, Long> {
    Optional<OgeWorkResult> findByFullNameAndSubjectName(String fullName, String subjectName);

    List<OgeWorkResult> findAllByOrderByClassNameAscFullNameAscSubjectNameAsc();
}
