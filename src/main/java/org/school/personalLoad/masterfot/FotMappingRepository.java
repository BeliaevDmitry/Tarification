package org.school.personalLoad.masterfot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FotMappingRepository extends JpaRepository<FotMapping, String> {
    List<FotMapping> findAllByAcademicYear(String academicYear);
}
