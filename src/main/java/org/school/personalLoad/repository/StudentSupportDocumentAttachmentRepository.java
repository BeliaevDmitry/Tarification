package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentSupportDocumentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentSupportDocumentAttachmentRepository
        extends JpaRepository<StudentSupportDocumentAttachment, Long> {
    List<StudentSupportDocumentAttachment> findAllByDocument_IdOrderByUploadedAtAsc(Long documentId);
    void deleteAllByDocument_Id(Long documentId);
}
