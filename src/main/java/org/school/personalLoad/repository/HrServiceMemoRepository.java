package org.school.personalLoad.repository;
import org.school.personalLoad.model.HrServiceMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HrServiceMemoRepository extends JpaRepository<HrServiceMemo, Long> {
    List<HrServiceMemo> findAllByAcademicYearOrderByCreatedAtDesc(String academicYear);
}
