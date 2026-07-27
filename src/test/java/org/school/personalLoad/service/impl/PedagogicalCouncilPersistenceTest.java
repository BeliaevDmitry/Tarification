package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.PedagogicalCouncilDtos;
import org.school.personalLoad.model.AcademicYearConfig;
import org.school.personalLoad.model.PedagogicalCouncilProtocol;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.PedagogicalCouncilProtocolRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(PedagogicalCouncilServiceImpl.class)
class PedagogicalCouncilPersistenceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.school")
    @EnableJpaRepositories("org.school.personalLoad.repository")
    static class JpaTestConfiguration {
    }

    @Autowired
    private PedagogicalCouncilServiceImpl service;
    @Autowired
    private PedagogicalCouncilProtocolRepository protocolRepository;
    @Autowired
    private TeacherDirectoryRepository teacherRepository;
    @Autowired
    private EntityManager entityManager;
    @MockBean
    private AcademicYearService academicYearService;

    @Test
    void createWithSpeakerPersistsOnlyFullyInitializedAgendaItem() {
        AcademicYearConfig year = new AcademicYearConfig();
        year.setCode("2025/2026");
        when(academicYearService.findAll()).thenReturn(List.of(year));

        TeacherDirectoryEntry speaker = new TeacherDirectoryEntry();
        speaker.setFioTeacher("Архангельская Татьяна Михайловна");
        speaker.setInitials("Архангельская Т.М.");
        speaker = teacherRepository.saveAndFlush(speaker);

        SessionUser user = new SessionUser();
        user.setUsername("secretary");
        user.setFullName("Секретарь");

        PedagogicalCouncilDtos.ProtocolDetails result = service.create(
                new PedagogicalCouncilDtos.CreateProtocolRequest(
                        "2025/2026",
                        "1",
                        LocalDate.of(2026, 3, 31),
                        null,
                        56,
                        "Директор",
                        "Жданова И.Д.",
                        "Учитель",
                        "Бочарова А.С.",
                        List.of(new PedagogicalCouncilDtos.ItemRequest(
                                null,
                                "О допуске учащихся к ГИА в формате ОГЭ",
                                10,
                                speaker.getId(),
                                "доложила о готовности учащихся.",
                                "Допустить учащихся согласно приложению № 1.",
                                54,
                                0,
                                0
                        ))
                ),
                user
        );

        entityManager.flush();
        entityManager.clear();

        PedagogicalCouncilProtocol saved = protocolRepository.findById(result.id()).orElseThrow();
        assertEquals(1, saved.getItems().size());
        assertNotNull(saved.getItems().get(0).getDecisionText());
        assertEquals("Допустить учащихся согласно приложению № 1.", saved.getItems().get(0).getDecisionText());
        assertEquals(10, saved.getItems().get(0).getAgendaDurationMinutes());
        assertEquals(54, saved.getItems().get(0).getVotesFor());
    }

    @Test
    void releasedProtocolCanBeSavedAsDraftAndImmediatelyReissued() {
        AcademicYearConfig year = new AcademicYearConfig();
        year.setCode("2025/2026");
        when(academicYearService.findAll()).thenReturn(List.of(year));

        SessionUser user = new SessionUser();
        user.setUsername("secretary");
        user.setFullName("Секретарь");

        PedagogicalCouncilDtos.ProtocolDetails created = service.create(
                new PedagogicalCouncilDtos.CreateProtocolRequest(
                        "2025/2026",
                        "2",
                        LocalDate.of(2026, 4, 15),
                        null,
                        25,
                        "Директор",
                        "Иванова И.И.",
                        "Методист",
                        "Петрова П.П.",
                        List.of(new PedagogicalCouncilDtos.ItemRequest(
                                null,
                                "О результатах обучения",
                                10,
                                null,
                                null,
                                "Принять информацию к сведению.",
                                25,
                                0,
                                0
                        ))
                ),
                user
        );
        service.release(created.id(), user);
        PedagogicalCouncilDtos.ProtocolDetails opened = service.get(created.id());
        PedagogicalCouncilDtos.ItemView item = opened.items().get(0);

        PedagogicalCouncilDtos.ProtocolDetails draft = service.update(
                opened.id(),
                new PedagogicalCouncilDtos.UpdateProtocolRequest(
                        opened.protocolNumber(),
                        opened.meetingDate(),
                        opened.agendaTime(),
                        opened.attendeeCount(),
                        opened.chairPosition(),
                        opened.chairFio(),
                        opened.secretaryPosition(),
                        opened.secretaryFio(),
                        PedagogicalCouncilProtocol.Status.DRAFT,
                        opened.version(),
                        List.of(new PedagogicalCouncilDtos.ItemRequest(
                                item.id(),
                                item.agendaTitle(),
                                item.agendaDurationMinutes(),
                                item.speakerTeacherId(),
                                item.speakerPosition(),
                                item.speechContent(),
                                "Обновлённое решение.",
                                item.votesFor(),
                                item.votesAgainst(),
                                item.votesAbstained(),
                                item.fingerprint()
                        )),
                        opened.headerFingerprint(),
                        List.of()
                ),
                user
        );
        PedagogicalCouncilDtos.ProtocolDetails reissued = service.release(draft.id(), user);

        assertEquals(PedagogicalCouncilProtocol.Status.DRAFT, draft.status());
        assertEquals(PedagogicalCouncilProtocol.Status.REGISTERED, reissued.status());
        assertEquals("Обновлённое решение.", reissued.items().get(0).decisionText());
        assertEquals("Секретарь", reissued.registeredBy());
    }
}
