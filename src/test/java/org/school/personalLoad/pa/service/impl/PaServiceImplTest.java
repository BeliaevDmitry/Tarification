package org.school.personalLoad.pa.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaReportVersion;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaWorkType;
import org.school.personalLoad.pa.repository.PaClassLevelAssignmentRepository;
import org.school.personalLoad.pa.repository.PaParticipationRepository;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.school.personalLoad.pa.repository.PaSpecImportLogRepository;
import org.school.personalLoad.pa.repository.PaSpecificationRepository;
import org.school.personalLoad.pa.repository.PaSpecificationTaskRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaServiceImplTest {

    @Mock
    private PaSpecificationRepository specificationRepository;
    @Mock
    private PaSpecificationTaskRepository taskRepository;
    @Mock
    private PaParticipationRepository participationRepository;
    @Mock
    private PaClassLevelAssignmentRepository classLevelAssignmentRepository;
    @Mock
    private PaReportVersionRepository reportVersionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PaSpecImportLogRepository paSpecImportLogRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private ContingentSnapshotRepository contingentSnapshotRepository;
    @Mock
    private ContingentStudentRepository contingentStudentRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;

    @Test
    void uploadReportsKeepsGeneratedTemplateAndOtherTeacherUploadsActive() throws Exception {
        String academicYear = "2025/2026";
        LocalDate workDate = LocalDate.of(2026, 5, 10);
        PaReportVersion generated = version("GENERATED", null, false, true);
        PaReportVersion firstTeacherUpload = version("ACCEPTED", "Иванова Анна Петровна", true, true);
        PaReportVersion previousSameTeacherUpload = version("ACCEPTED", "Петрова Мария Ивановна", true, true);
        when(teacherDirectoryRepository.findAll()).thenReturn(List.of(teacher("Петрова Мария Ивановна")));
        when(contingentSnapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear))
                .thenReturn(Optional.of(snapshot(10L, academicYear)));
        when(contingentStudentRepository.findAllBySnapshotId(10L))
                .thenReturn(List.of(student(10L, academicYear, "8-Ц", "Сидоров Семён")));
        when(reportVersionRepository.findAllByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDate(
                eq(academicYear), eq("Английский язык"), eq(PaScopeType.CLASS), eq("8-Ц"), eq(PaLevel.BASIC), eq(PaWorkType.EXIT), eq(workDate)))
                .thenReturn(List.of(generated, firstTeacherUpload, previousSameTeacherUpload));
        when(reportVersionRepository.findTopByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndWorkDateOrderByVersionNoDesc(
                eq(academicYear), eq("Английский язык"), eq(PaScopeType.CLASS), eq("8-Ц"), eq(PaLevel.BASIC), eq(PaWorkType.EXIT), eq(workDate)))
                .thenReturn(previousSameTeacherUpload);
        PaServiceImpl service = service();

        List<PaDtos.ReportUploadResult> result = service.uploadReports(
                academicYear,
                List.of(reportFile("report.xlsx", "Петрова Мария Ивановна", "Английский язык", "8-Ц", workDate)),
                "teacher2",
                "Петрова М.И."
        );

        assertEquals("ACCEPTED", result.get(0).status());
        assertEquals("Отчёт заменен", result.get(0).message());
        assertTrue(generated.isActiveVersion(), "Сгенерированный шаблон должен оставаться активным для других педагогов подгрупп");
        assertTrue(firstTeacherUpload.isActiveVersion(), "Сданный отчёт другого педагога не должен перетираться");
        assertFalse(previousSameTeacherUpload.isActiveVersion(), "Перезатирается только предыдущая сдача этого же педагога");
        verify(reportVersionRepository).saveAll(List.of(previousSameTeacherUpload));
        verify(reportVersionRepository).save(any(PaReportVersion.class));
    }

    private PaServiceImpl service() {
        return new PaServiceImpl(
                specificationRepository,
                taskRepository,
                participationRepository,
                classLevelAssignmentRepository,
                reportVersionRepository,
                eventPublisher,
                paSpecImportLogRepository,
                curriculumPlanEntryRepository,
                teacherDirectoryRepository,
                contingentSnapshotRepository,
                contingentStudentRepository,
                manualLoadEntryRepository
        );
    }

    private MockMultipartFile reportFile(String fileName, String teacher, String subject, String className, LocalDate workDate) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet info = workbook.createSheet("Информация");
            infoRow(info, 0, "Учитель", teacher);
            infoRow(info, 1, "Дата написания работы", workDate.toString());
            infoRow(info, 2, "Предмет", subject);
            infoRow(info, 3, "Класс", className);
            infoRow(info, 4, "Тип", "Выходная работа");
            infoRow(info, 5, "Уровень", "Базовый");
            infoRow(info, 6, "Школа", "ГБОУ №7");
            infoRow(info, 7, "Учебный год", "2025/2026");
            Sheet data = workbook.createSheet("Сбор информации");
            Row header = data.createRow(0);
            header.createCell(0).setCellValue("№");
            header.createCell(1).setCellValue("ФИО ученика");
            header.createCell(2).setCellValue("Присутствие");
            header.createCell(3).setCellValue("Вариант");
            Row row = data.createRow(3);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("Сидоров Семён");
            row.createCell(2).setCellValue("был");
            workbook.write(out);
            return new MockMultipartFile("files", fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private void infoRow(Sheet sheet, int rowIndex, String key, String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
    }

    private PaReportVersion version(String status, String teacherFio, boolean uploadedBackSuccess, boolean active) {
        PaReportVersion version = new PaReportVersion();
        version.setAcademicYear("2025/2026");
        version.setSubjectName("Английский язык");
        version.setScopeType(PaScopeType.CLASS);
        version.setScopeValue("8-Ц");
        version.setLevel(PaLevel.BASIC);
        version.setWorkType(PaWorkType.EXIT);
        version.setWorkDate(LocalDate.of(2026, 5, 10));
        version.setVersionNo(1);
        version.setStatus(status);
        version.setTeacherFio(teacherFio);
        version.setTeacherFioNormalized(teacherFio == null ? null : teacherFio.toUpperCase());
        version.setUploadedBackSuccess(uploadedBackSuccess);
        version.setActiveVersion(active);
        version.setCreatedAt(LocalDateTime.now());
        return version;
    }

    private TeacherDirectoryEntry teacher(String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private ContingentSnapshot snapshot(Long id, String academicYear) {
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(id);
        snapshot.setAcademicYear(academicYear);
        return snapshot;
    }

    private ContingentStudent student(Long snapshotId, String academicYear, String className, String fio) {
        ContingentStudent student = new ContingentStudent();
        student.setSnapshotId(snapshotId);
        student.setAcademicYear(academicYear);
        student.setClassName(className);
        student.setFullName(fio);
        return student;
    }
}
