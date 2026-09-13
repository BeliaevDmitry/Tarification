package org.school.personalLoad.repository;

import org.school.personalLoad.model.TeacherTimeOffEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherTimeOffEntryRepository extends JpaRepository<TeacherTimeOffEntry, Long> {
    List<TeacherTimeOffEntry> findAllByOrderByEventDateDescCreatedAtDesc();

    List<TeacherTimeOffEntry> findAllByTeacherId(Long teacherId);
}
