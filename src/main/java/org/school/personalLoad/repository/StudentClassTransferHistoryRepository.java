package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentClassTransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StudentClassTransferHistoryRepository extends JpaRepository<StudentClassTransferHistory, Long> {
    List<StudentClassTransferHistory> findAllByTransferRequest_IdInOrderByChangedAtDescIdDesc(Collection<Long> requestIds);
}
