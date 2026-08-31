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
import static org.mockito.Mockito.verify;
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
    void ogeDiagnosticIsStoredButDoesNotCountAsActiveMcko() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping mapping = mapping("Русский язык", 20L, "Русский язык", "5-11");
        MckoCertificate oge = certificate(teacher, "Русский язык", "Высокий",
                true, LocalDate.now().minusMonths(1), MckoCertificateSource.IMPORT);
        oge.setId(91L);
        oge.setExamType("Комплексная диагностика ОГЭ");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(teacher, 20L, "Русский язык", "9-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of(oge));

        List<MckoDtos.EligibilityRow> eligibility = service.eligibility("2026/2027");
        List<MckoDtos.OverviewRow> overview = service.overview("2026/2027");
        List<MckoDtos.CertificateRow> certificates = service.certificates("2026/2027", "all");

        assertThat(eligibility).hasSize(1);
        assertThat(eligibility.get(0).status()).isEqualTo("MISSING");
        assertThat(eligibility.get(0).message()).contains("ОГЭ не учитывается");
        assertThat(overview).hasSize(1);
        assertThat(overview.get(0).status()).isEqualTo("MISSING");
        assertThat(certificates).hasSize(1);
        assertThat(certificates.get(0).status()).isEqualTo("MISSING");
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
    void eligibilityReportsBasicLevelAsMissingWithLevelMessage() {
        TeacherDirectoryEntry teacher = teacher(1L, "Ярочкина Татьяна Ивановна");
        MckoSubjectMapping mapping = mapping("Математика профильная", 10L, "Математика");
        LocalDate today = LocalDate.now();
        MckoCertificate basic = certificate(teacher, "Математика профильная", "Базовый",
                true, today.minusMonths(1), MckoCertificateSource.IMPORT);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 10L, "Математика", "7-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of(basic));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("MISSING");
        assertThat(rows.get(0).message()).isEqualTo("МЦКО уровень Базовый");
        assertThat(rows.get(0).level()).isEqualTo("Базовый");
    }

    @Test
    void okEligibilityShowsExpirationMessage() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping mapping = mapping("Математика профильная", 10L, "Алгебра");
        LocalDate today = LocalDate.now();
        MckoCertificate activeHigh = certificate(teacher, "Математика профильная", "Высокий",
                true, today.minusMonths(1), MckoCertificateSource.MANUAL);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 10L, "Алгебра", "7-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of(activeHigh));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("OK");
        assertThat(rows.get(0).message()).contains("МЦКО до");
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
        assertThat(rows.get(0).subjectId()).isEqualTo(40L);
        assertThat(rows.get(0).subjectName()).isEqualTo("Математика");
        assertThat(rows.get(0).status()).isEqualTo("MISSING");
    }

    @Test
    void overviewCombinesCurriculumSubjectsThatRequireTheSameMcko() {
        TeacherDirectoryEntry teacher = teacher(1L, "Teacher One");
        MckoSubjectMapping algebra = mapping("Math profile", 10L, "Algebra");
        MckoSubjectMapping geometry = mapping("Math profile", 11L, "Geometry");
        MckoCertificate certificate = certificate(teacher, "Math profile", "Высокий",
                true, LocalDate.now().minusMonths(1), MckoCertificateSource.IMPORT);
        certificate.setId(77L);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(
                load(teacher, 10L, "Algebra", "7-A"),
                load(teacher, 11L, "Geometry", "7-A")
        ));
        when(mappingRepository.findAll()).thenReturn(List.of(algebra, geometry));
        when(certificateRepository.findAll()).thenReturn(List.of(certificate));

        List<MckoDtos.OverviewRow> rows = service.overview("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).certificateId()).isEqualTo(77L);
        assertThat(rows.get(0).curriculumSubjects()).contains("Algebra", "Geometry");
        assertThat(rows.get(0).status()).isEqualTo("OK");
    }

    @Test
    void overviewUsesOneBroadCertificateForSeveralMathSubjectsAndAlternativeMappings() {
        TeacherDirectoryEntry teacher = teacher(1L, "Васильев Михаил Юрьевич");
        MckoSubjectMapping plainGeometry = mapping("Математика", 11L, "Геометрия", "5-11");
        List<MckoSubjectMapping> profileMappings = List.of(
                mapping("Математика (профильный уровень)", 10L, "Математика", "5-11"),
                mapping("Математика (профильный уровень)", 11L, "Геометрия", "5-11"),
                mapping("Математика (профильный уровень)", 12L, "Алгебра", "5-11"),
                mapping("Математика (профильный уровень)", 13L, "Вероятность и статистика", "5-11")
        );
        MckoCertificate profileCertificate = certificate(teacher, "Математика (профильный уровень)", "Экспертный",
                true, LocalDate.now().minusMonths(1), MckoCertificateSource.IMPORT);
        profileCertificate.setId(88L);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(
                load(teacher, 10L, "Математика", "6-А"),
                load(teacher, 11L, "Геометрия", "8-А"),
                load(teacher, 12L, "Алгебра", "8-А"),
                load(teacher, 13L, "Вероятность и статистика", "8-А")
        ));
        when(mappingRepository.findAll()).thenReturn(java.util.stream.Stream.concat(
                java.util.stream.Stream.of(plainGeometry), profileMappings.stream()).toList());
        when(certificateRepository.findAll()).thenReturn(List.of(profileCertificate));

        List<MckoDtos.OverviewRow> rows = service.overview("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).certificateId()).isEqualTo(88L);
        assertThat(rows.get(0).mckoSubject()).isEqualTo("Математика (профильный уровень)");
        assertThat(rows.get(0).curriculumSubjects())
                .contains("Математика", "Геометрия", "Алгебра", "Вероятность и статистика");
        assertThat(rows.get(0).status()).isEqualTo("OK");
    }

    @Test
    void ignoreSubjectAtomicallyReplacesMappingsWithIgnoredMarker() {
        MckoSubjectMapping geometry = mapping("Математика", 11L, "Геометрия", "5-11");
        MckoSubjectMapping algebra = mapping("Математика", 12L, "Алгебра", "5-11");
        when(mappingRepository.findAllByMckoSubjectIgnoreCase("Математика")).thenReturn(List.of(geometry, algebra));
        when(mappingRepository.save(any(MckoSubjectMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MckoDtos.SubjectMappingRow result = service.ignoreSubject("Математика");

        verify(mappingRepository).deleteAll(List.of(geometry, algebra));
        verify(mappingRepository).flush();
        assertThat(result.ignored()).isTrue();
        assertThat(result.subjectId()).isNull();
    }

    @Test
    void overviewShowsMissingRequirementWithoutCertificate() {
        TeacherDirectoryEntry teacher = teacher(1L, "Teacher One");
        MckoSubjectMapping chemistry = mapping("Chemistry MCKO", 12L, "Chemistry");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(teacher, 12L, "Chemistry", "8-A")));
        when(mappingRepository.findAll()).thenReturn(List.of(chemistry));
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<MckoDtos.OverviewRow> rows = service.overview("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).certificateId()).isNull();
        assertThat(rows.get(0).status()).isEqualTo("MISSING");
        assertThat(rows.get(0).curriculumSubjects()).isEqualTo("Chemistry");
    }

    @Test
    void primaryMetaSubjectAllowsMappedCoreSubjectInGradesOneToFour() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        LocalDate today = LocalDate.now();
        MckoCertificate meta = certificate(teacher, "Метапредметные умения (начальное образование)", "Высокий",
                true, today.minusMonths(1), MckoCertificateSource.IMPORT);
        MckoSubjectMapping mapping = mapping("Метапредметные умения (начальное образование)", 20L, "Русский язык");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 20L, "Русский язык", "2-Б")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of(meta));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("OK");
        assertThat(rows.get(0).subjectName()).isEqualTo("Начальная школа");
    }

    @Test
    void primaryCoreSubjectWithoutPrimaryMappingIsNotChecked() {
        TeacherDirectoryEntry teacher = teacher(1L, "Хидирян Армен Кароевич");
        MckoSubjectMapping mapping = mapping("Метапредметные умения (начальное образование)", 20L, "Русский язык");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(teacher, 31L, "Музыка", "2-Б")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).isEmpty();
    }

    @Test
    void primaryAliasMappingUsesMetaSubjectCertificate() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        LocalDate today = LocalDate.now();
        MckoCertificate meta = certificate(teacher, "Метапредметные умения (начальное образование)", "Высокий",
                true, today.minusMonths(1), MckoCertificateSource.IMPORT);
        MckoSubjectMapping mapping = mapping("Начальная школа", 20L, "Русский язык");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(load(teacher, 20L, "Русский язык", "2-Б")));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of(meta));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("OK");
        assertThat(rows.get(0).subjectName()).isEqualTo("Начальная школа");
    }

    @Test
    void gradeBandLimitsMappingToSelectedClasses() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping primaryMath = mapping("Метапредметные умения (начальное образование)", 10L, "Математика", "1-4");
        MckoSubjectMapping secondaryMath = mapping("Математика профильная", 10L, "Математика", "5-11");
        LocalDate today = LocalDate.now();
        MckoCertificate primaryCert = certificate(teacher, "Метапредметные умения (начальное образование)", "Высокий",
                true, today.minusMonths(1), MckoCertificateSource.IMPORT);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(teacher, 10L, "Математика", "2-Б"), load(teacher, 10L, "Математика", "6-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(primaryMath, secondaryMath));
        when(certificateRepository.findAll()).thenReturn(List.of(primaryCert));

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.subjectName()).isEqualTo("Начальная школа");
            assertThat(row.status()).isEqualTo("OK");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.subjectName()).isEqualTo("Математика");
            assertThat(row.status()).isEqualTo("MISSING");
        });
    }

    @Test
    void secondaryRussianTeacherDoesNotRequireOrUsePrimarySchoolMcko() {
        TeacherDirectoryEntry teacher = teacher(1L, "Виноградова Дарья Александровна");
        MckoSubjectMapping primaryRussian = mapping("Начальная школа", 20L, "Русский язык", "ALL");
        MckoSubjectMapping secondaryRussian = mapping("Русский язык", 20L, "Русский язык", "ALL");
        MckoCertificate primaryCertificate = certificate(teacher,
                "Метапредметные умения (начальное образование)", "Высокий",
                true, LocalDate.now().minusMonths(1), MckoCertificateSource.IMPORT);

        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(
                load(teacher, 20L, "Русский язык", "7-А"),
                load(teacher, 20L, "Русский язык", "8-Б"),
                load(teacher, 20L, "Русский язык", "9-В")
        ));
        when(mappingRepository.findAll()).thenReturn(List.of(primaryRussian, secondaryRussian));
        when(certificateRepository.findAll()).thenReturn(List.of(primaryCertificate));

        List<MckoDtos.OverviewRow> overview = service.overview("2026/2027");
        List<MckoDtos.EligibilityRow> eligibility = service.eligibility("2026/2027");

        assertThat(overview).hasSize(1);
        assertThat(overview.get(0).mckoSubject()).isEqualTo("Русский язык");
        assertThat(overview.get(0).status()).isEqualTo("MISSING");
        assertThat(eligibility).hasSize(1);
        assertThat(eligibility.get(0).status()).isEqualTo("MISSING");
    }

    @Test
    void ignoredMckoSubjectIsNotChecked() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoSubjectMapping ignored = mapping("Тестовая диагностика", null, null);
        ignored.setIgnored(true);
        MckoSubjectMapping oldMapping = mapping("Тестовая диагностика", 10L, "Математика");

        when(manualLoadRepository.findAllByAcademicYear("2026/2027"))
                .thenReturn(List.of(load(teacher, 10L, "Математика", "7-А")));
        when(mappingRepository.findAll()).thenReturn(List.of(ignored, oldMapping));
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).isEmpty();
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

    @Test
    void loadModeDoesNotReturnCertificatesWhenSelectedYearHasNoLoad() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoCertificate certificate = certificate(teacher, "Математика профильная", "Высокий",
                true, LocalDate.now().minusMonths(1), MckoCertificateSource.IMPORT);
        when(manualLoadRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of());
        when(certificateRepository.findAll()).thenReturn(List.of(certificate));

        List<MckoDtos.CertificateRow> rows = service.certificates("2026/2027", "load");

        assertThat(rows).isEmpty();
    }

    @Test
    void allModeStillReturnsCertificatesWhenSelectedYearHasNoLoad() {
        TeacherDirectoryEntry teacher = teacher(1L, "Иванов Иван Иванович");
        MckoCertificate certificate = certificate(teacher, "Математика профильная", "Высокий",
                true, LocalDate.now().minusMonths(1), MckoCertificateSource.IMPORT);
        when(certificateRepository.findAll()).thenReturn(List.of(certificate));

        List<MckoDtos.CertificateRow> rows = service.certificates("2026/2027", "all");

        assertThat(rows).hasSize(1);
    }

    private TeacherDirectoryEntry teacher(Long id, String fio) {
        TeacherDirectoryEntry teacher = new TeacherDirectoryEntry();
        teacher.setId(id);
        teacher.setFioTeacher(fio);
        return teacher;
    }

    private MckoSubjectMapping mapping(String mckoSubject, Long subjectId, String subjectName) {
        return mapping(mckoSubject, subjectId, subjectName, "ALL");
    }

    private MckoSubjectMapping mapping(String mckoSubject, Long subjectId, String subjectName, String gradeBand) {
        MckoSubjectMapping mapping = new MckoSubjectMapping();
        mapping.setMckoSubject(mckoSubject);
        mapping.setSubjectId(subjectId);
        mapping.setSubjectName(subjectName);
        mapping.setGradeBand(gradeBand);
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
