package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentClassTransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentClassTransferRequestRepository extends JpaRepository<StudentClassTransferRequest, Long> {
    List<StudentClassTransferRequest> findAllByAcademicYearOrderByUpdatedAtDescIdDesc(String academicYear);

    Optional<StudentClassTransferRequest> findByIdAndAcademicYear(Long id, String academicYear);
}
