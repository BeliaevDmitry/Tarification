package org.school.personalLoad.pa.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.*;
import org.school.personalLoad.pa.repository.PaWorkMaterialRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaWorkMaterialServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void textAndAnswersCanBeUploadedSeparatelyWithIndependentAuthorsAndPublicArchive() throws Exception {
        PaWorkMaterialRepository repository = mock(PaWorkMaterialRepository.class);
        CurriculumPlanEntryRepository curriculum = mock(CurriculumPlanEntryRepository.class);
        AtomicReference<PaWorkMaterial> stored = new AtomicReference<>();
        when(repository.findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndVariantCountOrderByUpdatedAtDesc(
                anyString(), anyString(), any(), anyString(), any(), any(), anyInt()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.saveAndFlush(any(PaWorkMaterial.class))).thenAnswer(invocation -> {
            PaWorkMaterial value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(41L);
            stored.set(value);
            return value;
        });
        when(repository.findById(41L)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc("2026/2027"))
                .thenAnswer(invocation -> List.of(stored.get()));
        PaWorkMaterialService service = new PaWorkMaterialService(repository, curriculum, tempDirectory.toString());

        PaDtos.WorkMaterialUploadResponse first = service.upload("2026/2027", "Математика", PaScopeType.CLASS,
                "7-А", PaLevel.BASIC, PaWorkType.EXIT, 3,
                new MockMultipartFile("textFile", "текст.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "WORK".getBytes()),
                null, "ivanov", "Иванов Иван Иванович");

        assertEquals(41L, first.id());
        assertTrue(first.textUploaded());
        assertFalse(first.answersUploaded());
        assertArrayEquals("WORK".getBytes(), service.loadFile(41L, PaWorkMaterialService.FileKind.TEXT));

        PaDtos.WorkMaterialUploadResponse second = service.upload("2026/2027", "Математика", PaScopeType.CLASS,
                "7-А", PaLevel.BASIC, PaWorkType.EXIT, 3, null,
                new MockMultipartFile("answersFile", "ответы.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "ANSWERS".getBytes()),
                "petrova", "Петрова Анна Сергеевна");

        assertFalse(second.textUploaded());
        assertTrue(second.answersUploaded());
        PaDtos.WorkMaterialRow row = service.materials("2026/2027").get(0);
        assertTrue(row.textAvailable());
        assertTrue(row.answersAvailable());
        assertEquals("Иванов Иван Иванович", row.textUploadedByFio());
        assertEquals("Петрова Анна Сергеевна", row.answersUploadedByFio());
        assertEquals(3, row.variantCount());

        byte[] archive = service.downloadAll("2026/2027");
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            while (zip.getNextEntry() != null) files++;
        }
        assertEquals(2, files);
    }

    @Test
    void referencesOfferBothParallelAndConcreteClassesFromCurriculum() {
        PaWorkMaterialRepository repository = mock(PaWorkMaterialRepository.class);
        CurriculumPlanEntryRepository curriculum = mock(CurriculumPlanEntryRepository.class);
        CurriculumPlanEntry first = curriculum("Русский язык", "5-А");
        CurriculumPlanEntry second = curriculum("Русский язык", "5-Б");
        when(curriculum.findAllByAcademicYear("2026/2027")).thenReturn(List.of(first, second));
        PaWorkMaterialService service = new PaWorkMaterialService(repository, curriculum, tempDirectory.toString());

        List<PaDtos.WorkMaterialReferenceRow> rows = service.references("2026/2027");

        assertEquals(2, rows.size());
        assertEquals(List.of("5-А", "5-Б"), rows.stream().map(PaDtos.WorkMaterialReferenceRow::className).toList());
        assertTrue(rows.stream().allMatch(row -> row.parallel() == 5));
    }

    @Test
    void rejectsUnsupportedFilesAndMissingClassLetter() {
        PaWorkMaterialService service = new PaWorkMaterialService(mock(PaWorkMaterialRepository.class),
                mock(CurriculumPlanEntryRepository.class), tempDirectory.toString());
        MockMultipartFile executable = new MockMultipartFile("textFile", "run.exe", "application/octet-stream", new byte[]{1});

        IllegalArgumentException badFormat = assertThrows(IllegalArgumentException.class, () ->
                service.upload("2026/2027", "Физика", PaScopeType.PARALLEL, "8", PaLevel.BASIC,
                        PaWorkType.EXIT, 1, executable, null, "u", "Пользователь"));
        assertTrue(badFormat.getMessage().contains("Недопустимый формат"));

        MockMultipartFile pdf = new MockMultipartFile("textFile", "work.pdf", "application/pdf", new byte[]{1});
        IllegalArgumentException badClass = assertThrows(IllegalArgumentException.class, () ->
                service.upload("2026/2027", "Физика", PaScopeType.CLASS, "8", PaLevel.BASIC,
                        PaWorkType.EXIT, 1, pdf, null, "u", "Пользователь"));
        assertTrue(badClass.getMessage().contains("номер и букву"));
    }

    private CurriculumPlanEntry curriculum(String subject, String className) {
        CurriculumPlanEntry entry = new CurriculumPlanEntry();
        entry.setSubjectName(subject);
        entry.setClassName(className);
        entry.setDeprecated(false);
        return entry;
    }
}
