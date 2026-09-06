package org.school.personalLoad.repository;

import org.school.personalLoad.model.ExitOrderApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExitOrderApprovalRepository extends JpaRepository<ExitOrderApproval, Long> {
    List<ExitOrderApproval> findAllByOrder_Id(Long orderId);
    List<ExitOrderApproval> findAllByOrder_IdIn(Collection<Long> orderIds);
    void deleteAllByOrder_Id(Long orderId);
}
