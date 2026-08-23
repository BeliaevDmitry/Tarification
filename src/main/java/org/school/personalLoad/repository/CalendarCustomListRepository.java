package org.school.personalLoad.repository;

import org.school.personalLoad.model.CalendarCustomList;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalendarCustomListRepository extends JpaRepository<CalendarCustomList, Long> {

    @EntityGraph(attributePaths = {"owner", "members"})
    List<CalendarCustomList> findAllByOwner_IdOrderByNameAsc(Long ownerUserId);

    @EntityGraph(attributePaths = {"owner", "members"})
    List<CalendarCustomList> findAllByOwner_IdAndIdIn(Long ownerUserId, Collection<Long> ids);

    @EntityGraph(attributePaths = {"owner", "members"})
    Optional<CalendarCustomList> findByIdAndOwner_Id(Long id, Long ownerUserId);

    boolean existsByOwner_IdAndNameIgnoreCase(Long ownerUserId, String name);

    boolean existsByOwner_IdAndNameIgnoreCaseAndIdNot(Long ownerUserId, String name, Long id);
}
