package org.school.personalLoad.repository;

import org.school.personalLoad.model.ExitOrderGeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExitOrderGeneratedDocumentRepository extends JpaRepository<ExitOrderGeneratedDocument, Long> {
    Optional<ExitOrderGeneratedDocument> findByOrder_Id(Long orderId);
    List<ExitOrderGeneratedDocument> findAllByOrder_IdIn(Collection<Long> orderIds);
    void deleteByOrder_Id(Long orderId);
}
