package org.school.personalLoad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.contingent.StudentSupportDocumentDtos;
import org.school.personalLoad.model.StudentProfile;
import org.school.personalLoad.model.CorrectionSpecialistCatalogEntry;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.StudentSupportDocument;
import org.school.personalLoad.model.StudentSupportDocumentCorrection;
import org.school.personalLoad.model.StudentSupportDocumentForm;
import org.school.personalLoad.model.StudentSupportDocumentType;
import org.school.personalLoad.model.StudentSupportStatus;
import org.school.personalLoad.model.SupportEducationStage;
import org.school.personalLoad.repository.CorrectionSpecialistCatalogEntryRepository;
import org.school.personalLoad.repository.NosologyCatalogEntryRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.StudentSupportDocumentAttachmentRepository;
import org.school.personalLoad.repository.StudentSupportDocumentCorrectionRepository;
import org.school.personalLoad.repository.StudentSupportDocumentRepository;
import org.school.personalLoad.repository.StudentSupportStatusRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSupportDocumentServiceTest {

    @Mock
    private StudentSupportDocumentRepository documentRepository;
    @Mock
    private StudentSupportDocumentAttachmentRepository attachmentRepository;
    @Mock
    private StudentProfileRepository studentRepository;
    @Mock
    private StudentClassEnrollmentRepository enrollmentRepository;
    @Mock
    private StudentSupportStatusRepository supportStatusRepository;
    @Mock
    private NosologyCatalogEntryRepository nosologyRepository;
    @Mock
    private CorrectionSpecialistCatalogEntryRepository specialistRepository;
    @Mock
    private StudentSupportDocumentCorrectionRepository correctionRepository;

    private StudentSupportDocumentService service;
    private StudentProfile student;

    @BeforeEach
    void setUp() {
        service = new StudentSupportDocumentService(
                documentRepository,
                attachmentRepository,
                studentRepository,
                enrollmentRepository,
                supportStatusRepository,
                nosologyRepository,
                specialistRepository,
                correctionRepository
        );
        student = new StudentProfile();
        student.setId(1L);
        student.setCurrentFullName("Иванов Иван Иванович");
        lenient().when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        lenient().when(documentRepository.save(any(StudentSupportDocument.class))).thenAnswer(invocation -> {
            StudentSupportDocument document = invocation.getArgument(0);
            document.setId(10L);
            return document;
        });
        lenient().when(enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(1L, "2026/2027"))
                .thenReturn(List.of());
        lenient().when(supportStatusRepository.findBySourceDocumentId(10L)).thenReturn(Optional.empty());
        lenient().when(supportStatusRepository.save(any(StudentSupportStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(nosologyRepository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        lenient().when(correctionRepository.findAllByDocument_IdOrderBySpecialist_NameAsc(10L)).thenReturn(List.of());
    }

    @Test
    void mseAutomaticallyCreatesK2StatusAndDoesNotKeepUnusedNumber() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.MSE_CERTIFICATE);
        request.setAcceptedForm(StudentSupportDocumentForm.COPY);
        request.setIpraPresent(true);
        request.setDocumentNumber("МСЭ-1");
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2027, 8, 31));
        request.setReceivedAt(LocalDate.of(2026, 9, 2));

        StudentSupportDocumentDtos.View saved = service.save("2026/2027", request);

        assertEquals(10L, saved.getId());
        assertEquals(StudentSupportDocumentType.MSE_CERTIFICATE, saved.getDocumentType());
        assertEquals(StudentSupportDocumentForm.COPY, saved.getAcceptedForm());
        assertEquals(true, saved.isIpraPresent());
        assertEquals(null, saved.getDocumentNumber());
        org.mockito.ArgumentCaptor<StudentSupportStatus> captor =
                org.mockito.ArgumentCaptor.forClass(StudentSupportStatus.class);
        org.mockito.Mockito.verify(supportStatusRepository).save(captor.capture());
        assertEquals(StudentCategory.K2, captor.getValue().getCategory());
        assertEquals(10L, captor.getValue().getSourceDocumentId());
    }

    @Test
    void mseIgnoresNosologyAndAlwaysCreatesK2Status() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.MSE_CERTIFICATE);
        request.setAcceptedForm(StudentSupportDocumentForm.COPY);
        request.setNosologyCode("И4.1");
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2027, 8, 31));

        StudentSupportDocumentDtos.View saved = service.save("2026/2027", request);

        assertEquals(StudentCategory.K2, saved.getDerivedCategory());
        assertEquals(null, saved.getNosologyCode());
        org.mockito.ArgumentCaptor<StudentSupportStatus> captor =
                org.mockito.ArgumentCaptor.forClass(StudentSupportStatus.class);
        org.mockito.Mockito.verify(supportStatusRepository).save(captor.capture());
        assertEquals(StudentCategory.K2, captor.getValue().getCategory());
        assertEquals(null, captor.getValue().getNosologyCodeSnapshot());
    }

    @Test
    void mseRejectsOriginalAndElectronicCopy() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.MSE_CERTIFICATE);
        request.setAcceptedForm(StudentSupportDocumentForm.ORIGINAL);
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2027, 8, 31));

        IllegalArgumentException originalError = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );
        assertEquals("Справка МСЭ принимается только как копия", originalError.getMessage());

        request.setAcceptedForm(StudentSupportDocumentForm.ELECTRONIC_COPY);
        IllegalArgumentException electronicError = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );
        assertEquals("Справка МСЭ принимается только как копия", electronicError.getMessage());
    }

    @Test
    void cpmpcRequiresStandardStageEndDateWhenProlongationIsUnavailable() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.CPMPC_CONCLUSION);
        request.setAcceptedForm(StudentSupportDocumentForm.ORIGINAL);
        request.setDocumentNumber("ЦМПК-1");
        request.setNosologyCode("О5.2");
        request.setEducationStage(SupportEducationStage.NOO);
        request.setEducationProgram("Адаптированная программа для обучающихся с ЗПР");
        request.setEducationProgramCustom(true);
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2027, 7, 31));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );

        assertEquals("Срок документа ЦМПК должен оканчиваться 31.08", error.getMessage());
    }

    @Test
    void cpmpcRequiresNosologyAndRejectsPlainCopy() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.CPMPC_CONCLUSION);
        request.setAcceptedForm(StudentSupportDocumentForm.COPY);
        request.setDocumentNumber("ЦМПК-2");
        request.setEducationStage(SupportEducationStage.NOO);
        request.setEducationProgram("Программа вручную");
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2027, 8, 31));

        IllegalArgumentException copyError = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );
        assertEquals("Заключение ЦМПК принимается как оригинал или электронная копия", copyError.getMessage());

        request.setAcceptedForm(StudentSupportDocumentForm.ELECTRONIC_COPY);
        IllegalArgumentException nosologyError = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );
        assertEquals("Укажите нозологию для заключения ЦМПК", nosologyError.getMessage());
    }

    @Test
    void cpmpcStoresFreeTextProgramAndDoesNotOwnIpraFlag() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.CPMPC_CONCLUSION);
        request.setAcceptedForm(StudentSupportDocumentForm.ORIGINAL);
        request.setDocumentNumber("ЦМПК-3");
        request.setNosologyCode("И6.4");
        request.setEducationStage(SupportEducationStage.NOO);
        request.setEducationProgram("  Индивидуально сформулированная образовательная программа  ");
        request.setEducationProgramCustom(true);
        request.setIpraPresent(true);
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2027, 8, 31));

        StudentSupportDocumentDtos.View saved = service.save("2026/2027", request);

        assertEquals("Индивидуально сформулированная образовательная программа", saved.getEducationProgram());
        assertEquals(false, saved.isIpraPresent());
    }

    @Test
    void cpmpcRecommendationUsesSelectedProgramAndStoresCalculatedEndDate() {
        CorrectionSpecialistCatalogEntry specialist = new CorrectionSpecialistCatalogEntry();
        specialist.setId(7L);
        specialist.setName("Учитель-логопед");
        specialist.setActive(true);
        when(specialistRepository.findById(7L)).thenReturn(Optional.of(specialist));
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.CPMPC_RECOMMENDATION);
        request.setAcceptedForm(StudentSupportDocumentForm.ORIGINAL);
        request.setEducationStage(SupportEducationStage.NOO);
        request.setEducationProgram("Основная образовательная программа начального образования.");
        request.setValidTo(LocalDate.of(2027, 8, 31));
        request.setNosologyCode("И4.1");
        StudentSupportDocumentDtos.CorrectionDirectionRequest direction =
                new StudentSupportDocumentDtos.CorrectionDirectionRequest();
        direction.setSpecialistId(7L);
        direction.setTasks("Развитие письменной речи");
        request.setCorrectionDirections(List.of(direction));

        StudentSupportDocumentDtos.View saved = service.save("2026/2027", request);

        assertEquals(StudentSupportDocumentType.CPMPC_RECOMMENDATION, saved.getDocumentType());
        assertEquals(StudentSupportDocumentForm.COPY, saved.getAcceptedForm());
        assertEquals(SupportEducationStage.NOO, saved.getEducationStage());
        assertEquals("Основная образовательная программа начального образования.",
                saved.getEducationProgram());
        assertEquals(null, saved.getNosologyCode());
        assertEquals(null, saved.getValidFrom());
        assertEquals(LocalDate.of(2027, 8, 31), saved.getValidTo());
        assertEquals("ДЕЙСТВУЕТ", saved.getValidityStatus());
        org.mockito.ArgumentCaptor<StudentSupportDocumentCorrection> correctionCaptor =
                org.mockito.ArgumentCaptor.forClass(StudentSupportDocumentCorrection.class);
        org.mockito.Mockito.verify(correctionRepository).save(correctionCaptor.capture());
        assertEquals("Учитель-логопед", correctionCaptor.getValue().getSpecialist().getName());
        assertEquals("Развитие письменной речи", correctionCaptor.getValue().getTasks());
    }

    @Test
    void cpmpcRecommendationRequiresEducationLevel() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.CPMPC_RECOMMENDATION);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );

        assertEquals("Выберите уровень образования", error.getMessage());
    }

    @Test
    void cpmpcRecommendationRejectsProgramOutsideDirectory() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.CPMPC_RECOMMENDATION);
        request.setEducationStage(SupportEducationStage.NOO);
        request.setEducationProgram("Произвольная программа");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );

        assertEquals("Выберите образовательную программу из списка", error.getMessage());
    }

    @Test
    void educationDefaultsFollowCurrentSchoolLevelAndUnusedProlongation() {
        useCurrentClass("5-А");

        StudentSupportDocumentDtos.EducationDefaultsView regular = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_CONCLUSION, false, false
        );
        StudentSupportDocumentDtos.EducationDefaultsView prolonged = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_CONCLUSION, true, false
        );
        StudentSupportDocumentDtos.EducationDefaultsView alreadyUsed = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_CONCLUSION, true, true
        );

        assertEquals(SupportEducationStage.OOO, regular.getEducationStage());
        assertEquals(LocalDate.of(2031, 8, 31), regular.getValidTo());
        assertEquals(LocalDate.of(2032, 8, 31), prolonged.getValidTo());
        assertEquals(LocalDate.of(2031, 8, 31), alreadyUsed.getValidTo());
        assertEquals(7, regular.getEducationPrograms().size());
        assertEquals("АООП ООО обучающихся с РАС",
                regular.getEducationPrograms().get(6).getName());
        assertEquals(
                "https://drive.google.com/file/d/1kAe9k9ESzgjEYnhU6fMoHM0rOzTTSs5C/view?usp=sharing",
                regular.getEducationPrograms().get(6).getSourceUrl()
        );
    }

    @Test
    void educationDefaultsMapGradesToNooOooAndSoo() {
        useCurrentClass("2-Б");
        StudentSupportDocumentDtos.EducationDefaultsView noo = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_RECOMMENDATION, false, false
        );
        assertEquals(SupportEducationStage.NOO, noo.getEducationStage());
        assertEquals(LocalDate.of(2029, 8, 31), service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_RECOMMENDATION, false, false
        ).getValidTo());

        StudentSupportDocumentDtos.EducationDefaultsView nooConclusion = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_CONCLUSION, false, false
        );
        assertEquals(9, nooConclusion.getEducationPrograms().size());

        useCurrentClass("7-В");
        assertEquals(SupportEducationStage.OOO, service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_RECOMMENDATION, false, false
        ).getEducationStage());

        useCurrentClass("10-А");
        StudentSupportDocumentDtos.EducationDefaultsView soo = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_RECOMMENDATION, false, false
        );
        assertEquals(SupportEducationStage.SOO, soo.getEducationStage());
        assertEquals(LocalDate.of(2028, 8, 31), soo.getValidTo());
        assertEquals(0, soo.getEducationPrograms().size());
    }

    @Test
    void conclusionRequiresProgramFromCurrentLevelUnlessOtherIsSelected() {
        useCurrentClass("5-А");
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setStudentId(1L);
        request.setDocumentType(StudentSupportDocumentType.CPMPC_CONCLUSION);
        request.setAcceptedForm(StudentSupportDocumentForm.ORIGINAL);
        request.setDocumentNumber("ЦМПК-4");
        request.setNosologyCode("И6.4");
        request.setEducationStage(SupportEducationStage.OOO);
        request.setEducationProgram("АООП НОО обучающихся с РАС");
        request.setValidFrom(LocalDate.of(2026, 9, 1));
        request.setValidTo(LocalDate.of(2031, 8, 31));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );
        assertEquals("Выберите образовательную программу из списка для уровня OOO", error.getMessage());

        request.setEducationProgram("Индивидуальная программа по заключению ЦМПК");
        request.setEducationProgramCustom(true);
        StudentSupportDocumentDtos.View saved = service.save("2026/2027", request);
        assertEquals("Индивидуальная программа по заключению ЦМПК", saved.getEducationProgram());
    }

    @Test
    void preschoolDeadlineUsesYearChildTurnsSevenAndRequestsManualCheck() {
        student.setBirthDate(LocalDate.of(2020, 12, 15));

        StudentSupportDocumentDtos.EducationDefaultsView defaults = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_RECOMMENDATION, false, false
        );

        assertEquals(SupportEducationStage.DO, defaults.getEducationStage());
        assertEquals(LocalDate.of(2027, 8, 31), defaults.getValidTo());
        assertEquals(true, defaults.isManualCheckRequired());
        assertEquals(true, defaults.getMessage().contains("Проверьте её вручную"));
        assertEquals(0, defaults.getEducationPrograms().size());

        StudentSupportDocumentDtos.EducationDefaultsView conclusion = service.educationDefaults(
                "2026/2027", 1L, StudentSupportDocumentType.CPMPC_CONCLUSION, false, false
        );
        assertEquals(12, conclusion.getEducationPrograms().size());
        assertEquals("АООП дошкольного образования для детей с ТМНР",
                conclusion.getEducationPrograms().get(11).getName());
    }

    @Test
    void rejectsDocumentWithoutStudentAsUserInputError() {
        StudentSupportDocumentDtos.SaveRequest request = new StudentSupportDocumentDtos.SaveRequest();
        request.setDocumentType(StudentSupportDocumentType.MSE_CERTIFICATE);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.save("2026/2027", request)
        );

        assertEquals("Выберите ребёнка", error.getMessage());
    }

    private void useCurrentClass(String className) {
        StudentClassEnrollment enrollment = new StudentClassEnrollment();
        enrollment.setClassName(className);
        when(enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(1L, "2026/2027"))
                .thenReturn(List.of(enrollment));
    }
}
