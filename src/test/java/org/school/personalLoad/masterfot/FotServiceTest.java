package org.school.personalLoad.masterfot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.impl.MckoServiceImpl;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FotServiceTest {
    final FotBatchRepository batches = mock(FotBatchRepository.class);
    final FotIssueRepository issues = mock(FotIssueRepository.class);
    final FotMappingRepository mappings = mock(FotMappingRepository.class);
    final CurriculumPlanEntryRepository curriculum = mock(CurriculumPlanEntryRepository.class);
    final ManualLoadEntryRepository loads = mock(ManualLoadEntryRepository.class);
    final StudyPeriodSettingRepository periods = mock(StudyPeriodSettingRepository.class);
    final MckoServiceImpl mcko = mock(MckoServiceImpl.class);
    final FotParser parser = mock(FotParser.class);
    final Map<String,FotIssue> stored = new HashMap<>();
    final List<FotBatch> history = new ArrayList<>();
    final String year = "2026/2027";
    FotService service;
    @BeforeEach void setup() {
        service = new FotService(parser,batches,issues,mappings,curriculum,loads,periods,mcko,new ObjectMapper().registerModule(new JavaTimeModule()));
        when(curriculum.findAllByAcademicYear(year)).thenReturn(List.of(FotComparisonTest.plan(3)));
        when(loads.findAllByAcademicYear(year)).thenReturn(List.of(FotComparisonTest.load(3)));
        when(batches.findAllByAcademicYearOrderByIdDesc(year)).thenAnswer(i -> history.reversed());
        AtomicLong ids = new AtomicLong();
        when(batches.saveAndFlush(any())).thenAnswer(i -> { FotBatch b = i.getArgument(0); b.setId(ids.incrementAndGet()); history.add(b); return b; });
        when(batches.findById(anyLong())).thenAnswer(i -> history.stream().filter(b -> b.getId().equals(i.getArgument(0))).findFirst());
        when(issues.findAllByAcademicYear(year)).thenAnswer(i -> new ArrayList<>(stored.values()));
        when(issues.findById(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        when(issues.save(any())).thenAnswer(i -> { FotIssue row = i.getArgument(0); stored.put(row.getId(),row); return row; });
        when(issues.saveAndFlush(any())).thenAnswer(i -> { FotIssue row = i.getArgument(0); stored.put(row.getId(),row); row.setVersion(row.getVersion()+1); return row; });
    }
    void upload(int hours) throws Exception {
        when(parser.parse(any(),eq(year))).thenReturn(FotComparisonTest.source(FotComparisonTest.row("Иванов Иван Иванович","7-А",hours)));
        service.upload(year,FotParserTest.file(false),"Тестовый пользователь");
    }
    FotIssue first() { return stored.values().iterator().next(); }
    @Test void fixRequiresNextExportAndArchivePreservesHistoryAndComments() throws Exception {
        upload(2); assertThat(stored).hasSize(2); FotIssue issue = first();
        service.decision(year,issue.getId(),new FotDtos.DecisionRequest("FIXED","Исправил в ФОТ",issue.getVersion()),"Методист");
        upload(2); assertThat(issue.getStatus()).isEqualTo("OPEN"); assertThat(issue.isArchived()).isFalse();
        service.decision(year,issue.getId(),new FotDtos.DecisionRequest("FIXED","Исправил в ФОТ",issue.getVersion()),"Методист");
        upload(3);
        assertThat(stored.values()).allMatch(FotIssue::isArchived);
        assertThat(issue.getComment()).isEqualTo("Исправил в ФОТ"); assertThat(issue.getArchivedBatchId()).isEqualTo(3L);
        assertThat(service.history(year,1L)).hasSize(2); assertThat(service.history(year,3L)).isEmpty();
        upload(2); assertThat(stored).hasSize(2); assertThat(issue.isArchived()).isFalse(); assertThat(issue.getStatus()).isEqualTo("OPEN");
        assertThat(issue.getFirstBatchId()).isEqualTo(1L);
        verify(curriculum,never()).save(any()); verify(loads,never()).save(any());
    }
    @Test void expectedDecisionPersistsOnlyWhileValuesStaySame() throws Exception {
        upload(2); FotIssue issue = first();
        service.decision(year,issue.getId(),new FotDtos.DecisionRequest("EXPECTED","Особенность",issue.getVersion()),"Методист");
        upload(2); assertThat(issue.getStatus()).isEqualTo("EXPECTED");
        upload(1); assertThat(issue.getStatus()).isEqualTo("OPEN");
        assertThat(issue.getComment()).isEqualTo("Особенность");
    }
    @Test void incompleteComparisonCannotArchivePriorIssues() throws Exception {
        upload(2);
        when(parser.parse(any(),eq(year))).thenReturn(FotComparisonTest.source(FotComparisonTest.row("Другой педагог","7-А",3)));
        service.upload(year,FotParserTest.file(false),"Методист");
        assertThat(history.getLast().isComparisonComplete()).isFalse();
        assertThat(stored.values()).noneMatch(FotIssue::isArchived);
    }
    @Test void oldFileAndWrongYearDecisionRejectedBeforeMutation() throws Exception {
        upload(2); FotIssue issue = first();
        assertThatThrownBy(() -> service.decision("2025/2026",issue.getId(),new FotDtos.DecisionRequest("FIXED","",0),"Тест")).hasMessageContaining("не найдена");
        var src = FotComparisonTest.source(FotComparisonTest.row("Иванов Иван Иванович","7-А",3));
        when(parser.parse(any(),eq(year))).thenReturn(new FotDtos.Source(year,src.date().minusDays(1),src.organization(),src.rows()));
        var file = FotParserTest.file(false);
        assertThatThrownBy(() -> service.upload(year,file,"Тест")).hasMessageContaining("раньше последней");
        assertThat(history).hasSize(1);
    }
    @Test void staleDecisionVersionDoesNotOverwrite() throws Exception {
        upload(2); FotIssue issue = first();
        service.decision(year,issue.getId(),new FotDtos.DecisionRequest("EXPECTED","Первый комментарий",0),"Тест");
        assertThatThrownBy(() -> service.decision(year,issue.getId(),new FotDtos.DecisionRequest("FIXED","Другой комментарий",0),"Тест")).hasMessageContaining("уже изменена");
        assertThat(issue.getComment()).isEqualTo("Первый комментарий");
    }
    @Test void rejectsWorkbookOfAnotherNumericSchool() {
        assertThatThrownBy(() -> FotService.verifySchool("ГБОУ Школа № 1811 · 2026/2027", "7"))
                .hasMessageContaining("школы № 1811").hasMessageContaining("школы № 7");
        assertThatCode(() -> FotService.verifySchool("ГБОУ Школа № 7 · 2026/2027", "7")).doesNotThrowAnyException();
        assertThatCode(() -> FotService.verifySchool("Тестовая организация", "demo")).doesNotThrowAnyException();
    }
}
