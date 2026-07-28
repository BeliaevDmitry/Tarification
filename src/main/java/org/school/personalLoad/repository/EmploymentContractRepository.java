package org.school.personalLoad.repository;
import org.school.personalLoad.model.EmploymentContract;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {
    List<EmploymentContract> findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(Long teacherId);
    List<EmploymentContract> findAllByActiveTrueOrderByTeacherIdAsc();
    List<EmploymentContract> findAllByActiveTrueAndLoadHoursMayBeIncludedInRateTrueOrderByTeacherIdAsc();
    boolean existsByLoadInRateRuleId(Long loadInRateRuleId);
}
