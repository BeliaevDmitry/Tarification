package org.school.personalLoad.vsoko.mcko;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentNameHistory;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.pa.analytics.model.PaReportStudentResult;
import org.school.personalLoad.pa.analytics.model.PaStudentResultStatus;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentNameHistoryRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.vsoko.mcko.dto.VsokoMckoDtos;
import org.school.personalLoad.vsoko.mcko.model.MckoParticipantRosterEntry;
import org.school.personalLoad.vsoko.mcko.model.MckoStudentResult;
import org.school.personalLoad.vsoko.mcko.repository.MckoImportFileRepository;
import org.school.personalLoad.vsoko.mcko.repository.MckoParticipantRosterRepository;
import org.school.personalLoad.vsoko.mcko.repository.MckoStudentResultRepository;
import org.school.personalLoad.vsoko.mcko.service.MckoLegacyPdfParser;
import org.school.personalLoad.vsoko.mcko.service.MckoParticipantRosterParser;
import org.school.personalLoad.vsoko.mcko.service.StudentResultLinker;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoImportService;
import org.school.personalLoad.vsoko.mcko.service.VsokoMckoQueryService;
import org.school.personalLoad.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({
        VsokoMckoImportService.class,
        VsokoMckoQueryService.class,
        StudentResultLinker.class,
        MckoParticipantRosterParser.class,
        MckoLegacyPdfParser.class,
        VsokoMckoEndToEndPersistenceTest.JsonConfiguration.class
})
class VsokoMckoEndToEndPersistenceTest {
    private static final String YEAR = "2024/2025";
    private static final String CLASS_NAME = "7-А";
    private static final String SUBJECT = "Математика";
    private static final LocalDate WORK_DATE = LocalDate.of(2025, 3, 11);

    @Autowired private VsokoMckoImportService importService;
    @Autowired private VsokoMckoQueryService queryService;
    @Autowired private StudentProfileRepository profileRepository;
    @Autowired private StudentNameHistoryRepository nameHistoryRepository;
    @Autowired private StudentClassEnrollmentRepository enrollmentRepository;
    @Autowired private MckoParticipantRosterRepository rosterRepository;
    @Autowired private MckoStudentResultRepository resultRepository;
    @Autowired private MckoImportFileRepository fileRepository;
    @Autowired private PaReportStudentResultRepository paRepository;
    @Autowired private TeacherDirectoryRepository teacherRepository;

    @Test
    void keepsMckoAndPaOnPermanentStudentFkAndAssignsTeacherBeforeExport() throws Exception {
        StudentProfile student = student();
        MckoParticipantRosterEntry roster = roster(student);
        TeacherDirectoryEntry teacher = teacherRepository.save(teacher());
        PaReportStudentResult pa = paRepository.save(pa(roster, teacher));

        MockMultipartFile file = new MockMultipartFile("files", "legacy-mcko.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", legacyWorkbook());
        VsokoMckoDtos.ImportResponse imported = importService.importFiles(YEAR, List.of(file), "e2e-test");

        assertEquals(1, imported.filesProcessed());
        assertEquals(0, imported.filesFailed());
        MckoStudentResult mcko = resultRepository.findAll().get(0);
        assertEquals(student.getId(), mcko.getStudentId());
        assertEquals(roster.getStudentCode(), mcko.getStudentCode());
        assertEquals(roster.getStudentFio(), mcko.getStudentFioSnapshot());

        queryService.reconcile();
        pa = paRepository.findById(pa.getId()).orElseThrow();
        assertEquals(student.getId(), pa.getStudentId());
        assertEquals(1, queryService.autofillAssignments(YEAR));
        mcko = resultRepository.findById(mcko.getId()).orElseThrow();
        assertEquals(teacher.getId(), mcko.getTeacherId());

        VsokoMckoDtos.StudentSummary studentSummary = queryService.studentSummary(student.getId());
        assertEquals(2, studentSummary.results().size());
        assertTrue(studentSummary.knownNames().contains(roster.getStudentFio()));
        VsokoMckoDtos.ClassSubjectComparison comparison = queryService.classSummary(YEAR, CLASS_NAME)
                .subjects().stream().filter(row -> SUBJECT.equals(row.subjectName())).findFirst().orElseThrow();
        assertEquals(1, comparison.mckoCount());
        assertEquals(1, comparison.paCount());

        assertWorkbook(queryService.exportStudentSummary(student.getId()), 2, 2);
        assertWorkbook(queryService.exportClassSummary(YEAR, CLASS_NAME), 1, 2);
        assertWorkbook(queryService.exportResults(YEAR, CLASS_NAME, SUBJECT, "", "", ""), 5, 2);
        assertWorkbook(queryService.exportAssignments(YEAR), 1, 2);

        assertEquals(1, fileRepository.count());
        var history = fileRepository.findAll().get(0);
        assertEquals(YEAR, history.getDetectedAcademicYear());
        assertEquals("11.03.2025", history.getDetectedWorkDate());
        assertEquals(SUBJECT, history.getDetectedSubject());
    }

