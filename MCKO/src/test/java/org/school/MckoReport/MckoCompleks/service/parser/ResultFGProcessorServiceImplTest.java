package org.school.MckoReport.MckoCompleks.service.parser;

import org.junit.jupiter.api.Test;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFGProcessorServiceImplTest {

    @Test
    void doesNotInventSectionPercentsWhenPdfHasNoSectionColumns() {
        String text = """
                Фамилия, имя № уч.
                1 9116-0001 4038 1 1 12 46% 44% 50% Базовый
                """;

        StudentResultFGData result = onlyResult(text);

        assertThat(result.getOverallPercent()).isEqualTo("46");
        assertThat(result.getSection1Percent()).isNull();
        assertThat(result.getSection2Percent()).isNull();
        assertThat(result.getSection3Percent()).isNull();
    }

    @Test
    void readsSectionPercentsWhenPdfContainsSectionColumns() {
        String text = """
                Раздел 1 Раздел 2 Раздел 3
                Фамилия, имя № уч.
                1 9116-0001 8012 6 29% 38% 13% 14% 33% 38% 33% 25% 29% 33% Ниже базового
                """;

        StudentResultFGData result = onlyResult(text);

        assertThat(result.getOverallPercent()).isEqualTo("29");
        assertThat(result.getSection1Percent()).isEqualTo("25");
        assertThat(result.getSection2Percent()).isEqualTo("29");
        assertThat(result.getSection3Percent()).isEqualTo("33");
    }

    private StudentResultFGData onlyResult(String text) {
        List<StudentResultFGData> results = ResultFGProcessorServiceImpl.extractStudentResults(
                text,
                "5-А",
                "Функциональная грамотность",
                "10.12.2025",
                "ГБОУ №7"
        );
        assertThat(results).hasSize(1);
        return results.get(0);
    }
}
