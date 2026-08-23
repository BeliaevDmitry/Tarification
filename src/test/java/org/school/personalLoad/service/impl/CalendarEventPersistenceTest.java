package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.CalendarEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class CalendarEventPersistenceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.school")
    @EnableJpaRepositories("org.school.personalLoad.repository")
    static class JpaTestConfiguration {
    }

    @Autowired
    private CalendarEventRepository eventRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void eventWithAllAudienceSelectionsIsFetchedOnlyOnce() {
        AppUser owner = new AppUser();
        owner.setUsername("calendar-owner");
        owner.setFullName("Владелец календаря");
        owner.setPasswordHash("test");
        owner.setRole(UserRole.METHODIST);
        entityManager.persist(owner);

        TeacherDirectoryEntry first = teacher("Первый участник");
        TeacherDirectoryEntry second = teacher("Второй участник");
        entityManager.persist(first);
        entityManager.persist(second);

        BuildingGroup group = new BuildingGroup();
        group.setCode("СП2");
        group.setName("Корпус 2");
        entityManager.persist(group);
        SchoolBuilding building = new SchoolBuilding();
        building.setCode("СП2");
        building.setName("Корпус 2");
        building.setAddress("Улица Школьная, 2");
        building.setManagerFio("Руководитель");
        building.setBuildingGroup(group);
        entityManager.persist(building);

        CalendarEvent event = new CalendarEvent();
        event.setOwner(owner);
        event.setTitle("Общее совещание");
        event.setStartsAt(LocalDateTime.of(2026, 8, 25, 10, 0));
        event.setEndsAt(LocalDateTime.of(2026, 8, 25, 11, 0));
        event.setDurationMinutes(60);
        event.getParticipants().addAll(List.of(first, second));
        event.getBuildings().add(building);
        event.getSelectedPersonIds().add(first.getId());
        event.getSelectedGroups().add(CalendarAudienceGroup.ADMINISTRATION);
        event.getSelectedCustomListIds().add(50L);
        eventRepository.saveAndFlush(event);
        entityManager.clear();

        List<CalendarEvent> result = eventRepository
                .findDistinctByStartsAtLessThanAndEndsAtGreaterThanEqualOrderByStartsAtAsc(
                        LocalDateTime.of(2026, 8, 26, 0, 0),
                        LocalDateTime.of(2026, 8, 25, 0, 0));

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getParticipants().size());
        assertEquals(1, result.get(0).getBuildings().size());
        assertEquals(List.of(CalendarAudienceGroup.ADMINISTRATION), result.get(0).getSelectedGroups().stream().toList());
    }

    private TeacherDirectoryEntry teacher(String name) {
        TeacherDirectoryEntry result = new TeacherDirectoryEntry();
        result.setFioTeacher(name);
        return result;
    }
}
