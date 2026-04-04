package org.school.personalLoad.repository;

import org.school.personalLoad.model.ManualLoadEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualLoadEntryRepository extends JpaRepository<ManualLoadEntry, Long> {
    boolean existsByFioTeacherIgnoreCase(String fioTeacher);

    java.util.List<ManualLoadEntry> findByFioTeacherIgnoreCase(String fioTeacher);

    @Modifying
    @Query("delete from ManualLoadEntry m where lower(m.numberSchoolBuilding) in :codes")
    void deleteByBuildingCodes(@Param("codes") java.util.Collection<String> codes);
}
