package org.school.personalLoad.repository;

import org.school.personalLoad.model.CalendarAudienceMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarAudienceMembershipRepository extends JpaRepository<CalendarAudienceMembership, Long> {

    @Override
    @EntityGraph(attributePaths = "teacher")
    List<CalendarAudienceMembership> findAll();
}
