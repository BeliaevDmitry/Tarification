package org.school.personalLoad.masterfot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FotIssueRepository extends JpaRepository<FotIssue, String> {
    List<FotIssue> findAllByAcademicYear(String academicYear);
}
