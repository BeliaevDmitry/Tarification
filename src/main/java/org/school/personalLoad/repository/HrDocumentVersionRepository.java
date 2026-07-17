package org.school.personalLoad.repository;
import org.school.personalLoad.model.HrDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HrDocumentVersionRepository extends JpaRepository<HrDocumentVersion, Long> {
    List<HrDocumentVersion> findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc(String type, Long id);
}
