package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.AppUserTabPermission;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.dto.PedagogicalCouncilDtos;
import org.school.personalLoad.model.AcademicYearConfig;
import org.school.personalLoad.model.PedagogicalCouncilAttachment;
import org.school.personalLoad.model.PedagogicalCouncilItem;
import org.school.personalLoad.model.PedagogicalCouncilProtocol;
import org.school.personalLoad.repository.EmploymentContractRepository;
import org.school.personalLoad.repository.PedagogicalCouncilAttachmentRepository;
import org.school.personalLoad.repository.PedagogicalCouncilProtocolRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.repository.auth.AppUserTabPermissionRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PedagogicalCouncilServiceImplTest {

    private PedagogicalCouncilProtocolRepository protocols;
    private PedagogicalCouncilAttachmentRepository attachments;
    private AcademicYearService academicYears;
    private TeacherDirectoryRepository teachers;
    private EmploymentContractRepository contracts;
    private AppUserRepository users;
    private AppUserTabPermissionRepository permissions;
    private PedagogicalCouncilServiceImpl service;

    @BeforeEach
    void setUp() {
        protocols = mock(PedagogicalCouncilProtocolRepository.class);
        attachments = mock(PedagogicalCouncilAttachmentRepository.class);
        academicYears = mock(AcademicYearService.class);
        teachers = mock(TeacherDirectoryRepository.class);
        contracts = mock(EmploymentContractRepository.class);
        users = mock(AppUserRepository.class);
        permissions = mock(AppUserTabPermissionRepository.class);
        service = new PedagogicalCouncilServiceImpl(
                protocols,
                attachments,
                teachers,
                contracts,
                users,
                permissions,
                academicYears
        );
    }

    @Test
    void archiveUploadStoresOriginalWordAsRegisteredProtocol() throws Exception {
        AcademicYearConfig year = new AcademicYearConfig();
        year.setCode("2023/2024");
        when(academicYears.findAll()).thenReturn(List.of(year));
        when(protocols.save(any())).thenAnswer(invocation -> {
            PedagogicalCouncilProtocol protocol = invocation.getArgument(0);
            protocol.setId(42L);
            return protocol;
        });
        byte[] word = docxWithText("Старый протокол");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Протокол № 3.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                word
        );

        PedagogicalCouncilDtos.ProtocolDetails result = service.uploadArchive(
                "2023/2024",
                "3",
                LocalDate.of(2024, 3, 15),
                file,
                user()
        );

        assertEquals(42L, result.id());
        assertEquals(PedagogicalCouncilProtocol.SourceType.ARCHIVE_WORD, result.sourceType());
        assertEquals(PedagogicalCouncilProtocol.Status.REGISTERED, result.status());
        assertEquals("Протокол № 3.docx", result.archiveFilename());
        verify(protocols).save(argThat(protocol ->
                java.util.Arrays.equals(word, protocol.getArchiveDocument())
                        && "2023/2024".equals(protocol.getAcademicYear())
        ));
    }

    @Test
    void releasedProtocolCanBeEditedAndReissuedWithoutCorrectedStatus() {
        PedagogicalCouncilProtocol protocol = baseProtocol();
        protocol.setStatus(PedagogicalCouncilProtocol.Status.REGISTERED);
        protocol.setRegisteredAt(LocalDateTime.of(2026, 3, 31, 12, 0));
        protocol.setRegisteredBy("Старый пользователь");
        protocol.setChairPositionSnapshot("Директор");
        protocol.setChairFioSnapshot("Иванова И.И.");
        protocol.setSecretaryPositionSnapshot("Методист");
        protocol.setSecretaryFioSnapshot("Петрова П.П.");
        protocol.setAttendeeCount(25);
        protocol.getItems().add(item(protocol, 10L, 1));
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));
        when(protocols.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PedagogicalCouncilDtos.ProtocolDetails result = service.update(
                1L,
                new PedagogicalCouncilDtos.UpdateProtocolRequest(
                        "8",
                        LocalDate.of(2026, 3, 31),
                        null,
                        25,
                        "Директор",
                        "Иванова И.И.",
                        "Методист",
                        "Петрова П.П.",
                        PedagogicalCouncilProtocol.Status.REGISTERED,
                        0L,
                        List.of(new PedagogicalCouncilDtos.ItemRequest(
                                10L,
                                "Вопрос с дополнением",
                                10,
                                null,
                                null,
                                null,
                                "Обновлённое решение",
                                25,
                                0,
                                0
                        ))
                ),
                user()
        );

        assertEquals(PedagogicalCouncilProtocol.Status.REGISTERED, result.status());
        assertEquals("Секретарь", result.registeredBy());
        assertTrue(result.registeredAt().isAfter(LocalDateTime.of(2026, 3, 31, 12, 0)));
    }

    @Test
    void constructorStoresManualChairAndSecretaryAndDefaultsAgendaDurationToTenMinutes() {
        AcademicYearConfig year = new AcademicYearConfig();
        year.setCode("2025/2026");
        when(academicYears.findAll()).thenReturn(List.of(year));
        when(protocols.save(any())).thenAnswer(invocation -> {
            PedagogicalCouncilProtocol protocol = invocation.getArgument(0);
            protocol.setId(43L);
            return protocol;
        });

        PedagogicalCouncilDtos.ProtocolDetails result = service.create(
                new PedagogicalCouncilDtos.CreateProtocolRequest(
                        "2025/2026",
                        "9",
                        LocalDate.of(2026, 4, 15),
                        null,
                        25,
                        "Директор",
                        "Иванова Ирина Ивановна",
                        "Методист",
                        "Петрова П.П.",
                        List.of(new PedagogicalCouncilDtos.ItemRequest(
                                null,
                                "О результатах обучения",
                                null,
                                null,
                                "представила результаты.",
                                "Принять информацию к сведению.",
                                25,
                                0,
                                0
                        ))
                ),
                user()
        );

        assertNull(result.chairTeacherId());
        assertEquals("Директор", result.chairPosition());
        assertEquals("Иванова Ирина Ивановна", result.chairFio());
        assertNull(result.secretaryTeacherId());
        assertEquals("Методист", result.secretaryPosition());
        assertEquals("Петрова П.П.", result.secretaryFio());
        assertEquals(10, result.items().get(0).agendaDurationMinutes());
        verifyNoInteractions(teachers);
    }

    @Test
    void constructorRejectsMoreVotesThanAttendees() {
        AcademicYearConfig year = new AcademicYearConfig();
        year.setCode("2025/2026");
        when(academicYears.findAll()).thenReturn(List.of(year));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.create(
                        new PedagogicalCouncilDtos.CreateProtocolRequest(
                                "2025/2026",
                                "10",
                                LocalDate.of(2026, 4, 15),
                                null,
                                56,
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
                                        57,
                                        0,
                                        0
                                ))
                        ),
                        user()
                )
        );

        assertTrue(error.getMessage().contains("57 голосов при 56 присутствующих"));
        verify(protocols, never()).save(any());
    }

    @Test
    void attachmentNumberIsAssignedAcrossWholeProtocol() throws Exception {
        PedagogicalCouncilProtocol protocol = baseProtocol();
        PedagogicalCouncilItem first = item(protocol, 10L, 1);
        PedagogicalCouncilItem second = item(protocol, 20L, 2);
        PedagogicalCouncilAttachment existing = new PedagogicalCouncilAttachment();
        existing.setId(100L);
        existing.setItem(first);
        existing.setAttachmentNumber(1);
        existing.setOriginalFilename("first.docx");
        existing.setContent(docxWithText("Первое"));
        existing.setUploadedBy("Секретарь");
        first.getAttachments().add(existing);
        protocol.getItems().addAll(List.of(first, second));
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));
        when(attachments.save(any())).thenAnswer(invocation -> {
            PedagogicalCouncilAttachment attachment = invocation.getArgument(0);
            attachment.setId(101L);
            return attachment;
        });

        PedagogicalCouncilDtos.AttachmentView result = service.addAttachment(
                1L,
                20L,
                new MockMultipartFile("file", "second.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxWithText("Второе")),
                user()
        );

        assertEquals(2, result.attachmentNumber());
        assertEquals("second.docx", result.originalFilename());
        assertEquals(1, second.getAttachments().size());
    }

    @Test
    void generatedProtocolPlacesAttachmentReferenceBeforeVotingAndMergesWordAttachment() throws Exception {
        PedagogicalCouncilProtocol protocol = baseProtocol();
        protocol.setChairFioSnapshot("Петрова И.И.");
        protocol.setChairPositionSnapshot("Директор");
        protocol.setSecretaryFioSnapshot("Беляев Д.А.");
        protocol.setSecretaryPositionSnapshot("Старший методист");
        protocol.setAttendeeCount(35);
        PedagogicalCouncilItem item = item(protocol, 10L, 1);
        item.setAgendaTitle("О допуске обучающихся к ГИА");
        item.setSpeakerPositionSnapshot("Заместитель директора");
        item.setSpeakerFioSnapshot("Власова Ю.С.");
        item.setSpeechContent("представила сведения о готовности обучающихся.");
        item.setDecisionText("Допустить обучающихся к государственной итоговой аттестации.");
        item.setVotesFor(35);
        PedagogicalCouncilAttachment attachment = new PedagogicalCouncilAttachment();
        attachment.setItem(item);
        attachment.setAttachmentNumber(1);
        attachment.setOriginalFilename("Список.docx");
        attachment.setContent(docxWithTableAndPicture("Таблица обучающихся"));
        attachment.setUploadedBy("Секретарь");
        item.getAttachments().add(attachment);
        protocol.getItems().add(item);
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));

        PedagogicalCouncilDtos.FilePayload file = service.buildFullProtocol(1L);
        writeVisualQaSample(file, "school-7-protocol.docx");
        String text;
        int pictureCount;
        long pictureWidth;
        boolean titlePage;
        String headersXml;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            text = extractor.getText();
            pictureCount = document.getAllPictures().size();
            pictureWidth = maxInlinePictureWidth(document);
            titlePage = document.getDocument().getBody().getSectPr().isSetTitlePg();
            headersXml = document.getHeaderList().stream()
                    .flatMap(header -> header.getParagraphs().stream())
                    .map(paragraph -> paragraph.getCTP().xmlText())
                    .reduce("", String::concat);
        }

        assertTrue(text.contains("ПРОТОКОЛ № 8"));
        assertTrue(text.contains("ГОСУДАРСТВЕННОЕ БЮДЖЕТНОЕ ОБЩЕОБРАЗОВАТЕЛЬНОЕ УЧРЕЖДЕНИЕ"));
        assertTrue(text.contains("ГОРОДА МОСКВЫ «ШКОЛА № 7»"));
        assertFalse(text.contains("КОМУ"));
        assertFalse(text.contains("И.Д. Жданова"));
        assertTrue(text.contains("Заместитель директора Власова Ю.С."));
        assertTrue(text.contains("О допуске обучающихся к ГИА (10 минут)"));
        assertTrue(text.contains("Таблица обучающихся"));
        assertTrue(text.indexOf("Приложение № 1 к пункту 1") < text.indexOf("Голосовали: за — 35"));
        assertEquals(2, pictureCount);
        assertTrue(pictureWidth > 500_000L);
        assertTrue(titlePage);
        assertTrue(headersXml.contains("PAGE"));
    }

    @Test
    void archiveDateMustBelongToAcademicYearStartingOnFirstOfAugust() throws Exception {
        AcademicYearConfig year = new AcademicYearConfig();
        year.setCode("2023/2024");
        when(academicYears.findAll()).thenReturn(List.of(year));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.uploadArchive(
                        "2023/2024",
                        "3",
                        LocalDate.of(2023, 7, 31),
                        new MockMultipartFile("file", "old.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxWithText("Старый")),
                        user()
                )
        );

        assertTrue(error.getMessage().contains("с 1 августа по 31 июля"));
        verify(protocols, never()).save(any());
    }

    @Test
    void extractCanContainSeveralIndependentCertifiers() throws Exception {
        PedagogicalCouncilProtocol protocol = baseProtocol();
        protocol.setSchoolCodeSnapshot("1811");
        protocol.setSchoolNameSnapshot("ГБОУ Школа № 1811");
        protocol.setChairFioSnapshot("Петрова И.И.");
        protocol.setChairPositionSnapshot("Директор");
        protocol.setSecretaryFioSnapshot("Беляев Д.А.");
        protocol.setSecretaryPositionSnapshot("Методист");
        PedagogicalCouncilItem item = item(protocol, 10L, 1);
        protocol.getItems().add(item);
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));

        AppUser first = appUser(11L, "Беляев Дмитрий Александрович");
        AppUser second = appUser(12L, "Власова Юлия Сергеевна");
        when(users.findById(1L)).thenReturn(Optional.of(appUser(1L, "Секретарь")));
        when(users.findById(11L)).thenReturn(Optional.of(first));
        when(users.findById(12L)).thenReturn(Optional.of(second));
        when(teachers.findByFioTeacherIgnoreCase(anyString())).thenReturn(Optional.empty());
        AppUserTabPermission firstPermission = permission(first);
        AppUserTabPermission secondPermission = permission(second);
        when(permissions.findAllByTabAndCanExportTrue(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS))
                .thenReturn(List.of(firstPermission, secondPermission));

        PedagogicalCouncilDtos.FilePayload file = service.buildExtract(
                1L,
                new PedagogicalCouncilDtos.ExtractRequest(
                        List.of(10L),
                        List.of(11L, 12L),
                        false,
                        null,
                        false,
                        null
                ),
                user()
        );
        writeVisualQaSample(file, "school-1811-extract.docx");
        String text;
        int pictureCount;
        String pictureExtension;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            text = extractor.getText();
            pictureCount = document.getAllPictures().size();
            pictureExtension = document.getAllPictures().get(0).suggestFileExtension();
        }

        assertTrue(text.contains("ВЫПИСКА ИЗ ПРОТОКОЛА"));
        assertFalse(text.contains("ГБОУ Школа № 1811"));
        assertTrue(text.contains("Беляев Дмитрий Александрович"));
        assertTrue(text.contains("Власова Юлия Сергеевна"));
        assertEquals(2, text.split("Верно", -1).length - 1);
        assertTrue(text.contains("М.П."));
        assertEquals(1, pictureCount);
        assertEquals("png", pictureExtension);
    }

    @Test
    void extractDefaultsToGeneratingUserAndIncludesSourceSignersAndStamp() throws Exception {
        PedagogicalCouncilProtocol protocol = baseProtocol();
        protocol.setChairFioSnapshot("Петрова И.И.");
        protocol.setChairPositionSnapshot("Директор");
        protocol.setSecretaryFioSnapshot("Беляев Д.А.");
        protocol.setSecretaryPositionSnapshot("Методист");
        protocol.getItems().add(item(protocol, 10L, 1));
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));
        when(users.findById(1L)).thenReturn(Optional.of(appUser(1L, "Секретарь С.С.")));
        when(teachers.findByFioTeacherIgnoreCase(anyString())).thenReturn(Optional.empty());

        PedagogicalCouncilDtos.FilePayload file = service.buildExtract(
                1L,
                new PedagogicalCouncilDtos.ExtractRequest(
                        List.of(10L),
                        List.of(),
                        List.of(),
                        false,
                        null,
                        true,
                        false,
                        null,
                        null
                ),
                user()
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertTrue(text.contains("Секретарь С.С."));
            assertTrue(text.contains("Председатель педагогического совета: Директор Петрова И.И."));
            assertTrue(text.contains("Секретарь: Методист Беляев Д.А."));
            assertTrue(text.contains("М.П."));
            assertEquals(1, text.split("Верно", -1).length - 1);
        }
    }

    @Test
    void generatedProtocolUsesSchool1811LetterheadFromProtocolSnapshot() throws Exception {
        PedagogicalCouncilProtocol protocol = baseProtocol();
        protocol.setSchoolCodeSnapshot("1811");
        protocol.setSchoolNameSnapshot("ГБОУ Школа № 1811");
        protocol.setChairFioSnapshot("Иванова И.И.");
        protocol.setChairPositionSnapshot("Директор");
        protocol.setSecretaryFioSnapshot("Петрова П.П.");
        protocol.setSecretaryPositionSnapshot("Учитель");
        protocol.setAttendeeCount(20);
        protocol.getItems().add(item(protocol, 10L, 1));
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));

        PedagogicalCouncilDtos.FilePayload file = service.buildFullProtocol(1L);
        writeVisualQaSample(file, "school-1811-protocol.docx");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertTrue(text.contains("ПРОТОКОЛ № 8"));
            assertFalse(text.contains("ГОРОДА МОСКВЫ «ШКОЛА № 7»"));
            assertFalse(text.contains("ГБОУ Школа № 1811"));
            assertEquals(1, document.getAllPictures().size());
            assertEquals("png", document.getAllPictures().get(0).suggestFileExtension());
            assertTrue(maxInlinePictureWidth(document) > 5_000_000L);
        }
    }

    @Test
    void generatedSchool7ExtractUsesCleanLetterheadWithoutLetterFields() throws Exception {
        PedagogicalCouncilProtocol protocol = baseProtocol();
        protocol.setChairFioSnapshot("Петрова И.И.");
        protocol.setChairPositionSnapshot("Директор");
        protocol.setSecretaryFioSnapshot("Беляев Д.А.");
        protocol.setSecretaryPositionSnapshot("Методист");
        protocol.getItems().add(item(protocol, 10L, 1));
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));

        AppUser certifier = appUser(11L, "Беляев Дмитрий Александрович");
        when(users.findById(1L)).thenReturn(Optional.of(appUser(1L, "Секретарь")));
        when(users.findById(11L)).thenReturn(Optional.of(certifier));
        when(teachers.findByFioTeacherIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(permissions.findAllByTabAndCanExportTrue(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS))
                .thenReturn(List.of(permission(certifier)));

        PedagogicalCouncilDtos.FilePayload file = service.buildExtract(
                1L,
                new PedagogicalCouncilDtos.ExtractRequest(
                        List.of(10L),
                        List.of(11L),
                        false,
                        null,
                        false,
                        null
                ),
                user()
        );
        writeVisualQaSample(file, "school-7-extract.docx");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.content()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertTrue(text.contains("ВЫПИСКА ИЗ ПРОТОКОЛА"));
            assertTrue(text.contains("ГОРОДА МОСКВЫ «ШКОЛА № 7»"));
            assertFalse(text.contains("КОМУ"));
            assertFalse(text.contains("И.Д. Жданова"));
            assertTrue(text.contains("М.П."));
            assertEquals(1, document.getAllPictures().size());
            assertEquals("jpeg", document.getAllPictures().get(0).suggestFileExtension());
        }
    }

    private PedagogicalCouncilProtocol baseProtocol() {
        PedagogicalCouncilProtocol protocol = new PedagogicalCouncilProtocol();
        protocol.setId(1L);
        protocol.setAcademicYear("2025/2026");
        protocol.setProtocolNumber("8");
        protocol.setMeetingDate(LocalDate.of(2026, 3, 31));
        protocol.setSourceType(PedagogicalCouncilProtocol.SourceType.CONSTRUCTOR);
        protocol.setStatus(PedagogicalCouncilProtocol.Status.DRAFT);
        protocol.setSchoolCodeSnapshot("7");
        protocol.setSchoolNameSnapshot("ГБОУ Школа № 7");
        protocol.setCreatedByUsername("secretary");
        protocol.setCreatedByFio("Секретарь");
        return protocol;
    }

    private PedagogicalCouncilItem item(PedagogicalCouncilProtocol protocol, Long id, int order) {
        PedagogicalCouncilItem item = new PedagogicalCouncilItem();
        item.setId(id);
        item.setProtocol(protocol);
        item.setItemOrder(order);
        item.setAgendaTitle("Вопрос");
        item.setDecisionText("Решение");
        return item;
    }

    private SessionUser user() {
        return new SessionUser(
                1L,
                "secretary",
                "Секретарь",
                null,
                null,
                UserRole.METHODIST,
                true,
                true,
                true,
                null,
                false,
                new LinkedHashSet<>(),
                List.of()
        );
    }

    private AppUser appUser(Long id, String fio) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setFullName(fio);
        user.setRole(UserRole.METHODIST);
        user.setActive(true);
        user.setCanView(true);
        return user;
    }

    private AppUserTabPermission permission(AppUser user) {
        AppUserTabPermission permission = new AppUserTabPermission();
        permission.setUser(user);
        permission.setTab(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS);
        permission.setCanView(true);
        permission.setCanExport(true);
        return permission;
    }

    private byte[] docxWithText(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithTableAndPicture(String text) throws Exception {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var cell = document.createTable(1, 1).getRow(0).getCell(0);
            cell.setText(text);
            var pictureParagraph = cell.addParagraph();
            pictureParagraph.createRun().addPicture(
                    new ByteArrayInputStream(png),
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "marker.png",
                    org.apache.poi.util.Units.toEMU(0.2),
                    org.apache.poi.util.Units.toEMU(0.2)
            );
            document.write(output);
            return output.toByteArray();
        }
    }

    private void writeVisualQaSample(PedagogicalCouncilDtos.FilePayload file, String filename) throws Exception {
        String outputDirectory = System.getProperty("pedagogicalCouncil.visualQaDir");
        if (outputDirectory == null || outputDirectory.isBlank()) {
            outputDirectory = "target/pedagogical-council-visual-qa";
        }
        Path directory = Path.of(outputDirectory);
        Files.createDirectories(directory);
        Files.write(directory.resolve(filename), file.content());
    }

    private long maxInlinePictureWidth(XWPFDocument document) {
        return document.getParagraphs().stream()
                .flatMap(paragraph -> paragraph.getRuns().stream())
                .flatMap(run -> run.getEmbeddedPictures().stream())
                .mapToLong(picture -> picture.getCTPicture().getSpPr().getXfrm().getExt().getCx())
                .max()
                .orElse(0L);
    }
}
