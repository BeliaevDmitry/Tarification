package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.PaSpecificationTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaSpecificationTaskRepository extends JpaRepository<PaSpecificationTask, Long> {
    List<PaSpecificationTask> findAllBySpecificationIdOrderByTaskNoAsc(Long specificationId);
    void deleteAllBySpecificationId(Long specificationId);
}
