package org.school.personalLoad.repository;

import org.school.personalLoad.model.ProbeOrderApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProbeOrderApprovalRepository extends JpaRepository<ProbeOrderApproval, Long> {
    List<ProbeOrderApproval> findAllByOrder_Id(Long orderId);

    List<ProbeOrderApproval> findAllByOrder_IdIn(Collection<Long> orderIds);

    void deleteAllByOrder_Id(Long orderId);
}
