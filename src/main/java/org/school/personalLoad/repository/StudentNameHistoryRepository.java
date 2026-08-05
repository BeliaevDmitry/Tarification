package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentNameHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface StudentNameHistoryRepository extends JpaRepository<StudentNameHistory, Long> {
    Optional<StudentNameHistory> findFirstByStudent_IdAndValidToIsNullOrderByValidFromDesc(Long studentId);

    List<StudentNameHistory> findAllByStudent_IdOrderByValidFromAsc(Long studentId);
}
