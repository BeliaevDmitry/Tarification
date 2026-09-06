package org.school.personalLoad.repository;

import org.school.personalLoad.model.AcademicLoadOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicLoadOrderRepository extends JpaRepository<AcademicLoadOrder, Long> {
    List<AcademicLoadOrder> findAllByAcademicYearOrderByOrderDateDescCreatedAtDesc(String academicYear);
}
