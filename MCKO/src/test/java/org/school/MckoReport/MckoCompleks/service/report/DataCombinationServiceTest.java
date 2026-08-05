package org.school.MckoReport.MckoCompleks.service.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;
import org.school.MckoReport.MckoCompleks.repository.ListStudentDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultFGDataRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataCombinationServiceTest {
    private static final String SCHOOL = "ГБОУ №7";
    private static final String SUBJECT = "Английский язык";
    private static final String CLASS_NAME = "5-А";
    private static final String LIST_DATE = "01.10.2025";

    private ListStudentDataRepository listRepository;
    private StudentResultDataRepository resultRepository;
    private StudentResultFGDataRepository fgRepository;
    private DataCombinationService service;

    @BeforeEach
    void setUp() {
        listRepository = mock(ListStudentDataRepository.class);
        resultRepository = mock(StudentResultDataRepository.class);
        fgRepository = mock(StudentResultFGDataRepository.class);
        service = new DataCombinationService(listRepository, resultRepository, fgRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Бланк"})
    void matchesResultByStudentNumberWhenSourceCodeIsMissingOrInvalid(String sourceCode) {
        ListStudentData student = student("9116-0004", 4, LIST_DATE);
        StudentResultData result = result(sourceCode, 4, LIST_DATE, 76);
        stubData(student, List.of(result), List.of());

        CombinedResultData combined = onlyResult();

        assertThat(combined.isHasResultData()).isTrue();
        assertThat(combined.getPercentCompleted()).isEqualTo(76);
        assertThat(combined.getStudentNumber()).isEqualTo(4);
    }

    @Test
    void rejectsAmbiguousStudentNumberMatch() {
        ListStudentData student = student("9116-0004", 4, LIST_DATE);
        StudentResultData first = result(null, 4, LIST_DATE, 61);
        StudentResultData second = result(null, 4, LIST_DATE, 82);
        stubData(student, List.of(first, second), List.of());

        CombinedResultData combined = onlyResult();

        assertThat(combined.isHasResultData()).isFalse();
        assertThat(combined.getPercentCompleted()).isNull();
    }

    @Test
    void usesUniqueResultDateWithinMonthAndKeepsSourceDate() {
        ListStudentData student = student("9116-0004", 4, LIST_DATE);
        StudentResultData result = result("9116-0004", 4, "21.10.2025", 76);
        stubData(student, List.of(result), List.of());

        CombinedResultData combined = onlyResult();

        assertThat(combined.isHasResultData()).isTrue();
        assertThat(combined.getDate()).isEqualTo("21.10.2025");
    }

    @Test
    void rejectsMonthMatchWhenSourceContainsSeveralDates() {
        ListStudentData student = student("9116-0004", 4, LIST_DATE);
        StudentResultData first = result("9116-0004", 4, "05.10.2025", 61);
        StudentResultData second = result("9116-0004", 4, "07.10.2025", 82);
        stubData(student, List.of(first, second), List.of());

        CombinedResultData combined = onlyResult();

        assertThat(combined.isHasResultData()).isFalse();
    }

    @Test
    void rejectsConflictingResultsFromOneFallbackDate() {
        ListStudentData student = student("9116-0004", 4, LIST_DATE);
        StudentResultData first = result("9116-0004", 4, "05.10.2025", 61);
        StudentResultData second = result("9116-0004", 4, "05.10.2025", 82);
        stubData(student, List.of(first, second), List.of());

        CombinedResultData combined = onlyResult();

        assertThat(combined.isHasResultData()).isFalse();
    }

    @Test
    void exportsFunctionalLiteracyClassAndCityPercents() {
        ListStudentData student = student("9116-0004", 4, LIST_DATE);
        StudentResultFGData fg = StudentResultFGData.builder()
                .school(SCHOOL)
                .subject(SUBJECT)
                .className(CLASS_NAME)
                .date(LIST_DATE)
                .code("9116-0004")
                .overallPercent("46")
                .classPercent(48)
                .cityPercent(55)
                .build();
        stubData(student, List.of(), List.of(fg));

        CombinedResultData combined = onlyResult();

        assertThat(combined.isHasFGData()).isTrue();
        assertThat(combined.getClassLevel()).isEqualTo("48%");
        assertThat(combined.getCityLevel()).isEqualTo("55%");
    }

    @Test
    void rejectsConflictingFunctionalLiteracyRows() {
        ListStudentData student = student("9116-0004", 4, LIST_DATE);
        StudentResultFGData first = functionalLiteracyResult("46", 48, 55);
        StudentResultFGData second = functionalLiteracyResult("71", 48, 55);
        stubData(student, List.of(), List.of(first, second));

        CombinedResultData combined = onlyResult();

        assertThat(combined.isHasFGData()).isFalse();
    }

    private void stubData(
            ListStudentData student,
            List<StudentResultData> results,
            List<StudentResultFGData> fgResults) {
        when(listRepository.findBySchoolAndClassNameAndSubjectAndDate(
                SCHOOL,
                CLASS_NAME,
                SUBJECT,
                LIST_DATE
        )).thenReturn(List.of(student));
        when(resultRepository.findBySchoolAndClassName(SCHOOL, CLASS_NAME)).thenReturn(results);
        when(fgRepository.findBySchool(SCHOOL)).thenReturn(fgResults);
    }

    private CombinedResultData onlyResult() {
        List<CombinedResultData> combined =
                service.combineDataByKey(SCHOOL, SUBJECT, LIST_DATE, CLASS_NAME);
        assertThat(combined).hasSize(1);
        return combined.get(0);
    }

    private ListStudentData student(String code, Integer number, String date) {
        return ListStudentData.builder()
                .school(SCHOOL)
                .subject(SUBJECT)
                .className(CLASS_NAME)
                .date(date)
                .schoolYear("2025-2026")
                .nameFIO("Иванов Иван")
                .code(code)
                .studentNumber(number)
                .build();
    }

    private StudentResultData result(String code, Integer number, String date, Integer percent) {
        return StudentResultData.builder()
                .school(SCHOOL)
                .subject(SUBJECT)
                .className(CLASS_NAME)
                .date(date)
                .schoolYear("2025-2026")
                .code(code)
                .studentNumber(number)
                .percentCompleted(percent)
                .ball(percent)
                .variant(1)
                .mark(4)
                .build();
    }

    private StudentResultFGData functionalLiteracyResult(
            String overallPercent,
            Integer classPercent,
            Integer cityPercent) {
        return StudentResultFGData.builder()
                .school(SCHOOL)
                .subject(SUBJECT)
                .className(CLASS_NAME)
                .date(LIST_DATE)
                .code("9116-0004")
                .overallPercent(overallPercent)
                .classPercent(classPercent)
                .cityPercent(cityPercent)
                .build();
    }
}
