package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.StudentDataExchangeDtos;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudentGroupMembership;
import org.school.personalLoad.model.StudentGroupMembershipSource;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.StudentSupportDocument;
import org.school.personalLoad.model.StudentSupportStatus;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumMeshMappingRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.IupPlanRepository;
import org.school.personalLoad.repository.IupSubjectLineRepository;
import org.school.personalLoad.repository.IupTeacherAssignmentRepository;
import org.school.personalLoad.repository.NosologyCatalogEntryRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentGroupMembershipRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.StudentSupportDocumentAttachmentRepository;
import org.school.personalLoad.repository.StudentSupportDocumentRepository;
import org.school.personalLoad.repository.StudentSupportStatusRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.StudentSupportService;
import org.school.personalLoad.service.StudentDataExchangeService;
import org.school.personalLoad.service.IupLoadService;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class StudentDataExchangeServiceImplTest {

    private static final String YEAR = "2026/2027";
    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private ContingentSnapshotRepository snapshotRepository;
    @Mock
    private ContingentStudentRepository contingentStudentRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private StudentSupportStatusRepository supportStatusRepository;
    @Mock
    private StudentSupportDocumentRepository supportDocumentRepository;
    @Mock
    private StudentSupportDocumentAttachmentRepository supportDocumentAttachmentRepository;
    @Mock
    private NosologyCatalogEntryRepository nosologyRepository;
    @Mock
    private IupPlanRepository iupPlanRepository;
    @Mock
    private IupSubjectLineRepository iupSubjectLineRepository;
    @Mock
    private IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    @Mock
    private StudentGroupMembershipRepository groupMembershipRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumRepository;
    @Mock
    private CurriculumMeshMappingRepository meshMappingRepository;
    @Mock
    private TeacherDirectoryRepository teacherRepository;
    @Mock
    private StudentClassEnrollmentRepository enrollmentRepository;
    @Mock
    private StudentSupportService studentSupportService;
    @Mock
    private IupLoadService iupLoadService;

    @InjectMocks
    private StudentDataExchangeServiceImpl service;

    private ContingentSnapshot snapshot;
    private ContingentStudent source;
    private StudentProfile profile;
    private CurriculumPlanEntry curriculum;

    @BeforeEach
    void setUp() {
        snapshot = new ContingentSnapshot();
        snapshot.setId(10L);
        snapshot.setAcademicYear(YEAR);
        snapshot.setSnapshotDate(SNAPSHOT_DATE);
        snapshot.setSourceFileName("Контингент.xlsx");

        source = new ContingentStudent();
        source.setStudentId(1L);
        source.setAcademicYear(YEAR);
        source.setRecordNumber("ЛД-1");
        source.setFullName("Иванов Иван Иванович");
        source.setBirthDate("01.01.2015");
        source.setClassName("5-А");

        profile = new StudentProfile();
        profile.setId(1L);
        profile.setCurrentFullName(source.getFullName());
        profile.setNormalizedFullName("иванов иван иванович");
        profile.setBirthDate(LocalDate.of(2015, 1, 1));
        profile.setRecordNumber("ЛД-1");

        curriculum = new CurriculumPlanEntry();
        curriculum.setId(25L);
        curriculum.setAcademicYear(YEAR);
        curriculum.setClassId(5L);
        curriculum.setClassName("5-А");
        curriculum.setSubjectName("Иностранный язык");
        curriculum.setSubgroupRequired(true);
        curriculum.setSubgroupCount(2);

        when(snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(YEAR))
                .thenReturn(Optional.of(snapshot));
        when(contingentStudentRepository.findAllBySnapshotId(10L)).thenReturn(List.of(source));
        lenient().when(studentProfileRepository.findAllById(any())).thenReturn(List.of(profile));
        when(curriculumRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of(curriculum));
    }

    @Test
    void exportCreatesRoundTripPackageAndUsesUpNamesAsMeshDefaults() throws Exception {
        byte[] body = service.exportPackage(YEAR);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            assertEquals(13, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("Инструкция"));
            assertNotNull(workbook.getSheet("Дети"));
            assertNotNull(workbook.getSheet("Нозологии"));
            assertNotNull(workbook.getSheet("Статусы"));
            assertNotNull(workbook.getSheet("Документы"));
            assertNotNull(workbook.getSheet("ИУП"));
            assertNotNull(workbook.getSheet("Предметы ИУП"));
            assertNotNull(workbook.getSheet("Педагоги ИУП"));
            assertNotNull(workbook.getSheet("Названия УП-МЭШ"));
            assertNotNull(workbook.getSheet("Распределение"));
            assertNotNull(workbook.getSheet("Справочник педагогов"));
            assertNotNull(workbook.getSheet("Проекция групп"));
            assertNotNull(workbook.getSheet("Контроль готовности"));

            Sheet names = workbook.getSheet("Названия УП-МЭШ");
            int subjectUp = column(names, "Предмет УП");
            int subjectMesh = column(names, "Предмет МЭШ");
            assertEquals(
                    names.getRow(1).getCell(subjectUp).getStringCellValue(),
                    names.getRow(1).getCell(subjectMesh).getStringCellValue()
            );

            Sheet distribution = workbook.getSheet("Распределение");
            assertEquals("Иванов Иван Иванович",
                    distribution.getRow(1).getCell(column(distribution, "ФИО")).getStringCellValue());
            assertTrue(distribution.getRow(1).getCell(column(distribution, "Группа УП")) == null
                    || distribution.getRow(1).getCell(column(distribution, "Группа УП")).getStringCellValue().isBlank());
        }
    }

    @Test
    void exportedDistributionCanBeFilledAndImportedBack() throws Exception {
        byte[] exported = service.exportPackage(YEAR);
        byte[] filled;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exported));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet distribution = workbook.getSheet("Распределение");
            Row row = distribution.getRow(1);
            set(row, column(distribution, "Действие"), "UPSERT");
            set(row, column(distribution, "Группа УП"), "Группа 1");
            set(row, column(distribution, "Группа МЭШ"), "Группа 1");
            workbook.write(output);
            filled = output.toByteArray();
        }

        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(curriculumRepository.findById(25L)).thenReturn(Optional.of(curriculum));
        when(groupMembershipRepository.findAllByStudent_IdAndAcademicYear(1L, YEAR)).thenReturn(List.of());
        when(groupMembershipRepository.save(any(StudentGroupMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentDataExchangeDtos.ImportResult result = service.importPackage(
                YEAR,
                new MockMultipartFile(
                        "file",
                        "Пакет.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        filled
                )
        );

        ArgumentCaptor<StudentGroupMembership> captor = ArgumentCaptor.forClass(StudentGroupMembership.class);
        verify(groupMembershipRepository).save(captor.capture());
        assertEquals("Группа 1", captor.getValue().getGroupNameEducationalPlan());
        assertEquals(StudentGroupMembershipSource.MESH_IMPORT, captor.getValue().getSource());
        assertTrue(result.getImported() >= 1);
        assertTrue(result.getErrors().isEmpty(), () -> "Ошибки импорта: " + result.getErrors());
    }

    @Test
    void exportedDocumentTemplateCanBeImportedBackWithoutBinaryCopies() throws Exception {
        byte[] exported = service.exportPackage(YEAR);
        byte[] filled;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exported));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet documents = workbook.getSheet("Документы");
            Row row = documents.getRow(1);
            set(row, column(documents, "Действие"), "UPSERT");
            set(row, column(documents, "Карточка ID"), "1");
            workbook.write(output);
            filled = output.toByteArray();
        }
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(supportDocumentRepository.save(any(StudentSupportDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentDataExchangeDtos.ImportResult result = service.importPackage(
                YEAR,
                new MockMultipartFile(
                        "file",
                        "Пакет.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        filled
                )
        );

        assertTrue(result.getErrors().isEmpty(), () -> "Ошибки импорта: " + result.getErrors());
        assertEquals(1, result.getSheets().stream()
                .filter(sheet -> "Документы".equals(sheet.getSheetName()))
                .mapToInt(StudentDataExchangeDtos.SheetImportResult::getImported)
                .sum(), () -> "Результаты листов: " + result.getSheets());
        ArgumentCaptor<StudentSupportDocument> captor =
                ArgumentCaptor.forClass(StudentSupportDocument.class);
        verify(supportDocumentRepository).save(captor.capture());
        assertEquals("МСЭ-001", captor.getValue().getDocumentNumber());
        assertEquals(profile, captor.getValue().getStudent());
    }

    @Test
    void meshStyleRowCanBeImportedByNameBirthDateAndMatchingUpNames() throws Exception {
        byte[] exported = service.exportPackage(YEAR);
        byte[] filled;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exported));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet distribution = workbook.getSheet("Распределение");
            Row row = distribution.getRow(1);
            set(row, column(distribution, "Действие"), "UPSERT");
            clear(row, column(distribution, "Карточка ID"));
            clear(row, column(distribution, "Личное дело"));
            clear(row, column(distribution, "Строка УП ID"));
            clear(row, column(distribution, "Предмет УП"));
            clear(row, column(distribution, "Группа УП"));
            set(row, column(distribution, "Группа МЭШ"), "Группа 2");
            workbook.write(output);
            filled = output.toByteArray();
        }

        when(studentProfileRepository.findAllByNormalizedFullNameAndBirthDate(
                "иванов иван иванович",
                LocalDate.of(2015, 1, 1)
        )).thenReturn(List.of(profile));
        when(groupMembershipRepository.findAllByStudent_IdAndAcademicYear(1L, YEAR)).thenReturn(List.of());
        when(groupMembershipRepository.save(any(StudentGroupMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentDataExchangeDtos.ImportResult result = service.importPackage(
                YEAR,
                new MockMultipartFile("file", "mesh.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", filled)
        );

        ArgumentCaptor<StudentGroupMembership> captor = ArgumentCaptor.forClass(StudentGroupMembership.class);
        verify(groupMembershipRepository).save(captor.capture());
        assertEquals(25L, captor.getValue().getCurriculumEntryId());
        assertEquals("Группа 2", captor.getValue().getGroupNameEducationalPlan());
        assertTrue(result.getErrors().isEmpty(), () -> "Ошибки импорта МЭШ: " + result.getErrors());
    }

    @Test
    void completedDistributionProvidesActualGroupSizeForSalary() {
        StudentGroupMembership membership = new StudentGroupMembership();
        membership.setId(50L);
        membership.setStudent(profile);
        membership.setAcademicYear(YEAR);
        membership.setCurriculumEntryId(25L);
        membership.setGroupNameEducationalPlan("Группа 2");
        membership.setValidFrom(SNAPSHOT_DATE);
        membership.setSource(StudentGroupMembershipSource.MESH_IMPORT);
        when(groupMembershipRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of(membership));

        ManualLoadEntry load = new ManualLoadEntry();
        load.setId(70L);
        load.setAcademicYear(YEAR);
        load.setClassId(5L);
        load.setClassName("5-А");
        load.setSubjectName("Иностранный язык");
        load.setGroupNameEducationalPlan("Группа 2");

        StudentDataExchangeService.StudentCountResolution result =
                service.resolveStudentCounts(YEAR, List.of(load));

        assertTrue(result.contingentMode());
        assertEquals(1, result.childrenByLoadEntry().get(70L));
    }

    @Test
    void k3StudentCountsAsThreeForSalaryCalculation() {
        StudentGroupMembership membership = new StudentGroupMembership();
        membership.setId(51L);
        membership.setStudent(profile);
        membership.setAcademicYear(YEAR);
        membership.setCurriculumEntryId(25L);
        membership.setGroupNameEducationalPlan("Группа 1");
        membership.setValidFrom(SNAPSHOT_DATE);
        membership.setSource(StudentGroupMembershipSource.MESH_IMPORT);
        when(groupMembershipRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of(membership));

        StudentSupportStatus status = new StudentSupportStatus();
        status.setStudent(profile);
        status.setAcademicYear(YEAR);
        status.setCategory(StudentCategory.K3);
        status.setValidFrom(SNAPSHOT_DATE);
        when(supportStatusRepository.findAllByAcademicYear(YEAR)).thenReturn(List.of(status));

        ManualLoadEntry load = new ManualLoadEntry();
        load.setId(71L);
        load.setAcademicYear(YEAR);
        load.setClassId(5L);
        load.setClassName("5-А");
        load.setSubjectName("Иностранный язык");
        load.setGroupNameEducationalPlan("Группа 1");

        StudentDataExchangeService.StudentCountResolution result =
                service.resolveStudentCounts(YEAR, List.of(load));

        assertTrue(result.contingentMode());
        assertEquals(3, result.childrenByLoadEntry().get(71L));
    }

    private int column(Sheet sheet, String name) {
        for (Cell cell : sheet.getRow(0)) {
            if (name.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Колонка не найдена: " + name);
    }

    private void set(Row row, int column, String value) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
        }
        cell.setCellValue(value);
    }

    private void clear(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell != null) {
            cell.setBlank();
        }
    }
}
