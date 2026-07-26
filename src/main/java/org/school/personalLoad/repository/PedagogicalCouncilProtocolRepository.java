package org.school.personalLoad.repository;

import org.school.personalLoad.model.PedagogicalCouncilProtocol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedagogicalCouncilProtocolRepository extends JpaRepository<PedagogicalCouncilProtocol, Long> {

    List<PedagogicalCouncilProtocol> findAllByAcademicYearOrderByMeetingDateDescCreatedAtDesc(String academicYear);
}
