package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.OgeGiaVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OgeGiaVersionRepository extends JpaRepository<OgeGiaVersion, Long> {
    List<OgeGiaVersion> findTop2ByAcademicYearOrderByUploadedAtDescIdDesc(String academicYear);

    List<OgeGiaVersion> findAllByAcademicYearOrderByUploadedAtDescIdDesc(String academicYear);
}
