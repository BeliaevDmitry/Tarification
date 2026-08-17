package org.school.personalLoad.repository;

import org.school.personalLoad.model.ProbeOrderScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProbeOrderScanRepository extends JpaRepository<ProbeOrderScan, Long> {
    Optional<ProbeOrderScan> findByOrder_Id(Long orderId);
    List<ProbeOrderScan> findAllByOrder_IdIn(Collection<Long> orderIds);
}
