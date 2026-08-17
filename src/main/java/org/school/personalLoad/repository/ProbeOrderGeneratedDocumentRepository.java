package org.school.personalLoad.repository;

import org.school.personalLoad.model.ProbeOrderGeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProbeOrderGeneratedDocumentRepository extends JpaRepository<ProbeOrderGeneratedDocument, Long> {
    Optional<ProbeOrderGeneratedDocument> findByOrder_Id(Long orderId);
    List<ProbeOrderGeneratedDocument> findAllByOrder_IdIn(Collection<Long> orderIds);
    void deleteByOrder_Id(Long orderId);
}
