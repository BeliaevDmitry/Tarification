package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentSupportDocument;
import org.school.personalLoad.model.StudentSupportDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentSupportDocumentRepository extends JpaRepository<StudentSupportDocument, Long> {
    List<StudentSupportDocument> findAllByAcademicYearOrderByValidToAscStudent_CurrentFullNameAsc(String academicYear);
    List<StudentSupportDocument> findAllByStudent_IdAndAcademicYearOrderByIssueDateDesc(Long studentId, String academicYear);

    List<StudentSupportDocument> findAllByDocumentType(StudentSupportDocumentType documentType);
}
