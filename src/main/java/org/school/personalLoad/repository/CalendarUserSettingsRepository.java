package org.school.personalLoad.repository;

import org.school.personalLoad.model.CalendarUserSettings;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalendarUserSettingsRepository extends JpaRepository<CalendarUserSettings, Long> {

    @EntityGraph(attributePaths = {"user", "sharedWith"})
    Optional<CalendarUserSettings> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"user", "sharedWith"})
    List<CalendarUserSettings> findAllByUser_IdIn(Collection<Long> userIds);
}
