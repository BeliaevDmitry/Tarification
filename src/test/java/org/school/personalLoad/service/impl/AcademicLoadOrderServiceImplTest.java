package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.AcademicLoadOrderDtos;
import org.school.personalLoad.model.AcademicLoadOrderType;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.CurriculumStage;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.repository.AcademicLoadOrderRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.StudyPeriodSettingService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicLoadOrderServiceImplTest {

    @Mock
    private AcademicLoadOrderRepository orderRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumRepository;
    @Mock
    private ManualLoadEntryRepository loadRepository;
    @Mock
    private TeacherDirectoryRepository teacherRepository;
    @Mock
    private AcademicYearService academicYearService;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;
    @Mock
    private AcademicLoadOrderDocumentService documentService;

    private AcademicLoadOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicLoadOrderServiceImpl(orderRepository, curriculumRepository, loadRepository,
                teacherRepository, academicYearService, studyPeriodSettingService, documentService);
    }

    @Test
    void separatesSemesterHoursCorrectsSecondHalfDatesAndRemovesExactDuplicates() {
        String year = "2026/2027";
        ManualLoadEntry firstHalf = load(1, StudyPeriod.H1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        ManualLoadEntry secondHalf = load(2, StudyPeriod.H2,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        ManualLoadEntry duplicatedSecondHalf = load(2, StudyPeriod.H2,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        when(academicYearService.resolveRequestedOrDefault(year)).thenReturn(year);
        when(loadRepository.findAllByAcademicYear(year))
                .thenReturn(List.of(firstHalf, secondHalf, duplicatedSecondHalf));
        when(studyPeriodSettingService.resolveDateRange(year, "4-К", StudyPeriod.H1))
                .thenReturn(new StudyPeriodSettingService.DateRange(
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));
        when(studyPeriodSettingService.resolveDateRange(year, "4-К", StudyPeriod.H2))
                .thenReturn(new StudyPeriodSettingService.DateRange(
                        LocalDate.of(2027, 1, 11), LocalDate.of(2027, 5, 31)));
        when(documentService.generate(any())).thenReturn(new byte[]{1, 2, 3});
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AcademicLoadOrderDtos.CreateRequest request = new AcademicLoadOrderDtos.CreateRequest(
                year, AcademicLoadOrderType.LOAD_APPROVAL, "1-ОД", LocalDate.of(2026, 8, 31),
                "", null, LocalDate.of(2026, 9, 1), "Иванова Ирина Ивановна", "Директор",
                "", "");
        SessionUser user = new SessionUser();
        user.setUsername("admin");

        service.create(request, "7", user);

        ArgumentCaptor<AcademicLoadOrderDocumentService.DocumentData> document =
                ArgumentCaptor.forClass(AcademicLoadOrderDocumentService.DocumentData.class);
        verify(documentService).generate(document.capture());
        List<AcademicLoadOrderDocumentService.LoadRow> rows = document.getValue().loadRows();

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.teacher()).isEqualTo("Конышева Галина Ибрагимовна");
            assertThat(row.subject()).isEqualTo("Занимательная математика юного москвича");
            assertThat(row.classes()).isEqualTo("4-К");
            assertThat(row.hours()).isEqualTo("1");
            assertThat(row.period()).isEqualTo("01.09.2026 — 31.12.2026");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.teacher()).isEqualTo("Конышева Галина Ибрагимовна");
            assertThat(row.subject()).isEqualTo("Занимательная математика юного москвича");
            assertThat(row.classes()).isEqualTo("4-К");
            assertThat(row.hours()).isEqualTo("2");
            assertThat(row.period()).isEqualTo("11.01.2027 — 31.05.2027");
        });
        assertThat(rows).filteredOn(row -> row.hours().equals("2"))
                .extracting(AcademicLoadOrderDocumentService.LoadRow::period)
                .containsExactly("11.01.2027 — 31.05.2027");
    }

    @Test
    void buildsParallelCurriculumRowsAndDoesNotDoubleLegacyLevelDuplicates() {
        String year = "2026/2027";
        CurriculumPlanEntry firstHalf = curriculum("10-А", "Профильная математика", StudyPeriod.H1, 2,
                EducationLevel.BASIC);
        CurriculumPlanEntry duplicatedAtAdvancedLevel = curriculum(
                "10-А", "Профильная математика", StudyPeriod.H1, 2, EducationLevel.ADVANCED);
        CurriculumPlanEntry secondHalf = curriculum("10-А", "Профильная математика", StudyPeriod.H2, 3,
                EducationLevel.ADVANCED);

        when(academicYearService.resolveRequestedOrDefault(year)).thenReturn(year);
        when(curriculumRepository.findAllByAcademicYear(year))
                .thenReturn(List.of(firstHalf, duplicatedAtAdvancedLevel, secondHalf));
        when(documentService.generate(any())).thenReturn(new byte[]{1, 2, 3});
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AcademicLoadOrderDtos.CreateRequest request = new AcademicLoadOrderDtos.CreateRequest(
                year, AcademicLoadOrderType.CURRICULUM_APPROVAL, "2-ОД", LocalDate.of(2026, 8, 31),
                "", null, null, "Иванова Ирина Ивановна", "Директор", "", "");
        SessionUser user = new SessionUser();
        user.setUsername("admin");

        service.create(request, "7", user);

        ArgumentCaptor<AcademicLoadOrderDocumentService.DocumentData> document =
                ArgumentCaptor.forClass(AcademicLoadOrderDocumentService.DocumentData.class);
        verify(documentService).generate(document.capture());
        List<AcademicLoadOrderDocumentService.CurriculumPlanRow> rows = document.getValue().curriculumPlans();

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AcademicLoadOrderDocumentService.CurriculumPlanRow::parallel)
                .containsOnly(10);
        assertThat(rows).extracting(AcademicLoadOrderDocumentService.CurriculumPlanRow::className)
                .containsOnly("10-А");
        assertThat(rows).extracting(AcademicLoadOrderDocumentService.CurriculumPlanRow::studyPeriod)
                .containsExactly(StudyPeriod.H1, StudyPeriod.H2);
        assertThat(rows).extracting(AcademicLoadOrderDocumentService.CurriculumPlanRow::hours)
                .containsExactly(BigDecimal.valueOf(2), BigDecimal.valueOf(3));
    }

    private ManualLoadEntry load(int hours, StudyPeriod period, LocalDate from, LocalDate to) {
        ManualLoadEntry row = new ManualLoadEntry();
        row.setAcademicYear("2026/2027");
        row.setFioTeacher("Конышева Галина Ибрагимовна");
        row.setNumberSchoolBuilding("СП-3");
        row.setSubjectName("Занимательная математика юного москвича");
        row.setClassName("4-К");
        row.setLoad(hours);
        row.setEducationLevel(EducationLevel.BASIC);
        row.setStudyPeriod(period);
        row.setLoadFromDate(from);
        row.setLoadToDate(to);
        return row;
    }

    private CurriculumPlanEntry curriculum(String className,
                                           String subject,
                                           StudyPeriod period,
                                           int hours,
                                           EducationLevel level) {
        CurriculumPlanEntry row = new CurriculumPlanEntry();
        row.setAcademicYear("2026/2027");
        row.setNumberSchoolBuilding("СП-2");
        row.setStage(CurriculumStage.SOO);
        row.setClassName(className);
        row.setSubjectName(subject);
        row.setCurriculumPart(CurriculumPart.CORE);
        row.setStudyPeriod(period);
        row.setPlannedHours(BigDecimal.valueOf(hours));
        row.setEducationLevel(level);
        row.setDeprecated(false);
        return row;
    }
}
