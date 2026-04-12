package org.school.personalLoad.repository.contingent;

import org.school.personalLoad.model.contingent.ContingentSnapshot;
import org.school.personalLoad.model.contingent.ContingentWarning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContingentWarningRepository extends JpaRepository<ContingentWarning, Long> {
    List<ContingentWarning> findAllBySnapshot(ContingentSnapshot snapshot);
    void deleteAllBySnapshot(ContingentSnapshot snapshot);
}

