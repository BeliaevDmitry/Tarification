package org.school.personalLoad.masterfot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FotBatchRepository extends JpaRepository<FotBatch, Long> {
    List<FotBatch> findAllByAcademicYearOrderByIdDesc(String academicYear);
}
