package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.MckoDtos;
import org.school.personalLoad.model.MckoCertificate;
import org.school.personalLoad.model.MckoCertificateSource;
import org.school.personalLoad.model.MckoSubjectMapping;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

        when(teacherRepository.findAll()).thenReturn(List.of(teacher));
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

        when(teacherRepository.findAll()).thenReturn(List.of(teacher));
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

        when(teacherRepository.findAll()).thenReturn(List.of(teacher));
        when(mappingRepository.findAll()).thenReturn(List.of(mapping));
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<MckoDtos.EligibilityRow> rows = service.eligibility("2026/2027");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("MISSING");
        assertThat(rows.get(0).message()).isEqualTo("НЕТ МЦКО");
        assertThat(rows.get(0).subjectId()).isEqualTo(10L);
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
}
