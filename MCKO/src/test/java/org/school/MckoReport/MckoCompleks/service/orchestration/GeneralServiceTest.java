package org.school.MckoReport.MckoCompleks.service.orchestration;

import org.junit.jupiter.api.Test;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralServiceTest {

    @Test
    void removesExactDuplicateRowsBeforeExport() {
        CombinedResultData row = CombinedResultData.builder()
                .school("ГБОУ №7")
                .className("5-А")
                .nameFIO("Иванов Иван")
                .code("9116-0004")
                .subject("Английский язык")
                .date("01.10.2025")
                .percentCompleted(76)
                .hasResultData(true)
                .build();

        List<CombinedResultData> deduplicated =
                GeneralService.deduplicateCombinedResults(List.of(row, row));

        assertThat(deduplicated).containsExactly(row);
    }

    @Test
    void keepsYoSpellingAndStudentNumberFromDuplicateRows() {
        ListStudentData withNumber = ListStudentData.builder()
                .nameFIO("Семенова Анна")
                .studentNumber(17)
                .build();
        ListStudentData withYo = ListStudentData.builder()
                .nameFIO("Семёнова Анна")
                .build();

        ListStudentData merged = GeneralService.preferBetterNameRecord(withNumber, withYo);

        assertThat(merged.getNameFIO()).isEqualTo("Семёнова Анна");
        assertThat(merged.getStudentNumber()).isEqualTo(17);
    }
}
