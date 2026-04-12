package org.school.personalLoad.repository.contingent;

import org.school.personalLoad.model.contingent.ContingentSnapshot;
import org.school.personalLoad.model.contingent.ContingentStudentEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContingentStudentEntryRepository extends JpaRepository<ContingentStudentEntry, Long> {
    List<ContingentStudentEntry> findAllBySnapshot(ContingentSnapshot snapshot);
    void deleteAllBySnapshot(ContingentSnapshot snapshot);
}