    private StudentProfile student() {
        StudentProfile student = new StudentProfile();
        student.setCurrentFullName("Петрова Анна Сергеевна");
        student.setNormalizedFullName(StudentResultLinker.normalizeName(student.getCurrentFullName()));
        student.setRecordNumber("9116-0001");
        student.setNormalizedRecordNumber(StudentResultLinker.normalizeCode(student.getRecordNumber()));
        student = profileRepository.save(student);

        StudentNameHistory oldName = new StudentNameHistory();
        oldName.setStudent(student);
        oldName.setFullName("Иванова Анна Сергеевна");
        oldName.setNormalizedFullName(StudentResultLinker.normalizeName(oldName.getFullName()));
        oldName.setValidFrom(LocalDate.of(2024, 9, 1));
        oldName.setValidTo(LocalDate.of(2025, 1, 31));
        nameHistoryRepository.save(oldName);

        StudentClassEnrollment enrollment = new StudentClassEnrollment();
        enrollment.setStudent(student);
        enrollment.setAcademicYear(YEAR);
        enrollment.setClassName(CLASS_NAME);
        enrollment.setValidFrom(LocalDate.of(2024, 9, 1));
        enrollmentRepository.save(enrollment);
        return student;
    }

    private MckoParticipantRosterEntry roster(StudentProfile student) {
        MckoParticipantRosterEntry row = new MckoParticipantRosterEntry();
        row.setStudentId(student.getId());
        row.setStudentFio("Иванова Анна Сергеевна");
        row.setStudentCode("9116-0001");
        row.setStudentNumber(1);
        row.setClassName(CLASS_NAME);
        row.setSubjectName(SUBJECT);
        row.setWorkDate(WORK_DATE);
        row.setAcademicYear(YEAR);
        row.setFingerprint("a".repeat(64));
        return rosterRepository.save(row);
    }

    private TeacherDirectoryEntry teacher() {
        TeacherDirectoryEntry row = new TeacherDirectoryEntry();
        row.setFioTeacher("Смирнов Сергей Петрович");
        row.setInitials("Смирнов С.П.");
        return row;
    }

    private PaReportStudentResult pa(MckoParticipantRosterEntry roster, TeacherDirectoryEntry teacher) {
        PaReportStudentResult row = new PaReportStudentResult();
        row.setReportVersionId(1L);
        row.setAcademicYear(YEAR);
        row.setSubjectName(SUBJECT);
        row.setClassName(CLASS_NAME);
        row.setTeacherFio(teacher.getFioTeacher());
        row.setStudentFio(roster.getStudentFio());
        row.setStudentFioNormalized(StudentResultLinker.normalizeName(roster.getStudentFio()));
        row.setTotalScore(17D);
        row.setMaxScore(20D);
        row.setPercent(85D);
        row.setMark(5);
        row.setHasResult(true);
        row.setRowStatus(PaStudentResultStatus.PRESENT_WITH_RESULT);
        return row;
    }

    private byte[] legacyWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Лист 1");
            sheet.createRow(0).createCell(0).setCellValue("Рабочая форма для внутреннего пользования!");
            Row header = sheet.createRow(1);
            String[] headers = {"Школа", "Параллель", "Буква", "Предмет", "Дата", "Фамилия, имя", "№ уч.",
                    "Вариант", "1", "2", "3", "Балл", "% вып.", "Отметка"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            Row row = sheet.createRow(2);
            Object[] values = {"ГБОУ Школа № 7", 7, "А", SUBJECT, "11.03.2025", "", 1,
                    1001, 1, 0, 1, 2, 67, 4};
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Number number) row.createCell(i).setCellValue(number.doubleValue());
                else row.createCell(i).setCellValue(String.valueOf(values[i]));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void assertWorkbook(byte[] bytes, int minimumSheets, int minimumRows) throws Exception {
        assertTrue(bytes.length > 1_000);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertTrue(workbook.getNumberOfSheets() >= minimumSheets);
            assertTrue(workbook.getSheetAt(0).getLastRowNum() + 1 >= minimumRows);
            assertNotNull(workbook.getSheetAt(0).getRow(0));
        }
    }

    @TestConfiguration
    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        AppConfig appConfig() {
            AppConfig config = new AppConfig();
            config.setOutputDirectory("target/test-mcko-e2e-output");
            config.setKeepHistory(false);
            return config;
        }
    }
}
