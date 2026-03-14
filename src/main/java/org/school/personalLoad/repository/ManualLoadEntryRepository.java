package org.school.personalLoad.repository;

import org.school.personalLoad.model.ManualLoadEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualLoadEntryRepository extends JpaRepository<ManualLoadEntry, Long> {
    boolean existsByFioTeacherIgnoreCase(String fioTeacher);
}
