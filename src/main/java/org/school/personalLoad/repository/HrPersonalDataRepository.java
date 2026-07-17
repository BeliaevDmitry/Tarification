package org.school.personalLoad.repository;
import org.school.personalLoad.model.HrPersonalData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface HrPersonalDataRepository extends JpaRepository<HrPersonalData, Long> {
    Optional<HrPersonalData> findByTeacherId(Long teacherId);
}
