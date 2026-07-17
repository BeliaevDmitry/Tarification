package org.school.personalLoad.repository;
import org.school.personalLoad.model.AdditionalAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AdditionalAgreementRepository extends JpaRepository<AdditionalAgreement, Long> {
    List<AdditionalAgreement> findAllByAcademicYearOrderByCreatedAtDesc(String academicYear);
    List<AdditionalAgreement> findAllByContractIdOrderByCreatedAtDesc(Long contractId);
    List<AdditionalAgreement> findAllByServiceMemoId(Long serviceMemoId);
    List<AdditionalAgreement> findAllByLoadServiceMemoId(Long loadServiceMemoId);
    long countByContractIdAndAcademicYear(Long contractId, String academicYear);
}
