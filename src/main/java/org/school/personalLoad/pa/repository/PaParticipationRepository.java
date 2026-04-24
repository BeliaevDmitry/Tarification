package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.PaParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaParticipationRepository extends JpaRepository<PaParticipation, Long> {
    List<PaParticipation> findAllByAcademicYear(String academicYear);
}
