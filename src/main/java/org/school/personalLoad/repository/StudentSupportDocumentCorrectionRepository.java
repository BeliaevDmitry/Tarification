package org.school.personalLoad.repository;

import org.school.personalLoad.model.StudentSupportDocumentCorrection;
import org.school.personalLoad.model.StudentSupportDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentSupportDocumentCorrectionRepository
        extends JpaRepository<StudentSupportDocumentCorrection, Long> {
    List<StudentSupportDocumentCorrection> findAllByDocument_IdOrderBySpecialist_NameAsc(Long documentId);

    List<StudentSupportDocumentCorrection> findAllByDocument_AcademicYearOrderBySpecialist_NameAsc(String academicYear);

    @Query("select case when count(c) > 0 then true else false end from StudentSupportDocumentCorrection c " +
            "where c.document.student.id = :studentId and c.document.academicYear = :academicYear " +
            "and c.specialist.id = :specialistId and c.document.documentType in :types")
    boolean existsStudentNeed(@Param("academicYear") String academicYear,
                              @Param("studentId") Long studentId,
                              @Param("specialistId") Long specialistId,
                              @Param("types") List<StudentSupportDocumentType> types);

    void deleteAllByDocument_Id(Long documentId);
}
