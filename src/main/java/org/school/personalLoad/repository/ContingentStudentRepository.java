package org.school.personalLoad.repository;

import org.school.personalLoad.model.ContingentStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContingentStudentRepository extends JpaRepository<ContingentStudent, Long> {
    List<ContingentStudent> findAllBySnapshotId(Long snapshotId);

    @Query("select s.className from ContingentStudent s where s.snapshotId = :snapshotId")
    List<String> findClassNamesBySnapshotId(@Param("snapshotId") Long snapshotId);

    void deleteBySnapshotId(Long snapshotId);
}
