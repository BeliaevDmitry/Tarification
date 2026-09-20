package org.school.personalLoad.pa.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.*;
import org.school.personalLoad.pa.repository.PaWorkMaterialFileRepository;
import org.school.personalLoad.pa.repository.PaWorkMaterialRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaWorkMaterialServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsSeveralFilesAtOnceAndAddsMissingFilesOnlyThroughRegistryEdit() throws Exception {
        Fixture fixture = fixture();

        PaDtos.WorkMaterialUploadResponse first = fixture.service.upload("2026/2027", "Математика",
                PaScopeType.CLASS, "7-А", PaLevel.BASIC, PaWorkType.EXIT, 2,
                List.of(file("textFiles", "вариант-1.docx", "WORK-1"),
                        file("textFiles", "вариант-2.docx", "WORK-2")),
                List.of(), "ivanov", "Иванов Иван Иванович");

        assertEquals(41L, first.id());
        assertEquals(2, first.textFilesUploaded());
        assertEquals(0, first.answerFilesUploaded());

        PaDtos.WorkMaterialUploadResponse second = fixture.service.update(41L, 2, false, false,
                List.of(), List.of(file("answerFiles", "ответы.xlsx", "ANSWERS")),
                "petrova", "Петрова Анна Сергеевна");

        assertEquals(0, second.textFilesUploaded());
        assertEquals(1, second.answerFilesUploaded());
        PaDtos.WorkMaterialRow row = fixture.service.materials("2026/2027").get(0);
        assertEquals(2, row.textFiles().size());
        assertEquals(1, row.answerFiles().size());
        assertEquals(2, row.variantCount());
        assertTrue(row.textFiles().stream().allMatch(file -> "Иванов Иван Иванович".equals(file.uploadedByFio())));
        assertEquals("Петрова Анна Сергеевна", row.answerFiles().get(0).uploadedByFio());

        byte[] archive = fixture.service.downloadAll("2026/2027");
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            while (zip.getNextEntry() != null) files++;
        }
        assertEquals(3, files);
    }

    @Test
    void repeatedInitialUploadIsRejectedAndSameNameCanBeUpdatedThroughRegistry() throws Exception {
        Fixture fixture = fixture();
        fixture.service.upload("2026/2027", "Русский язык", PaScopeType.PARALLEL, "9",
                PaLevel.BASIC, PaWorkType.ENTRY, 10,
                List.of(file("textFiles", "все-варианты.pdf", "OLD")), List.of(),
                "first", "Первый Автор");

        PaDtos.WorkMaterialFileRow before = fixture.service.materials("2026/2027").get(0).textFiles().get(0);
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class, () ->
                fixture.service.upload("2026/2027", "Русский язык", PaScopeType.PARALLEL, "9",
                        PaLevel.BASIC, PaWorkType.ENTRY, 12,
                        List.of(file("textFiles", "все-варианты.pdf", "NEW")), List.of(),
                        "second", "Второй Автор"));
        assertTrue(duplicate.getMessage().contains("Реестр файлов"));

        fixture.service.update(41L, 12, false, false,
                List.of(file("textFiles", "все-варианты.pdf", "NEW")), List.of(),
                "second", "Второй Автор");

        PaDtos.WorkMaterialRow row = fixture.service.materials("2026/2027").get(0);
        assertEquals(1, row.textFiles().size());
        assertEquals(12, row.variantCount());
        assertEquals(before.id(), row.textFiles().get(0).id());
        assertEquals("Второй Автор", row.textFiles().get(0).uploadedByFio());
        assertArrayEquals("NEW".getBytes(), fixture.service.loadAttachment(before.id()));
    }

    @Test
    void registryEditReplacesTextsAndAddsAnswersToTheSameMaterial() throws Exception {
        Fixture fixture = fixture();
        fixture.service.upload("2026/2027", "Математика", PaScopeType.CLASS, "7-А",
                PaLevel.BASIC, PaWorkType.EXIT, 2,
                List.of(file("textFiles", "вариант-1.docx", "OLD-1"),
                        file("textFiles", "вариант-2.docx", "OLD-2")),
                List.of(file("answerFiles", "ответы-1.xlsx", "ANSWERS-1")),
                "first", "Первый Автор");
        List<Long> oldTextIds = fixture.service.materials("2026/2027").get(0).textFiles().stream()
                .map(PaDtos.WorkMaterialFileRow::id).toList();

        PaDtos.WorkMaterialUploadResponse result = fixture.service.update(41L, 3, true, false,
                List.of(file("textFiles", "новые-варианты.pdf", "NEW-TEXTS")),
                List.of(file("answerFiles", "ответы-2.xlsx", "ANSWERS-2")),
                "editor", "Редактор Реестра");

        assertEquals(41L, result.id());
        PaDtos.WorkMaterialRow row = fixture.service.materials("2026/2027").get(0);
        assertEquals(3, row.variantCount());
        assertEquals(List.of("новые-варианты.pdf"), row.textFiles().stream()
                .map(PaDtos.WorkMaterialFileRow::fileName).toList());
        assertEquals(List.of("ответы-1.xlsx", "ответы-2.xlsx"), row.answerFiles().stream()
                .map(PaDtos.WorkMaterialFileRow::fileName).toList());
        assertTrue(oldTextIds.stream().allMatch(id -> assertThrows(IllegalArgumentException.class,
                () -> fixture.service.loadAttachment(id)).getMessage().contains("не найден")));
    }

    @Test
    void registryEditDoesNotRemoveFilesWhenReplacementUploadIsMissing() throws Exception {
        Fixture fixture = fixture();
        fixture.service.upload("2026/2027", "Физика", PaScopeType.PARALLEL, "8",
                PaLevel.BASIC, PaWorkType.EXIT, 1,
                List.of(file("textFiles", "работа.pdf", "WORK")), List.of(),
                "first", "Первый Автор");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                fixture.service.update(41L, 1, true, false, List.of(), List.of(),
                        "editor", "Редактор Реестра"));

        assertTrue(error.getMessage().contains("Для замены текстов"));
        assertEquals(1, fixture.service.materials("2026/2027").get(0).textFiles().size());
    }

    @Test
    void parallelAndConcreteClassAreStoredAsSeparateWorkSets() throws Exception {
        Fixture fixture = fixture();

        PaDtos.WorkMaterialUploadResponse parallel = fixture.service.upload("2026/2027", "Математика",
                PaScopeType.PARALLEL, "6", PaLevel.BASIC, PaWorkType.EXIT, 4,
                List.of(file("textFiles", "математика-6-параллель.pdf", "ALL-SIXTH")), List.of(),
                "methodist", "Методист");
        PaDtos.WorkMaterialUploadResponse concreteClass = fixture.service.upload("2026/2027", "Математика",
                PaScopeType.CLASS, "6-а", PaLevel.BASIC, PaWorkType.EXIT, 1,
                List.of(file("textFiles", "математика-6а.pdf", "ONLY-6A")), List.of(),
                "methodist", "Методист");

        assertNotEquals(parallel.id(), concreteClass.id());
        List<PaDtos.WorkMaterialRow> rows = fixture.service.materials("2026/2027");
        assertEquals(2, rows.size());
        PaDtos.WorkMaterialRow parallelRow = rows.stream()
                .filter(row -> row.scopeType() == PaScopeType.PARALLEL).findFirst().orElseThrow();
        PaDtos.WorkMaterialRow classRow = rows.stream()
                .filter(row -> row.scopeType() == PaScopeType.CLASS).findFirst().orElseThrow();
        assertEquals("6", parallelRow.scopeValue());
        assertEquals("6-А", classRow.scopeValue());
        assertEquals(List.of("математика-6-параллель.pdf"), parallelRow.textFiles().stream()
                .map(PaDtos.WorkMaterialFileRow::fileName).toList());
        assertEquals(List.of("математика-6а.pdf"), classRow.textFiles().stream()
                .map(PaDtos.WorkMaterialFileRow::fileName).toList());
    }

    @Test
    void registryDeleteRemovesRecordAndAllAttachments() throws Exception {
        Fixture fixture = fixture();
        fixture.service.upload("2026/2027", "Математика", PaScopeType.CLASS, "6-А",
                PaLevel.BASIC, PaWorkType.EXIT, 1,
                List.of(file("textFiles", "работа.pdf", "WORK")),
                List.of(file("answerFiles", "ответы.pdf", "ANSWERS")),
                "methodist", "Методист");
        List<Long> fileIds = fixture.service.materials("2026/2027").get(0).textFiles().stream()
                .map(PaDtos.WorkMaterialFileRow::id).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        fileIds.addAll(fixture.service.materials("2026/2027").get(0).answerFiles().stream()
                .map(PaDtos.WorkMaterialFileRow::id).toList());

        fixture.service.delete(41L);

        assertTrue(fixture.service.materials("2026/2027").isEmpty());
        fileIds.forEach(id -> assertThrows(IllegalArgumentException.class,
                () -> fixture.service.loadAttachment(id)));
    }

    @Test
    void referencesOfferBothParallelAndConcreteClassesFromCurriculum() {
        PaWorkMaterialRepository repository = mock(PaWorkMaterialRepository.class);
        PaWorkMaterialFileRepository files = mock(PaWorkMaterialFileRepository.class);
        CurriculumPlanEntryRepository curriculum = mock(CurriculumPlanEntryRepository.class);
        CurriculumPlanEntry first = curriculum("Русский язык", "5-А");
        CurriculumPlanEntry second = curriculum("Русский язык", "5-Б");
        when(curriculum.findAllByAcademicYear("2026/2027")).thenReturn(List.of(first, second));
        PaWorkMaterialService service = new PaWorkMaterialService(repository, files, curriculum, tempDirectory.toString());

        List<PaDtos.WorkMaterialReferenceRow> rows = service.references("2026/2027");

        assertEquals(2, rows.size());
        assertEquals(List.of("5-А", "5-Б"), rows.stream().map(PaDtos.WorkMaterialReferenceRow::className).toList());
        assertTrue(rows.stream().allMatch(row -> row.parallel() == 5));
    }

    @Test
    void rejectsUnsupportedFilesAndMissingClassLetter() {
        PaWorkMaterialService service = new PaWorkMaterialService(mock(PaWorkMaterialRepository.class),
                mock(PaWorkMaterialFileRepository.class), mock(CurriculumPlanEntryRepository.class),
                tempDirectory.toString());
        MockMultipartFile executable = new MockMultipartFile("textFiles", "run.exe", "application/octet-stream", new byte[]{1});

        IllegalArgumentException badFormat = assertThrows(IllegalArgumentException.class, () ->
                service.upload("2026/2027", "Физика", PaScopeType.PARALLEL, "8", PaLevel.BASIC,
                        PaWorkType.EXIT, 1, List.of(executable), List.of(), "u", "Пользователь"));
        assertTrue(badFormat.getMessage().contains("Недопустимый формат"));

        MockMultipartFile pdf = new MockMultipartFile("textFiles", "work.pdf", "application/pdf", new byte[]{1});
        IllegalArgumentException badClass = assertThrows(IllegalArgumentException.class, () ->
                service.upload("2026/2027", "Физика", PaScopeType.CLASS, "8", PaLevel.BASIC,
                        PaWorkType.EXIT, 1, List.of(pdf), List.of(), "u", "Пользователь"));
        assertTrue(badClass.getMessage().contains("номер и букву"));

        IllegalArgumentException badVariantCount = assertThrows(IllegalArgumentException.class, () ->
                service.upload("2026/2027", "Физика", PaScopeType.PARALLEL, "8", PaLevel.BASIC,
                        PaWorkType.EXIT, 0, List.of(pdf), List.of(), "u", "Пользователь"));
        assertTrue(badVariantCount.getMessage().contains("Количество вариантов"));
    }

    private Fixture fixture() {
        PaWorkMaterialRepository repository = mock(PaWorkMaterialRepository.class);
        PaWorkMaterialFileRepository fileRepository = mock(PaWorkMaterialFileRepository.class);
        CurriculumPlanEntryRepository curriculum = mock(CurriculumPlanEntryRepository.class);
        List<PaWorkMaterial> stored = new ArrayList<>();
        AtomicLong materialIds = new AtomicLong(41);
        List<PaWorkMaterialFile> files = new ArrayList<>();
        AtomicLong fileIds = new AtomicLong(100);

        when(repository.findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeOrderByUpdatedAtDesc(
                anyString(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(invocation -> stored.stream().filter(material ->
                                material.getAcademicYear().equals(invocation.getArgument(0))
                                        && material.getSubjectName().equals(invocation.getArgument(1))
                                        && material.getScopeType() == invocation.getArgument(2)
                                        && material.getScopeValue().equals(invocation.getArgument(3))
                                        && material.getLevel() == invocation.getArgument(4)
                                        && material.getWorkType() == invocation.getArgument(5))
                        .findFirst());
        when(repository.saveAndFlush(any(PaWorkMaterial.class))).thenAnswer(invocation -> {
            PaWorkMaterial value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(materialIds.getAndIncrement());
                stored.add(value);
            }
            return value;
        });
        when(repository.findById(anyLong())).thenAnswer(invocation -> stored.stream()
                .filter(material -> material.getId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc(anyString()))
                .thenAnswer(invocation -> stored.stream()
                        .filter(material -> material.getAcademicYear().equals(invocation.getArgument(0))).toList());
        doAnswer(invocation -> {
            stored.remove(invocation.getArgument(0));
            return null;
        }).when(repository).delete(any(PaWorkMaterial.class));

        when(fileRepository.saveAndFlush(any(PaWorkMaterialFile.class))).thenAnswer(invocation -> {
            PaWorkMaterialFile value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(fileIds.getAndIncrement());
                files.add(value);
            }
            return value;
        });
        when(fileRepository.findFirstByMaterialIdAndKindAndOriginalFileNameIgnoreCaseOrderByIdDesc(
                anyLong(), any(), anyString())).thenAnswer(invocation -> {
            Long materialId = invocation.getArgument(0);
            PaWorkMaterialFileKind kind = invocation.getArgument(1);
            String name = invocation.getArgument(2);
            return files.stream().filter(file -> file.getMaterial().getId().equals(materialId)
                            && file.getKind() == kind && file.getOriginalFileName().equalsIgnoreCase(name))
                    .findFirst();
        });
        when(fileRepository.findAllByMaterialIdInOrderByMaterialIdAscKindAscOriginalFileNameAscIdAsc(anyList()))
                .thenAnswer(invocation -> List.copyOf(files));
        when(fileRepository.findAllByMaterialIdOrderByKindAscOriginalFileNameAscIdAsc(anyLong()))
                .thenAnswer(invocation -> List.copyOf(files));
        when(fileRepository.findById(anyLong())).thenAnswer(invocation -> files.stream()
                .filter(file -> file.getId().equals(invocation.getArgument(0))).findFirst());
        doAnswer(invocation -> {
            files.removeAll(invocation.getArgument(0));
            return null;
        }).when(fileRepository).deleteAll(anyList());

        return new Fixture(new PaWorkMaterialService(repository, fileRepository, curriculum, tempDirectory.toString()));
    }

    private MockMultipartFile file(String field, String name, String body) {
        return new MockMultipartFile(field, name, "application/octet-stream", body.getBytes());
    }

    private CurriculumPlanEntry curriculum(String subject, String className) {
        CurriculumPlanEntry entry = new CurriculumPlanEntry();
        entry.setSubjectName(subject);
        entry.setClassName(className);
        entry.setDeprecated(false);
        return entry;
    }

    private record Fixture(PaWorkMaterialService service) {
    }
}
