package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentSupportDocumentCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentSupportDocumentCorrectionRepository
        extends JpaRepository<StudentSupportDocumentCorrection, Long> {
    List<StudentSupportDocumentCorrection> findAllByDocument_IdOrderBySpecialist_NameAsc(Long documentId);

    void deleteAllByDocument_Id(Long documentId);
}
