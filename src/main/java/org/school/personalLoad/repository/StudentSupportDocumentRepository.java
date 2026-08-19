package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentSupportDocument;
import org.school.personalLoad.model.StudentSupportDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentSupportDocumentRepository extends JpaRepository<StudentSupportDocument, Long> {
    List<StudentSupportDocument> findAllByAcademicYearOrderByValidToAscStudent_CurrentFullNameAsc(String academicYear);
    List<StudentSupportDocument> findAllByStudent_IdAndAcademicYearOrderByIssueDateDesc(Long studentId, String academicYear);

    Optional<StudentSupportDocument> findFirstByStudent_IdAndAcademicYearAndDocumentType(
            Long studentId, String academicYear, StudentSupportDocumentType documentType
    );

    boolean existsByStudent_IdAndAcademicYearAndDocumentTypeAndIdNot(
            Long studentId, String academicYear, StudentSupportDocumentType documentType, Long id
    );

    void deleteAllByStudent_IdAndAcademicYear(Long studentId, String academicYear);

    List<StudentSupportDocument> findAllByDocumentType(StudentSupportDocumentType documentType);
}
