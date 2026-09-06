package org.school.personalLoad.repository;

import org.school.personalLoad.model.ExitOrderScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExitOrderScanRepository extends JpaRepository<ExitOrderScan, Long> {
    Optional<ExitOrderScan> findByOrder_Id(Long orderId);
    List<ExitOrderScan> findAllByOrder_IdIn(Collection<Long> orderIds);
}
