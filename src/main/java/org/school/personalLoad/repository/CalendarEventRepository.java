package org.school.personalLoad.repository;

import org.school.personalLoad.model.CalendarEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    @EntityGraph(attributePaths = {"owner", "participants", "buildings", "selectedPersonIds",
            "selectedGroups", "selectedCustomListIds"})
    List<CalendarEvent> findDistinctByStartsAtLessThanAndEndsAtGreaterThanEqualOrderByStartsAtAsc(
            LocalDateTime toExclusive, LocalDateTime fromInclusive);

    @Override
    @EntityGraph(attributePaths = {"owner", "participants", "buildings", "selectedPersonIds",
            "selectedGroups", "selectedCustomListIds"})
    Optional<CalendarEvent> findById(Long id);
}
