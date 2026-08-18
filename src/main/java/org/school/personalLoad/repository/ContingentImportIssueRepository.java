package org.school.personalLoad.repository;

import org.school.personalLoad.model.ContingentImportIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContingentImportIssueRepository extends JpaRepository<ContingentImportIssue, Long> {
    List<ContingentImportIssue> findAllBySnapshotIdOrderBySourceRowNumberAscIdAsc(Long snapshotId);

    void deleteBySnapshotId(Long snapshotId);
}
