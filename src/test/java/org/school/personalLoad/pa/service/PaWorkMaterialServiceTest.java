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
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaWorkMaterialServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsSeveralFilesAtOnceAndAdditionalFilesOneByOne() throws Exception {
        Fixture fixture = fixture();

        PaDtos.WorkMaterialUploadResponse first = fixture.service.upload("2026/2027", "Математика",
                PaScopeType.CLASS, "7-А", PaLevel.BASIC, PaWorkType.EXIT, 2,
                List.of(file("textFiles", "вариант-1.docx", "WORK-1"),
                        file("textFiles", "вариант-2.docx", "WORK-2")),
                List.of(), "ivanov", "Иванов Иван Иванович");

        assertEquals(41L, first.id());
        assertEquals(2, first.textFilesUploaded());
        assertEquals(0, first.answerFilesUploaded());

        PaDtos.WorkMaterialUploadResponse second = fixture.service.upload("2026/2027", "Математика",
                PaScopeType.CLASS, "7-А", PaLevel.BASIC, PaWorkType.EXIT, 2,
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
    void oneCombinedFileIsAllowedAndSameNameIsUpdatedWithoutDuplicate() throws Exception {
        Fixture fixture = fixture();
        fixture.service.upload("2026/2027", "Русский язык", PaScopeType.PARALLEL, "9",
                PaLevel.BASIC, PaWorkType.ENTRY, 10,
                List.of(file("textFiles", "все-варианты.pdf", "OLD")), List.of(),
                "first", "Первый Автор");

        PaDtos.WorkMaterialFileRow before = fixture.service.materials("2026/2027").get(0).textFiles().get(0);
        fixture.service.upload("2026/2027", "Русский язык", PaScopeType.PARALLEL, "9",
                PaLevel.BASIC, PaWorkType.ENTRY, 12,
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
        AtomicReference<PaWorkMaterial> stored = new AtomicReference<>();
        List<PaWorkMaterialFile> files = new ArrayList<>();
        AtomicLong fileIds = new AtomicLong(100);

        when(repository.findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeOrderByUpdatedAtDesc(
                anyString(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.saveAndFlush(any(PaWorkMaterial.class))).thenAnswer(invocation -> {
            PaWorkMaterial value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(41L);
            stored.set(value);
            return value;
        });
        when(repository.findById(41L)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc(anyString()))
                .thenAnswer(invocation -> stored.get() == null ? List.of() : List.of(stored.get()));

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
        when(fileRepository.findById(anyLong())).thenAnswer(invocation -> files.stream()
                .filter(file -> file.getId().equals(invocation.getArgument(0))).findFirst());

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
