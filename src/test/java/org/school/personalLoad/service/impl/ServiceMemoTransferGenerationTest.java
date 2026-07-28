package org.school.personalLoad.service.impl;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dao.TarifficationChangesDAO;
import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.model.ServiceMemo;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSettingKey;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.ServiceMemoRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.EmploymentContractRepository;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.school.personalLoad.service.ServiceMemoSettingsService;
import org.school.personalLoad.service.HrDocumentService;
import org.school.personalLoad.service.LoadSalaryCalculationService;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceMemoTransferGenerationTest {

    @Mock
    private TarifficationChangesDAO changesDAO;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private ServiceMemoRepository serviceMemoRepository;
    @Mock
    private StudyPeriodSettingService studyPeriodSettingService;
    @Mock
    private ServiceMemoSettingsService serviceMemoSettingsService;
    @Mock
    private EmploymentContractRepository employmentContractRepository;
    @Mock
    private HrDocumentService hrDocumentService;
    @Mock
    private LoadSalaryCalculationService loadSalaryCalculationService;

    private ServiceMemoServiceImpl service;
    private List<ServiceMemo> savedMemos;

    @BeforeEach
    void setUp() {
        savedMemos = new ArrayList<>();
        service = new ServiceMemoServiceImpl(
                changesDAO,
                manualLoadEntryRepository,
                teacherDirectoryRepository,
                serviceMemoRepository,
                studyPeriodSettingService,
                serviceMemoSettingsService,
                employmentContractRepository,
                hrDocumentService,
                loadSalaryCalculationService
        );
        lenient().when(loadSalaryCalculationService.totalHours(any())).thenAnswer(invocation -> {
            ManualLoadEntry row = invocation.getArgument(0);
            return java.math.BigDecimal.valueOf(row.getGroupLoad() == null
                    ? Objects.requireNonNullElse(row.getLoad(), 0) : row.getGroupLoad());
        });
        lenient().when(loadSalaryCalculationService.includedHours(any())).thenReturn(java.math.BigDecimal.ZERO);
        lenient().when(loadSalaryCalculationService.paidHours(any()))
                .thenAnswer(invocation -> loadSalaryCalculationService.totalHours(invocation.getArgument(0)));

        lenient().when(studyPeriodSettingService.rangesByKey()).thenReturn(Map.of(
                StudyPeriodSettingKey.YEAR_1_9,
                new StudyPeriodSettingService.DateRange(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31))
        ));
        lenient().when(studyPeriodSettingService.rangesByKey(anyString())).thenAnswer(invocation -> {
            String academicYear = invocation.getArgument(0);
            if ("2026/2027".equals(academicYear)) {
                return Map.of(
                        StudyPeriodSettingKey.YEAR_1_9,
                        new StudyPeriodSettingService.DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 5, 31)),
                        StudyPeriodSettingKey.H1_1_9,
                        new StudyPeriodSettingService.DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)),
                        StudyPeriodSettingKey.H2_1_9,
                        new StudyPeriodSettingService.DateRange(LocalDate.of(2027, 1, 11), LocalDate.of(2027, 5, 31)),
                        StudyPeriodSettingKey.H1_11,
                        new StudyPeriodSettingService.DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31)),
                        StudyPeriodSettingKey.H2_11,
                        new StudyPeriodSettingService.DateRange(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 5, 31))
                );
            }
            return Map.of(
                    StudyPeriodSettingKey.YEAR_1_9,
                    new StudyPeriodSettingService.DateRange(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31))
            );
        });
        lenient().when(teacherDirectoryRepository.findAll()).thenReturn(List.of());
        lenient().when(teacherDirectoryRepository.findByFioTeacherIgnoreCase(anyString())).thenAnswer(invocation -> {
            org.school.personalLoad.model.TeacherDirectoryEntry teacher = new org.school.personalLoad.model.TeacherDirectoryEntry();
            teacher.setId(1L); teacher.setFioTeacher(invocation.getArgument(0)); return java.util.Optional.of(teacher);
        });
        org.school.personalLoad.model.EmploymentContract contract = new org.school.personalLoad.model.EmploymentContract();
        contract.setId(10L); contract.setTeacherId(1L); contract.setContractNumber("ТД-1"); contract.setPrimaryContract(true); contract.setActive(true);
        lenient().when(employmentContractRepository.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of(contract));
        lenient().when(serviceMemoRepository.findAllByStatusInOrderByCreatedAtDesc(any())).thenReturn(List.of());
        lenient().when(serviceMemoRepository.findAllByAcademicYearAndStatusInOrderByCreatedAtDesc(anyString(), any())).thenReturn(List.of());
        lenient().when(changesDAO.findAll()).thenReturn(List.of());
        lenient().when(serviceMemoSettingsService.get()).thenReturn(new org.school.personalLoad.dto.ServiceMemoSettingsDto("Директору", "И.И. Ивановой"));
        AtomicLong seq = new AtomicLong(1);
        lenient().when(serviceMemoRepository.save(any(ServiceMemo.class))).thenAnswer(invocation -> {
            ServiceMemo memo = invocation.getArgument(0);
            if (memo.getId() == null) {
                memo.setId(seq.getAndIncrement());
            }
            savedMemos.add(memo);
            return memo;
        });
    }

    @Test
    void transferCreatesPendingForDonorAndRecipient() {
        ManualLoadEntry donor = row("Иванов И.И.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(donor, recipient));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertTrue(pending.stream().anyMatch(p -> "Иванов И.И.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
        assertTrue(pending.stream().anyMatch(p -> "Петров П.П.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
    }

    @Test
    void generatedLoadMemoIsBoundToContractAndCreatesAgreementDraft() throws Exception {
        ManualLoadEntry row = row("Сидоров С.С.", "Математика", "5-А", 5,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));
        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(row));
        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().get(0);

        service.generateForTeachers(null, List.of(pending.getTeacherKey()), "Автор");

        ServiceMemo memo = savedMemos.get(savedMemos.size() - 1);
        assertTrue(Objects.equals(1L, memo.getTeacherId()));
        assertTrue(Objects.equals(10L, memo.getContractId()));
        assertTrue(memo.getBeforeSnapshotJson().contains("rows"));
        assertTrue(memo.getAfterSnapshotJson().contains("Математика"));
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(memo.getGeneratedDocument()))){
            List<XWPFParagraph> paragraphs=new ArrayList<>(document.getParagraphs());
            document.getTables().forEach(table->table.getRows().forEach(tableRow->
                    tableRow.getTableCells().forEach(cell->paragraphs.addAll(cell.getParagraphs()))));
            assertTrue(paragraphs.stream().flatMap(paragraph->paragraph.getRuns().stream())
                    .filter(run->run.text()!=null&&!run.text().isBlank())
                    .allMatch(run->run.getFontSize()>=14),
                    "Текст служебной записки и таблицы должен быть не мельче 14 пунктов");
        }
        String qaOutput=System.getProperty("load.memo.qa.output");
        if(qaOutput!=null&&!qaOutput.isBlank()){
            Path path=Path.of(qaOutput);Files.createDirectories(path.getParent());Files.write(path,memo.getGeneratedDocument());
        }
        org.mockito.Mockito.verify(hrDocumentService).createLoadChangeDraft(
                org.mockito.ArgumentMatchers.same(memo), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("Автор"));
    }

    @Test
    void generatedLoadMemoDoesNotRequireEmploymentContract() {
        ManualLoadEntry row = row("Сидоров С.С.", "Математика", "5-А", 5,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));
        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(row));
        when(employmentContractRepository.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of());
        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().get(0);

        service.generateForTeachers(null, List.of(pending.getTeacherKey()), "Автор");

        ServiceMemo memo = savedMemos.get(savedMemos.size() - 1);
        assertTrue(Objects.equals(1L, memo.getTeacherId()));
        assertTrue(memo.getContractId() == null);
        assertTrue(memo.getGeneratedDocument().length > 0);
        org.mockito.Mockito.verify(hrDocumentService).createLoadChangeDraft(
                org.mockito.ArgumentMatchers.same(memo),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.eq("Автор"));
    }

    @Test
    void hrReceiptBackfillsLegacyBindingAndReleasesAgreement() {
        ServiceMemo memo = new ServiceMemo(); memo.setId(77L); memo.setFioTeacher("Сидоров С.С.");
        memo.setStatus(ServiceMemo.Status.SIGNED); memo.setAcademicYear("2025/2026");
        memo.setChangeStartDate(LocalDate.of(2025,10,11));
        when(serviceMemoRepository.findById(77L)).thenReturn(java.util.Optional.of(memo));

        service.receiveByHr(77L,"Кадры");

        assertTrue(memo.getStatus()==ServiceMemo.Status.RECEIVED_BY_HR);
        assertTrue(Objects.equals(1L,memo.getTeacherId()));
        assertTrue(Objects.equals(10L,memo.getContractId()));
        org.mockito.Mockito.verify(hrDocumentService).ensureLoadChangeDraft(
                org.mockito.ArgumentMatchers.same(memo),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("Кадры"));
        org.mockito.Mockito.verify(hrDocumentService).onLoadMemoReceived(memo);
    }

    @Test
    void hrCanReceiveLoadMemoBeforeEmploymentContractIsFilled() {
        ServiceMemo memo = new ServiceMemo(); memo.setId(78L); memo.setFioTeacher("Сидоров С.С.");
        memo.setStatus(ServiceMemo.Status.SIGNED); memo.setAcademicYear("2025/2026");
        memo.setChangeStartDate(LocalDate.of(2025,10,11));
        when(serviceMemoRepository.findById(78L)).thenReturn(java.util.Optional.of(memo));
        when(employmentContractRepository.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of());

        service.receiveByHr(78L,"Кадры");

        assertTrue(memo.getStatus()==ServiceMemo.Status.RECEIVED_BY_HR);
        assertTrue(Objects.equals(1L,memo.getTeacherId()));
        assertTrue(memo.getContractId()==null);
        org.mockito.Mockito.verify(hrDocumentService).ensureLoadChangeDraft(
                org.mockito.ArgumentMatchers.same(memo),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.eq("Кадры"));
        org.mockito.Mockito.verify(hrDocumentService).onLoadMemoReceived(memo);
    }

    @Test
    void hrCannotReceiveLoadMemoBeforeDirectorSignature() {
        ServiceMemo memo=new ServiceMemo();memo.setId(79L);memo.setStatus(ServiceMemo.Status.PROCESSED);
        when(serviceMemoRepository.findById(79L)).thenReturn(java.util.Optional.of(memo));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->service.receiveByHr(79L,"Кадры"));

        assertEquals(ServiceMemo.Status.PROCESSED,memo.getStatus());
    }

    @Test
    void transferBetweenActiveEmployeesUsesProductionNecessityRationale() throws Exception {
        ManualLoadEntry donor = row("Иванов И.И.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));
        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(donor, recipient));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();
        ServiceMemoDtos.PendingTeacher recipientMemo = pending.stream()
                .filter(p -> "Петров П.П.".equals(p.getFioTeacher()))
                .findFirst()
                .orElseThrow();

        String text = generatedMemoText(null, recipientMemo.getTeacherKey());

        assertTrue(text.contains("В связи с производственной необходимостью"));
        assertTrue(text.contains("Петров П.П."));
        assertTrue(text.contains("11.10.2025"));
        assertTrue(text.contains("Статус"));
        assertFalse(text.contains("вновь принятому сотруднику"));
        assertFalse(text.contains("в соответствие с учебным планом"));
    }

    @Test
    void newEmployeeUsesNewEmployeeRationaleAndTableWithoutStatus() throws Exception {
        ManualLoadEntry row = row("Сидоров С.С.", "Математика", "5-А", 5,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));
        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(row));

        ServiceMemoDtos.PendingTeacher pending = service.findPendingTeachers().stream()
                .filter(p -> "Сидоров С.С.".equals(p.getFioTeacher()))
                .findFirst()
                .orElseThrow();

        String text = generatedMemoText(null, pending.getTeacherKey());

        assertTrue(text.contains("вновь принятому сотруднику Сидоров С.С."));
        assertTrue(text.contains("11.10.2025"));
        assertFalse(text.contains("В связи с производственной необходимостью"));
        assertFalse(text.contains("Статус"));
    }

    @Test
    void donorWithoutCurrentRowsIsRecoveredFromRemovedHistoryChanges() {
        LocalDateTime changeTs = LocalDateTime.of(2025, 10, 11, 9, 0);
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(recipient));
        when(changesDAO.findAll()).thenReturn(List.of(
                change("Иванов И.И.", "Алгебра", "8-А", 6,
                        TarifficationChanges.ChangeType.REMOVED, changeTs),
                change("Петров П.П.", "Алгебра", "8-А", 6,
                        TarifficationChanges.ChangeType.ADDED, changeTs)
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertTrue(pending.stream().anyMatch(p -> "Иванов И.И.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
        assertTrue(pending.stream().anyMatch(p -> "Петров П.П.".equals(p.getFioTeacher())
                && LocalDate.of(2025, 10, 11).equals(p.getStartDate())));
    }

    @Test
    void transferOneClassOutOfFiveKeepsDonorMemoWithRemovedAndRemainingRows() {
        String donorFio = "Иванов И.И.";
        LocalDate transferDate = LocalDate.of(2025, 10, 11);

        ManualLoadEntry donorLeaving = row(donorFio, "Алгебра", "8-А", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 10));
        ManualLoadEntry donorRemaining1 = row(donorFio, "Алгебра", "8-Б", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry donorRemaining2 = row(donorFio, "Алгебра", "8-В", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry donorRemaining3 = row(donorFio, "Алгебра", "8-Г", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry donorRemaining4 = row(donorFio, "Алгебра", "8-Д", 6,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry recipient = row("Петров П.П.", "Алгебра", "8-А", 6,
                transferDate, LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(
                donorLeaving, donorRemaining1, donorRemaining2, donorRemaining3, donorRemaining4, recipient
        ));
        when(changesDAO.findAll()).thenReturn(List.of());

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();
        ServiceMemoDtos.PendingTeacher donorMemo = pending.stream()
                .filter(p -> donorFio.equals(p.getFioTeacher()) && transferDate.equals(p.getStartDate()))
                .findFirst()
                .orElseThrow();

        assertTrue(donorMemo.getRows().stream()
                .anyMatch(r -> "8-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
        long unchangedCount = donorMemo.getRows().stream()
                .filter(r -> r.getStatus() == null || r.getStatus().isBlank())
                .map(ServiceMemoDtos.LoadRow::getClassName)
                .filter(Objects::nonNull)
                .filter(className -> className.startsWith("8-"))
                .count();
        assertTrue(unchangedCount >= 4);
    }

    @Test
    void donorFallbackUsesLatestHistoryBatchOnly() {
        ManualLoadEntry recipient = row("Петров П.П.", "ИЗО", "1-А", 1,
                LocalDate.of(2025, 9, 14), LocalDate.of(2026, 5, 31));

        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(recipient));
        when(changesDAO.findAll()).thenReturn(List.of(
                change("Архангельская Т.М.", "ИЗО", "1-А", 1,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2025, 9, 14, 9, 0)),
                change("Архангельская Т.М.", "ИЗО", "1-Е", 1,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2025, 9, 14, 9, 0)),
                change("Архангельская Т.М.", "ИЗО", "1-А", 1,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2025, 9, 14, 10, 0))
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();
        ServiceMemoDtos.PendingTeacher donorMemo = pending.stream()
                .filter(p -> "Архангельская Т.М.".equals(p.getFioTeacher()))
                .findFirst()
                .orElseThrow();

        long removedRows = donorMemo.getRows().stream()
                .filter(r -> "Снять".equals(r.getStatus()))
                .count();
        assertTrue(removedRows == 1);
        assertTrue(donorMemo.getRows().stream()
                .anyMatch(r -> "1-А".equals(r.getClassName()) && "Снять".equals(r.getStatus())));
    }

    @Test
    void curriculumPlanSecondHalfChangeUsesCurriculumRationaleAndDoesNotRemoveActiveLoad() throws Exception {
        String fio = "Андриенкова Алиса Максимовна";
        String subject = "Занимательная математика юного москвича";
        LocalDate secondHalfStart = LocalDate.of(2027, 1, 11);

        ManualLoadEntry firstHalf = row(fio, subject, "4-А", 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        ManualLoadEntry secondHalf = row(fio, subject, "4-А", 2,
                LocalDate.of(2027, 1, 10), LocalDate.of(2027, 5, 31));
        firstHalf.setStudyPeriod(StudyPeriod.H1);
        secondHalf.setStudyPeriod(StudyPeriod.H2);

        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(firstHalf, secondHalf));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, subject, "4-А", 1,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2027, 1, 11, 9, 0))
        ));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers("2026/2027");

        ServiceMemoDtos.PendingTeacher memo = pending.stream()
                .filter(p -> fio.equals(p.getFioTeacher()) && secondHalfStart.equals(p.getStartDate()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, memo.getRows().size());
        assertEquals(2, memo.getTotalHours());
        assertEquals(1, memo.getRows().get(0).getLoad());
        assertEquals("Снять", memo.getRows().get(0).getStatus());
        assertEquals(2, memo.getRows().get(1).getLoad());
        assertEquals("Добавить", memo.getRows().get(1).getStatus());
        assertTrue(memo.getRows().stream().anyMatch(r -> Objects.equals(r.getLoad(), 1)
                && "Снять".equals(r.getStatus())));
        assertTrue(memo.getRows().stream().anyMatch(r -> Objects.equals(r.getLoad(), 2)
                && "Добавить".equals(r.getStatus())));
        assertFalse(memo.getRows().stream().anyMatch(r -> Objects.equals(r.getLoad(), 2)
                && "Снять".equals(r.getStatus())));
        String text = generatedMemoText("2026/2027", memo.getTeacherKey());

        assertTrue(text.contains("В связи с необходимостью приведения учебной нагрузки в соответствие с учебным планом на 2026/2027 учебный год"));
        assertTrue(text.contains("с 11.01.2027"));
        assertTrue(text.contains("Снять"));
        assertTrue(text.contains("Добавить"));
        assertFalse(text.contains("производственной необходимостью"));
    }

    @Test
    void equalHoursAcrossCurriculumHalvesDoNotProduceServiceMemo() {
        String fio = "Иванова И.И.";
        String subject = "Занимательная математика юного москвича";

        ManualLoadEntry firstHalf = row(fio, subject, "4-А", 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        ManualLoadEntry secondHalf = row(fio, subject, "4-А", 1,
                LocalDate.of(2027, 1, 11), LocalDate.of(2027, 5, 31));
        firstHalf.setStudyPeriod(StudyPeriod.H1);
        secondHalf.setStudyPeriod(StudyPeriod.H2);

        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(firstHalf, secondHalf));

        assertTrue(service.findPendingTeachers("2026/2027").isEmpty());
    }

    @Test
    void decemberCurriculumChangeForEleventhGradeSecondHalfProducesMemoForSecondHalfStart() throws Exception {
        String fio = "Павлова П.П.";
        String subject = "Литература";
        ManualLoadEntry firstHalf = row(fio, subject, "11-А", 2,
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31));
        ManualLoadEntry secondHalf = row(fio, subject, "11-А", 3,
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 5, 31));

        when(manualLoadEntryRepository.findAllByAcademicYear("2026/2027")).thenReturn(List.of(firstHalf, secondHalf));
        when(changesDAO.findAll()).thenReturn(List.of(
                change(fio, subject, "11-А", 2,
                        TarifficationChanges.ChangeType.REMOVED, LocalDateTime.of(2026, 12, 15, 10, 0)),
                change(fio, subject, "11-А", 3,
                        TarifficationChanges.ChangeType.ADDED, LocalDateTime.of(2026, 12, 15, 10, 0))
        ));

        ServiceMemoDtos.PendingTeacher memo = service.findPendingTeachers("2026/2027").stream()
                .filter(p -> fio.equals(p.getFioTeacher()))
                .filter(p -> LocalDate.of(2027, 2, 1).equals(p.getStartDate()))
                .findFirst()
                .orElseThrow();

        String text = generatedMemoText("2026/2027", memo.getTeacherKey());

        assertTrue(text.contains("учебным планом на 2026/2027 учебный год"));
        assertTrue(text.contains("с 01.02.2027"));
        assertTrue(text.contains(subject));
        assertTrue(text.contains("3"));
    }

    @Test
    void processedDonorMemoIsNotReopenedWhenOnlyUnchangedRowsAreUpdatedLater() {
        String donorFio = "Бегунц Александр Владимирович";
        LocalDate changeDate = LocalDate.of(2025, 9, 23);

        ManualLoadEntry donorRemoved = row(donorFio, "Изобразительное искусство", "1-А", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 22));
        ManualLoadEntry donorOther = row(donorFio, "Изобразительное искусство", "1-Е", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31));
        ManualLoadEntry recipient = row("Бардина Наталья Николаевна", "Изобразительное искусство", "1-А", 1,
                changeDate, LocalDate.of(2026, 5, 31));
        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(donorRemoved, donorOther, recipient));

        String donorSignaturePayload = "2025-09-23|false|"
                + "Снять|изобразительное искусство|1-а|1|2025-09-01|2025-09-22";
        ServiceMemo processed = new ServiceMemo();
        processed.setStatus(ServiceMemo.Status.PROCESSED);
        processed.setFioTeacher(donorFio);
        processed.setChangeStartDate(changeDate);
        processed.setCreatedBy("tester");
        processed.setGeneratedFilename("memo.docx");
        processed.setGeneratedDocument(new byte[]{1});
        processed.setLoadSignature(sha256(donorSignaturePayload));
        processed.setCreatedAt(LocalDateTime.now());
        when(serviceMemoRepository.findAllByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(processed));

        ManualLoadEntry donorOtherChangedLater = row(donorFio, "Изобразительное искусство", "1-Е", 1,
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 11, 10));
        when(manualLoadEntryRepository.findAll()).thenReturn(List.of(donorRemoved, donorOtherChangedLater, recipient));

        List<ServiceMemoDtos.PendingTeacher> pending = service.findPendingTeachers();

        assertTrue(pending.stream().noneMatch(p ->
                donorFio.equals(p.getFioTeacher()) && changeDate.equals(p.getStartDate())));
    }

    private ManualLoadEntry row(String fio, String subject, String className, int load,
                                LocalDate from, LocalDate to) {
        ManualLoadEntry entry = new ManualLoadEntry();
        entry.setFioTeacher(fio);
        entry.setSubjectName(subject);
        entry.setClassName(className);
        entry.setLoad(load);
        entry.setLoadFromDate(from);
        entry.setLoadToDate(to);
        return entry;
    }

    private TarifficationChanges change(String fio, String subject, String className, int load,
                                        TarifficationChanges.ChangeType type, LocalDateTime when) {
        TarifficationChanges change = new TarifficationChanges();
        change.setFioTeacher(fio);
        change.setSubjectName(subject);
        change.setClassName(className);
        change.setLoad(load);
        change.setChangeType(type);
        change.setChangeDate(when);
        return change;
    }

    private String generatedMemoText(String academicYear, String teacherKey) throws Exception {
        savedMemos.clear();
        service.generateForTeachers(academicYear, List.of(teacherKey), "Автор");
        ServiceMemo memo = savedMemos.get(savedMemos.size() - 1);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(memo.getGeneratedDocument()))) {
            StringBuilder text = new StringBuilder();
            doc.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
            for (XWPFTable table : doc.getTables()) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> text.append(cell.getText()).append('\n')));
            }
            return text.toString();
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
