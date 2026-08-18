package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.ContingentImportIssue;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.StudentIdentityMatchStatus;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentImportIssueRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.service.ClassSizeService;
import org.school.personalLoad.service.StudentIdentityService;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContingentServiceImplExportTest {

    @Mock
    private ContingentSnapshotRepository snapshotRepository;
    @Mock
    private ContingentStudentRepository studentRepository;
    @Mock
    private ContingentImportIssueRepository importIssueRepository;
    @Mock
    private ClassroomLeadershipRepository classroomLeadershipRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private ClassSizeService classSizeService;
    @Mock
    private StudentIdentityService studentIdentityService;

    private ContingentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContingentServiceImpl(
                snapshotRepository,
                studentRepository,
                importIssueRepository,
                classroomLeadershipRepository,
                curriculumPlanEntryRepository,
                schoolBuildingRepository,
                studentProfileRepository,
                classSizeService,
                studentIdentityService
        );
    }

    @Test
    void exportStatsCreatesBuildingAndAddressSheets() throws Exception {
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(42L);
        snapshot.setAcademicYear("2025/2026");
        snapshot.setSnapshotDate(LocalDate.of(2025, 9, 1));
        snapshot.setSourceFileName("contingent.xlsx");
        snapshot.setTotalStudents(5);

        ClassroomLeadershipEntry first = classEntry("СП1", "1-А", "ул. Общая, 1");
        ClassroomLeadershipEntry second = classEntry("СП2", "2-А", "ул. Общая, 1");
        ClassroomLeadershipEntry third = classEntry("СП2", "3-А", "ул. Другая, 2");

        when(snapshotRepository.findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc("2025/2026", snapshot.getSnapshotDate()))
                .thenReturn(Optional.of(snapshot));
        when(studentRepository.findClassNamesBySnapshotId(42L)).thenReturn(List.of("1-А", "1-А", "2-А", "2-А", "2-А"));
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(first, second, third));
        when(schoolBuildingRepository.findByCode("СП1")).thenReturn(Optional.empty());
        when(schoolBuildingRepository.findByCode("СП2")).thenReturn(Optional.empty());

        byte[] body = service.exportStats("2025/2026", snapshot.getSnapshotDate());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("По СП"));
            assertNotNull(workbook.getSheet("Детский сад"));
            var addressSheet = workbook.getSheet("По адресам");
            assertNotNull(addressSheet);
            assertNotNull(addressSheet.getRow(1).getCell(2));
            assertNotNull(addressSheet.getRow(1).getCell(3));
            assertNotNull(addressSheet.getRow(1).getCell(5));
            assertEquals(3D, addressSheet.getRow(5).getCell(2).getNumericCellValue(), 0.01);
            assertEquals(30D, addressSheet.getRow(5).getCell(4).getNumericCellValue(), 0.01);
            assertEquals(5D, addressSheet.getRow(5).getCell(6).getNumericCellValue(), 0.01);
        }
    }

    @Test
    void compactMeshExportWithoutHeadersIsImportedAndClassified() throws Exception {
        byte[] source;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Лист1");
            row(sheet, 0, "Иванов Иван Иванович", "2-А");
            row(sheet, 1, "Петрова Анна Сергеевна", "Старшая группа 16А");
            row(sheet, 2, "Сидоров Пётр Андреевич", "ГКП 88А");
            row(sheet, 3, "Орлов Артём Олегович", "Вне ОО Орлов Артём Олегович");
            workbook.write(out);
            source = out.toByteArray();
        }

        AtomicReference<ContingentSnapshot> savedSnapshot = new AtomicReference<>();
        AtomicReference<List<ContingentStudent>> savedStudents = new AtomicReference<>(List.of());
        when(snapshotRepository.save(any(ContingentSnapshot.class))).thenAnswer(invocation -> {
            ContingentSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(77L);
            savedSnapshot.set(snapshot);
            return snapshot;
        });
        when(snapshotRepository.findById(77L)).thenAnswer(ignored -> Optional.ofNullable(savedSnapshot.get()));
        when(studentIdentityService.linkStudents(any(ContingentSnapshot.class), anyList()))
                .thenReturn(new StudentIdentityService.LinkResult(2, 2, 0));
        when(studentRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ContingentStudent> students = new ArrayList<>(invocation.getArgument(0));
            savedStudents.set(students);
            return students;
        });
        when(studentRepository.findClassNamesBySnapshotId(77L)).thenAnswer(ignored ->
                savedStudents.get().stream().map(ContingentStudent::getClassName).toList());
        when(curriculumPlanEntryRepository.findAll()).thenReturn(List.of());

        var response = service.importSnapshot(
                "2025/2026",
                new MockMultipartFile("file", "Учащиеся.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", source)
        );

        assertEquals("COMPACT", response.getImportFormat());
        assertEquals(4, response.getImportedStudents());
        assertEquals(1, response.getSchoolStudents());
        assertEquals(2, response.getKindergartenStudents());
        assertEquals(1, response.getUnassignedStudents());
        assertEquals(4, savedStudents.get().size());
        assertTrue(savedStudents.get().stream().allMatch(student -> student.getBirthDate().isEmpty()));
    }

    @Test
    void extendedMeshCsvIsImportedWithIdentityAndRepresentativeData() {
        String source = "\uFEFF\"ФИО ребёнка\";\"Дата рождения\";\"Возраст\";\"Пол\";\"Класс / группа\";"
                + "\"Логин ребёнка\";\"Email ребёнка\";\"Телефон ребёнка\";\"СНИЛС ребёнка\";"
                + "\"Классный руководитель / наставник\";\"Представитель 1 — тип\";\"Представитель 1 — ФИО\";"
                + "\"Представитель 1 — логин\";\"Представитель 1 — телефон\";\"Представитель 1 — email\";"
                + "\"Представитель 1 — СНИЛС\"\r\n"
                + "\"Иванов Иван Иванович\";\"01.02.2018\";\"8\";\"М\";\"3-А\";\"ivanovii\";"
                + "\"child@example.ru\";\"9001002030\";\"123-456-789 01\";\"Учитель Тестовый\";\"Родитель\";"
                + "\"Петрова \"\"Мама\"\" Мария Сергеевна\";\"petrovams\";\"9112223344\";\"parent@example.ru\";"
                + "\"111-222-333 44\"\r\n"
                + "\"Сидорова Анна Игоревна\";\"10.03.2021\";\"5\";\"Ж\";\"Старшая группа 16А\";"
                + "\"sidorovaai\";\"\";\"\";\"987-654-321 00\";\"Наставник Тестовый\";\"\";\"\";\"\";\"\";\"\";\"\"";

        AtomicReference<ContingentSnapshot> savedSnapshot = new AtomicReference<>();
        AtomicReference<List<ContingentStudent>> savedStudents = new AtomicReference<>(List.of());
        when(snapshotRepository.save(any(ContingentSnapshot.class))).thenAnswer(invocation -> {
            ContingentSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(103L);
            savedSnapshot.set(snapshot);
            return snapshot;
        });
        when(snapshotRepository.findById(103L)).thenAnswer(ignored -> Optional.ofNullable(savedSnapshot.get()));
        when(studentIdentityService.linkStudents(any(ContingentSnapshot.class), anyList()))
                .thenReturn(new StudentIdentityService.LinkResult(1, 1, 0));
        when(studentRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ContingentStudent> students = new ArrayList<>(invocation.getArgument(0));
            savedStudents.set(students);
            return students;
        });
        when(studentRepository.findClassNamesBySnapshotId(103L)).thenAnswer(ignored ->
                savedStudents.get().stream().map(ContingentStudent::getClassName).toList());
        when(curriculumPlanEntryRepository.findAll()).thenReturn(List.of());

        var response = service.importSnapshot(
                "2026/2027",
                new MockMultipartFile("file", "MES_контингент_936_2026-08-16.csv", "text/csv",
                        source.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );

        assertEquals("MES_EXTENDED_CSV", response.getImportFormat());
        assertEquals(LocalDate.of(2026, 8, 16), response.getSnapshotDate());
        assertEquals(2, response.getImportedStudents());
        assertEquals(1, response.getSchoolStudents());
        assertEquals(1, response.getKindergartenStudents());
        assertEquals(0, response.getUnassignedStudents());
        ContingentStudent first = savedStudents.get().get(0);
        assertEquals("01.02.2018", first.getBirthDate());
        assertEquals("М", first.getGender());
        assertEquals("9001002030", first.getPhone());
        assertEquals("child@example.ru", first.getEmail());
        assertEquals("123-456-789 01", first.getPensionInsurance());
        assertEquals("Петрова \"Мама\" Мария Сергеевна", first.getRepresentativeName());
        assertEquals("9112223344", first.getRepresentativePhone());
        assertTrue(first.getRawPayload().contains("Петрова \\\"Мама\\\" Мария Сергеевна"));
        assertTrue(first.getRawPayload().contains("Представитель 1 — телефон"));
        assertTrue(first.getRawPayload().contains("9112223344"));
    }

    @Test
    void statsReportKindergartenGroupsSeparatelyFromSchoolClasses() {
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(91L);
        snapshot.setAcademicYear("2025/2026");
        snapshot.setSnapshotDate(LocalDate.of(2025, 9, 2));
        snapshot.setSourceFileName("compact.xlsx");
        snapshot.setTotalStudents(6);
        when(snapshotRepository.findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc(
                "2025/2026", snapshot.getSnapshotDate())).thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findById(91L)).thenReturn(Optional.of(snapshot));
        when(studentRepository.findClassNamesBySnapshotId(91L)).thenReturn(List.of(
                "2-А", "2-А", "Старшая группа 16А", "ГКП 88А", "ГКП 88А", "Вне ОО Ребёнок"
        ));
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of());
        when(curriculumPlanEntryRepository.findAll()).thenReturn(List.of());

        var stats = service.getStats("2025/2026", snapshot.getSnapshotDate());

        assertEquals(6, stats.getTotalImportedChildren());
        assertEquals(2, stats.getTotalSchoolChildren());
        assertEquals(3, stats.getTotalKindergartenChildren());
        assertEquals(1, stats.getTotalUnassignedChildren());
        assertEquals(2, stats.getKindergartenGroups().size());
        assertEquals(2, stats.getTotalStudents());
        var problems = service.getProblems("2025/2026", 91L);
        assertEquals(1, problems.size());
        assertEquals("2-А", problems.get(0).getClassName());
    }

    @Test
    void mismatchRegisterShowsOutsideAmbiguousAndSkippedRows() {
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(120L);
        snapshot.setAcademicYear("2026/2027");
        snapshot.setSnapshotDate(LocalDate.of(2026, 8, 16));
        snapshot.setSourceFileName("MES.csv");
        snapshot.setImportFormat("MES_EXTENDED_CSV");

        ContingentStudent outside = new ContingentStudent();
        outside.setId(1L);
        outside.setSnapshotId(120L);
        outside.setStudentId(501L);
        outside.setIdentityMatchStatus(StudentIdentityMatchStatus.CREATED);
        outside.setFullName("Иванов Иван Иванович");
        outside.setBirthDate("01.02.2018");
        outside.setClassName("Вне ОО");
        outside.setRawPayload("{}");

        ContingentStudent ambiguous = new ContingentStudent();
        ambiguous.setId(2L);
        ambiguous.setSnapshotId(120L);
        ambiguous.setIdentityMatchStatus(StudentIdentityMatchStatus.AMBIGUOUS);
        ambiguous.setFullName("Петров Алексей Сергеевич");
        ambiguous.setBirthDate("");
        ambiguous.setClassName("2-А");
        ambiguous.setRawPayload("{}");

        ContingentImportIssue skipped = new ContingentImportIssue();
        skipped.setId(3L);
        skipped.setSnapshotId(120L);
        skipped.setSourceRowNumber(17);
        skipped.setIssueType("SKIPPED_ROW");
        skipped.setMessage("Строка пропущена: не заполнено ФИО");
        skipped.setFullName("");
        skipped.setPlacementName("2-Б");
        skipped.setRawPayload("{\"Класс\":\"2-Б\"}");

        CurriculumPlanEntry planClass = new CurriculumPlanEntry();
        planClass.setAcademicYear("2026/2027");
        planClass.setClassName("2-А");
        when(snapshotRepository.findById(120L)).thenReturn(Optional.of(snapshot));
        when(studentRepository.findAllBySnapshotId(120L)).thenReturn(List.of(outside, ambiguous));
        when(importIssueRepository.findAllBySnapshotIdOrderBySourceRowNumberAscIdAsc(120L)).thenReturn(List.of(skipped));
        when(curriculumPlanEntryRepository.findAll()).thenReturn(List.of(planClass));
        when(classroomLeadershipRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(studentProfileRepository.findAll()).thenReturn(List.of());

        var result = service.getImportMismatches("2026/2027", 120L);

        assertEquals(3, result.getTotal());
        assertEquals(1, result.getOutsideOrganization());
        assertEquals(1, result.getAmbiguousIdentity());
        assertEquals(1, result.getSkippedRows());
        assertEquals("OUTSIDE_ORGANIZATION", result.getRows().get(0).getType());
        assertTrue(result.getRows().get(0).isCanResolve());
        assertEquals(17, result.getRows().get(2).getSourceRowNumber());
    }

    private void row(org.apache.poi.ss.usermodel.Sheet sheet, int index, String fullName, String placement) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(fullName);
        row.createCell(1).setCellValue(placement);
    }

    private ClassroomLeadershipEntry classEntry(String building, String className, String address) {
        ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
        entry.setAcademicYear("2025/2026");
        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setClassDirection("общеобразовательный");
        entry.setFioTeacher("Классный руководитель");
        entry.setCampusAddress(address);
        return entry;
    }
}
