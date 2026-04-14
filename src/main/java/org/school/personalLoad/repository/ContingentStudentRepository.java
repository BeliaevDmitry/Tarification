package org.school.personalLoad.repository;

import org.school.personalLoad.model.ContingentStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContingentStudentRepository extends JpaRepository<ContingentStudent, Long> {
    List<ContingentStudent> findAllBySnapshotId(Long snapshotId);

    void deleteBySnapshotId(Long snapshotId);
}
