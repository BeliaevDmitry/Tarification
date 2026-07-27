package org.school.personalLoad.service.impl;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.TeacherOneCImportDtos;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeacherDirectoryOneCImportTest {

    private TeacherDirectoryRepository teachers;
    private ManualLoadEntryRepository loadEntries;
    private TeacherDirectoryServiceImpl service;

    @BeforeEach
    void setUp() {
        teachers = mock(TeacherDirectoryRepository.class);
        loadEntries = mock(ManualLoadEntryRepository.class);
        service = new TeacherDirectoryServiceImpl(teachers, loadEntries);
    }

    @Test
    void previewRequiresDecisionWhenPrimaryEndedButAdditionalEmploymentIsActive() throws Exception {
        TeacherDirectoryEntry teacher = teacher(10L, "Иванов Иван Иванович", "Учитель");
        when(teachers.findAll()).thenReturn(List.of(teacher));

        LocalDate past = LocalDate.now().minusMonths(2);
        MockMultipartFile file = oneCFile(List.of(
                row("Иванов Иван Иванович", "100", "Учитель /Педагогические работники/",
                        LocalDate.now().minusYears(2), past, "Основное место работы"),
                row("Иванов Иван Иванович", "101", "Методист /Педагогические работники/",
                        LocalDate.now().minusYears(1), null, "Внутреннее совместительство")
        ));

        TeacherOneCImportDtos.Preview preview = service.previewOneCImport(file);

        assertEquals(2, preview.sourceRowCount());
        assertEquals(1, preview.rows().size());
        TeacherOneCImportDtos.PreviewRow change = preview.rows().get(0);
        assertEquals("Методист", change.proposedPosition());
        assertTrue(change.activeAdditionalEmployment());
        assertFalse(change.activePrimaryEmployment());
        assertEquals(List.of("DISMISS", "ACCEPT_ADDITIONAL", "IGNORE"), change.allowedActions());
        assertEquals("IGNORE", change.recommendedAction());
    }

    @Test
    void applyUpdatesPrimaryPositionOnlyAfterPreviewDecision() throws Exception {
        TeacherDirectoryEntry teacher = teacher(11L, "Петров Пётр Петрович", "Учитель");
        when(teachers.findAll()).thenReturn(List.of(teacher));
        when(teachers.findById(11L)).thenReturn(Optional.of(teacher));
        when(teachers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = oneCFile(List.of(
                row("Петров Пётр Петрович", "200", "Заместитель директора /Административно-управленческий персонал/",
                        LocalDate.now().minusYears(1), null, "Основное место работы")
        ));

        var result = service.applyOneCImport(
                file,
                new TeacherOneCImportDtos.ApplyRequest(List.of(
                        new TeacherOneCImportDtos.Decision("Петров Пётр Петрович", "UPDATE")
                )),
                "Кадровик"
        );

        assertEquals(1, result.get("updated"));
        assertEquals("Заместитель директора", teacher.getPrimaryPosition());
        assertEquals("200", teacher.getPersonnelNumber());
        assertEquals("Основное место работы", teacher.getEmploymentType());
        assertNotNull(teacher.getLastOneCSyncAt());
        verify(teachers).save(teacher);
    }

    @Test
    void frontendUsesTwoStepOneCImportWithExplicitDecisions() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/teachers.html"));
        String js = Files.readString(Path.of("src/main/resources/static/teachers.js"));
        String controller = Files.readString(Path.of(
                "src/main/java/org/school/personalLoad/controller/api/TeacherDirectoryController.java"));

        assertTrue(html.contains("Проверить выгрузку 1С"));
        assertTrue(html.contains("Применить выбранные решения"));
        assertTrue(html.contains("Основная должность"));
        assertTrue(js.contains("/api/teachers/import-1c/preview"));
        assertTrue(js.contains("/api/teachers/import-1c/apply"));
        assertTrue(js.contains("ACCEPT_ADDITIONAL"));
        assertTrue(controller.contains("@RequestPart(\"request\") TeacherOneCImportDtos.ApplyRequest"));
    }

    private TeacherDirectoryEntry teacher(Long id, String fio, String position) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        teacher.setPrimaryPosition(position);
        return teacher;
    }

    private OneCRow row(String fio,
                        String personnelNumber,
                        String position,
                        LocalDate employmentDate,
                        LocalDate dismissalDate,
                        String employmentType) {
        return new OneCRow(fio, personnelNumber, position, employmentDate, dismissalDate, employmentType);
    }

    private MockMultipartFile oneCFile(List<OneCRow> rows) throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Лист_1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Имя");
            header.createCell(1).setCellValue("Таб. номер");
            header.createCell(2).setCellValue("Должность по штатному расписанию");
            header.createCell(3).setCellValue("Дата приема");
            header.createCell(4).setCellValue("Дата увольнения");
            header.createCell(5).setCellValue("Вид занятости");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            int index = 1;
            for (OneCRow source : rows) {
                var row = sheet.createRow(index++);
                row.createCell(0).setCellValue(source.fio());
                row.createCell(1).setCellValue(source.personnelNumber());
                row.createCell(2).setCellValue(source.position());
                row.createCell(3).setCellValue(formatter.format(source.employmentDate()));
                if (source.dismissalDate() != null) {
                    row.createCell(4).setCellValue(formatter.format(source.dismissalDate()));
                }
                row.createCell(5).setCellValue(source.employmentType());
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "staff.xls",
                    "application/vnd.ms-excel",
                    output.toByteArray()
            );
        }
    }

    private record OneCRow(
            String fio,
            String personnelNumber,
            String position,
            LocalDate employmentDate,
            LocalDate dismissalDate,
            String employmentType
    ) {
    }
}
