package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.MckoDtos;
import org.school.personalLoad.model.MckoCertificate;
import org.school.personalLoad.model.MckoCertificateSource;
import org.school.personalLoad.model.MckoImportBatch;
import org.school.personalLoad.model.MckoSubjectMapping;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.*;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MckoServiceImplTest {

    @Mock
    private MckoCertificateRepository certificateRepository;
    @Mock
    private MckoImportBatchRepository importBatchRepository;
    @Mock
    private MckoSubjectMappingRepository mappingRepository;
    @Mock
    private TeacherDirectoryRepository teacherRepository;
    @Mock
    private SubjectCatalogRepository subjectRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadRepository;

    private MckoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MckoServiceImpl(certificateRepository, importBatchRepository, mappingRepository,
                teacherRepository, subjectRepository, manualLoadRepository);
    }

    @Test
    void eligibilityChoosesBestActiveCertificateAcrossManualAndImport() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping mapping = mapping("Математика профильная", 10L, "Алгебра");
        LocalDate today = LocalDate.now();
        MckoCertificate importedHigh = certificate(teacher, "Математика профильная", "Высокий",
                true, today.minusMonths(1), MckoCertificateSource.IMPORT);
        MckoCertificate manualExpertUnpublished = certificate(teacher, "Математика профильная", "Экспертный",
                false, today.minusMonths(2), MckoCertificateSource.MANUAL);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 10L, "Алгебра", "7-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of(importedHigh, manualExpertUnpublished));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        MckoDtos.EligibilityRow row = rows.get(0);
        assertThat(row.status()).isEqualTo("WARNING");
        assertThat(row.level()).isEqualTo("Экспертный");
        assertThat(row.message()).contains("результат не опубликован");
        assertThat(row.diagnosticDate()).isEqualTo(today.minusMonths(2));
    }

    @Test
    void eligibilityIgnoresExpiredExpertAndUsesActiveHigh() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping mapping = mapping("Математика профильная", 10L, "Алгебра");
        LocalDate today = LocalDate.now();
        MckoCertificate expiredExpert = certificate(teacher, "Математика профильная", "Экспертный",
                true, today.minusYears(4), MckoCertificateSource.IMPORT);
        MckoCertificate activeHigh = certificate(teacher, "Математика профильная", "Высокий",
                true, today.minusMonths(1), MckoCertificateSource.MANUAL);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 10L, "Алгебра", "7-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of(expiredExpert, activeHigh));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("OK");
        assertThat(rows.get(0).level()).isEqualTo("Высокий");
        assertThat(rows.get(0).diagnosticDate()).isEqualTo(today.minusMonths(1));
    }

    @Test
    void eligibilityReportsMissingForConfiguredSubjectWithoutActiveCertificate() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping mapping = mapping("Математика профильная", 10L, "Алгебра");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 10L, "Алгебра", "7-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("MISSING");
        assertThat(rows.get(0).message()).isEqualTo("НЕТ МЦКО");
        assertThat(rows.get(0).subjectName()).isEqualTo("Математика");
    }

    @Test
    void algebraAndCalculusIsGroupedAsMath() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping mapping = mapping("Математика профильная", 40L, "Алгебра и начала математического анализа");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(teacher, 40L, "Алгебра и начала математического анализа", "10-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).subjectName()).isEqualTo("Математика");
        assertThat(rows.get(0).status()).isEqualTo("MISSING");
    }

    @Test
    void primaryMetaSubjectAllowsAnyCoreSubjectInGradesOneToFour() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        LocalDate today = LocalDate.now();
        MckoCertificate meta = certificate(teacher, "Метапредметные умения (начальное образование)", "Высокий",
                true, today.minusMonths(1), MckoCertificateSource.IMPORT);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 20L, "Русский язык", "2-Б")));
        when(mappingRepository.findAll()).thenReturn(List.of());
        when(certificateRepository.findAll()).thenReturn(List.of(meta));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("OK");
        assertThat(rows.get(0).subjectName()).isEqualTo("Начальная школа");
    }

    @Test
    void coreSubjectWithoutMckoMappingIsNotChecked() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(teacher, 30L, "Индивидуальный проект", "10-А")));
        when(mappingRepository.findAll()).thenReturn(List.of());
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).isEmpty();
    }

    @Test
    void importAcceptsRealExportHeaderAndExcelSerialDate() throws Exception {
        TeacherDirectoryEntry teacher = teacher(1L, "Алфёров Александр Викторович");
        when(teacherRepository.findAll()).thenReturn(List.of(teacher));
        when(importBatchRepository.save(any(MckoImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(certificateRepository.findFirstByTeacherIdAndMckoSubjectIgnoreCaseAndDiagnosticDateAndExamTypeIgnoreCaseAndSource(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(certificateRepository.save(any(MckoCertificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "mcko.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes());

        MckoDtos.ImportResult result = service.importCertificates(file);

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).isZero();
        ArgumentCaptor<MckoCertificate> captor = ArgumentCaptor.forClass(MckoCertificate.class);
        org.mockito.Mockito.verify(certificateRepository).save(captor.capture());
        MckoCertificate saved = captor.getValue();
        assertThat(saved.getTeacherId()).isEqualTo(1L);
        assertThat(saved.getMckoSubject()).isEqualTo("Математика (профильный уровень)");
        assertThat(saved.getDiagnosticDate()).isEqualTo(LocalDate.of(2025, 11, 29));
        assertThat(saved.isPublished()).isTrue();
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private MckoSubjectMapping mapping(String mckoSubject, Long subjectId, String subjectName) {
        MckoSubjectMapping mapping = new MckoSubjectMapping();
        mapping.setMckoSubject(mckoSubject);
        mapping.setSubjectId(subjectId);
        mapping.setSubjectName(subjectName);
        return mapping;
    }

    private MckoCertificate certificate(TeacherDirectoryEntry teacher, String mckoSubject, String level,
                                        boolean published, LocalDate diagnosticDate, MckoCertificateSource source) {
        MckoCertificate certificate = new MckoCertificate();
        certificate.setTeacherId(teacher.getId());
        certificate.setTeacherFioSnapshot(teacher.getFioTeacher());
        certificate.setMckoSubject(mckoSubject);
        certificate.setExamType("Комплексная диагностика ЕГЭ");
        certificate.setDiagnosticDate(diagnosticDate);
        certificate.setExpiresAt(diagnosticDate.plusYears(3));
        certificate.setLevel(level);
        certificate.setPublished(published);
        certificate.setSource(source);
        return certificate;
    }

    private ManualLoadEntry load(TeacherDirectoryEntry teacher, Long subjectId, String subjectName, String className) {
        SubjectCatalogEntry subject = new SubjectCatalogEntry();
        subject.setId(subjectId);
        subject.setSubjectName(subjectName);
        subject.setSubjectType(SubjectType.CORE);
        ManualLoadEntry load = new ManualLoadEntry();
        load.setAcademicYear("2026/2027");
        load.setTeacherId(teacher.getId());
        load.setFioTeacher(teacher.getFioTeacher());
        load.setSubject(subject);
        load.setSubjectName(subjectName);
        load.setClassName(className);
        load.setCurriculumPart(CurriculumPart.CORE);
        load.setEducationLevel(EducationLevel.BASIC);
        load.setNumberSchoolBuilding("СП1");
        load.setLoad(1);
        return load;
    }

    private byte[] workbookBytes() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row header = workbook.createSheet("mcko").createRow(0);
            String[] headers = {"ФИО", "Дата диагностики", "Тип экзамена", "Предмет", "Достигнутый уровень", "Публикация результатов"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = workbook.getSheetAt(0).createRow(1);
            row.createCell(0).setCellValue("Алфёров Александр Викторович");
            row.createCell(1).setCellValue(45990);
            row.createCell(2).setCellValue("Комплексная диагностика ЕГЭ (29.11.2025 - 13.12.2025)");
            row.createCell(3).setCellValue("Математика (профильный уровень)");
            row.createCell(4).setCellValue("Экспертный");
            row.createCell(5).setCellValue("Опубликован");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
