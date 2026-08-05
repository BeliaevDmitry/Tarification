package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.school.personalLoad.dto.HrDocumentDtos.AgreementRequest;
import org.school.personalLoad.dto.HrDocumentDtos.AgreementEditRequest;
import org.school.personalLoad.dto.HrDocumentDtos.BatchAgreementRequest;
import org.school.personalLoad.dto.HrDocumentDtos.MemoRequest;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrDocumentServiceTest {
    EmploymentContractRepository contracts=mock(EmploymentContractRepository.class);
    HrPersonalDataRepository personal=mock(HrPersonalDataRepository.class);
    HrServiceMemoRepository memos=mock(HrServiceMemoRepository.class);
    ServiceMemoRepository loadMemos=mock(ServiceMemoRepository.class);
    AdditionalAgreementRepository agreements=mock(AdditionalAgreementRepository.class);
    HrDocumentVersionRepository versions=mock(HrDocumentVersionRepository.class);
    HrCatalogItemRepository catalog=mock(HrCatalogItemRepository.class);
    TeacherDirectoryRepository teachers=mock(TeacherDirectoryRepository.class);
    ManualLoadEntryRepository loads=mock(ManualLoadEntryRepository.class);
    SalarySettingsRepository salary=mock(SalarySettingsRepository.class);
    SubjectLevelCoefficientRepository coefficients=mock(SubjectLevelCoefficientRepository.class);
    SalaryGroupCoefficientSubjectRepository groups=mock(SalaryGroupCoefficientSubjectRepository.class);
    ClassroomLeadershipRepository classroomLeadership=mock(ClassroomLeadershipRepository.class);
    ClassSizeService sizes=mock(ClassSizeService.class);
    LoadSalaryCalculationService loadSalary;
    HrIncentiveRepository incentives=mock(HrIncentiveRepository.class);
    LoadInRateRuleRepository inRateRules=mock(LoadInRateRuleRepository.class);
    HrDocumentService service;
    EmploymentContract contract;
    TeacherDirectoryEntry teacher;

    @BeforeEach void setUp(){
        loadSalary=new LoadSalaryCalculationService(sizes,salary,coefficients,groups,new IupCompensationCalculator());
        service=new HrDocumentService(contracts,personal,memos,loadMemos,agreements,versions,catalog,teachers,loads,salary,coefficients,groups,classroomLeadership,sizes,loadSalary,incentives,inRateRules,new ObjectMapper().findAndRegisterModules());
        contract=new EmploymentContract(); contract.setId(10L); contract.setTeacherId(1L); contract.setContractNumber("1-ТД");
        contract.setContractDate(LocalDate.of(2025,1,1)); contract.setPositionName("Учитель");
        teacher=new TeacherDirectoryEntry(); teacher.setId(1L); teacher.setFioTeacher("Иванов Иван Иванович");
        when(contracts.findById(10L)).thenReturn(Optional.of(contract)); when(teachers.findById(1L)).thenReturn(Optional.of(teacher));
        when(contracts.save(any())).thenAnswer(x->x.getArgument(0));
        when(classroomLeadership.findAllByAcademicYear(anyString())).thenReturn(List.of());
        when(loads.findAllByAcademicYear(anyString())).thenReturn(List.of()); when(sizes.effectiveClassSizes(anyString())).thenReturn(Map.of());
        when(incentives.findAllByAcademicYear(anyString())).thenReturn(List.of());
        when(inRateRules.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(incentives.save(any())).thenAnswer(x->{HrIncentive incentive=x.getArgument(0);if(incentive.getId()==null)incentive.setId(200L);return incentive;});
        when(agreements.save(any())).thenAnswer(x->{AdditionalAgreement a=x.getArgument(0);if(a.getId()==null)a.setId(100L);return a;});
        when(memos.save(any())).thenAnswer(x->{HrServiceMemo m=x.getArgument(0);if(m.getId()==null)m.setId(50L);return m;});
    }

    @Test void annulledNumberIsReusedWithNewTechnicalRevision(){
        AdditionalAgreement old=new AdditionalAgreement(); old.setId(9L); old.setStatus(AdditionalAgreement.Status.ANNULLED);
        old.setInternalNumber("3 / 2025-2026"); old.setRevision(2); when(agreements.findById(9L)).thenReturn(Optional.of(old));
        AdditionalAgreement a=service.createAgreement(request(9L),"hr");
        assertEquals("3 / 2025-2026",a.getInternalNumber()); assertEquals(3,a.getRevision()); assertEquals(9L,a.getReplacesAgreementId());
        assertNull(a.getCurrentDocument(),"Черновик формируется только после ручной проверки");verifyNoInteractions(versions);
    }

    @Test void contractsAreExposedAsScalarViewsWithoutJpaTeacherAssociation() throws Exception {
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of(contract));

        var view=service.contractViews(1L).get(0);
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(view);

        assertEquals(1L,view.teacherId());
        assertTrue(json.contains("\"contractNumber\":\"1-ТД\""));
        assertFalse(json.contains("\"teacher\""));
    }

    @Test void positionRuleIsAppliedToContractWithoutManualFlag() {
        LoadInRateRule rule = new LoadInRateRule();
        rule.setId(71L);
        rule.setName("Преподаватель ОБЗР");
        rule.setDocumentLabel("Преподаватель ОБЗР");
        rule.setActive(true);
        when(inRateRules.findAllByOrderByNameAsc()).thenReturn(List.of(rule));

        EmploymentContract saved = service.saveContract(10L,
                new org.school.personalLoad.dto.HrDocumentDtos.ContractRequest(
                        1L, "1-ТД", LocalDate.of(2025, 1, 1), "Преподаватель ОБЗР",
                        LocalDate.of(2025, 1, 1), null, true, true,
                        false, null, "устаревшее ручное пояснение"));

        assertTrue(saved.isLoadHoursMayBeIncludedInRate());
        assertEquals(71L, saved.getLoadInRateRuleId());
        assertNull(saved.getLoadInRateDocumentLabel());
        var view = service.contractView(saved);
        assertTrue(view.loadHoursMayBeIncludedInRate());
        assertEquals(71L, view.loadInRateRuleId());
        assertNull(view.loadInRateDocumentLabel());
    }

    @Test void manualAdditionalWorkAgreementIsRejectedBecauseMemoCreatesItAutomatically() {
        AgreementRequest manualDuty=new AgreementRequest(10L,null,"2025/2026",LocalDate.of(2025,9,1),
                LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),AdditionalAgreement.Kind.ADDITIONAL_WORK,
                AdditionalAgreement.ChangeMode.AMEND,"Заведование кабинетом","Обязанности",new BigDecimal("15000"),null);

        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.createAgreement(manualDuty,"hr"));

        assertTrue(error.getReason().contains("служебную записку"));
        verify(agreements,never()).save(any());
    }

    @Test void issueIsBlockedUntilPersonalDataIsComplete(){
        AdditionalAgreement a=new AdditionalAgreement();a.setId(5L);a.setContractId(10L);a.setStatus(AdditionalAgreement.Status.DRAFT);
        when(agreements.findById(5L)).thenReturn(Optional.of(a));when(personal.findByTeacherId(1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,()->service.issue(5L,"hr"));
    }

    @Test void serviceMemoIsBoundToTeacherAndCreatesSeparateAgreementDraft(){
        MemoRequest request=new MemoRequest("2025/2026",1L,10L,null,null,LocalDate.of(2025,9,1),"Куратор здания",
                null,null,"2.4","Контролирует работу здания",new BigDecimal("15000"),
                LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),true,true,null);
        when(catalog.save(any())).thenAnswer(x->{HrCatalogItem item=x.getArgument(0);item.setId(7L);return item;});
        HrServiceMemo memo=service.createMemo(request,"deputy");
        assertEquals(1L,memo.getTeacherId()); assertTrue(memo.isSeparateAgreement()); assertEquals(7L,memo.getCatalogItemId());
        assertNull(memo.getContractClause());
        assertFalse(memo.getTitle().startsWith("О назначении:"));
        assertTrue(memo.getAssignmentText().contains("Прошу Вас согласовать поручение работнику Иванов Иван Иванович"));
        assertTrue(memo.getAssignmentText().contains("15 000,00 руб."));
        verify(catalog).save(argThat(item->item.isSeparateAgreement()&&item.getContractClause()==null));
        verify(agreements,atLeastOnce()).save(argThat(a->a.getKind()==AdditionalAgreement.Kind.ADDITIONAL_WORK
                && Objects.equals(a.getServiceMemoId(),50L) && a.getStatus()==AdditionalAgreement.Status.WAITING_FOR_MEMO));
    }

    @Test void selectedContractClauseIsUsedInAutomaticallyCreatedAgreement(){
        MemoRequest request=new MemoRequest("2025/2026",1L,10L,null,null,LocalDate.of(2025,9,1),"Заведование кабинетом",
                null,null,"2.5",null,new BigDecimal("5000"),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),false,false,null);

        HrServiceMemo memo=service.createMemo(request,"deputy");

        assertEquals("2.5",memo.getContractClause());
        assertTrue(memo.getAssignmentText().contains("за увеличение объема работ"));
        verify(agreements,atLeastOnce()).save(argThat(a->a.getConditionsJson()!=null
                && a.getConditionsJson().contains("пункт 2.5 трудового договора")));
    }

    @Test void nonSeparateMemoRejectsContractClauseOutsideStandardList() {
        MemoRequest request=new MemoRequest("2025/2026",1L,10L,null,null,LocalDate.of(2025,9,1),
                "Нестандартная выплата",null,null,"3.2",null,new BigDecimal("5000"),
                LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),false,false,null);

        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.createMemo(request,"deputy"));

        assertTrue(error.getReason().contains("2.1, 2.4 или 2.5"));
        verify(memos,never()).save(any());
    }

    @Test void clause24MemoContainsOnlyFunctionFromThisServiceMemo() throws Exception {
        ClassroomLeadershipEntry classroom=new ClassroomLeadershipEntry();classroom.setAcademicYear("2025/2026");
        classroom.setClassName("5 А");classroom.setTeacher(teacher);
        when(classroomLeadership.findAllByAcademicYear("2025/2026")).thenReturn(List.of(classroom));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5-а",22));
        MemoRequest request=new MemoRequest("2025/2026",1L,10L,null,null,LocalDate.of(2025,9,1),
                "Заведование кабинетом технологии",null,null,"2.4",null,new BigDecimal("15000"),
                LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),false,false,null);

        HrServiceMemo memo=service.createMemo(request,"deputy");

        assertTrue(memo.getAssignmentText().startsWith("Внести изменения в пункт 2.4 раздела 2 «Оплата труда»"));
        assertTrue(memo.getAssignmentText().contains("«2.4. Работнику выплачиваются ежемесячные компенсационные выплаты"));
        assertFalse(memo.getAssignmentText().contains("классного руководителя"));
        assertTrue(memo.getAssignmentText().contains("- возложена функция «заведование кабинетом технологии», в размере 15 000 рублей 00 коп. (пятнадцать тысяч рублей 00 коп.) в месяц»."));
        assertEquals(memo.getAssignmentText(),memo.getAgreementText());

        org.mockito.ArgumentCaptor<AdditionalAgreement> agreementCaptor=org.mockito.ArgumentCaptor.forClass(AdditionalAgreement.class);
        verify(agreements,atLeastOnce()).save(agreementCaptor.capture());
        AdditionalAgreement linked=agreementCaptor.getAllValues().stream()
                .filter(agreement->Objects.equals(agreement.getServiceMemoId(),50L)).reduce((left,right)->right).orElseThrow();
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        HrServiceMemo issued=service.memoStatus(50L,HrServiceMemo.Status.ISSUED,"deputy",
                "Беляев Дмитрий Алексеевич","заместителя директора");
        when(agreements.findAllByServiceMemoId(50L)).thenReturn(List.of(linked));
        service.memoStatus(50L,HrServiceMemo.Status.SIGNED,"director");
        service.memoStatus(50L,HrServiceMemo.Status.RECEIVED_BY_HR,"hr");
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(issued.getDocumentContent()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right);
            assertTrue(text.contains("Внести изменения в пункт 2.4 раздела 2 «Оплата труда»"));
            assertTrue(text.contains("возложена функция «заведование кабинетом технологии»"));
        }
        String qaOutput=System.getProperty("hr.memo.qa.output");
        if(qaOutput!=null&&!qaOutput.isBlank()){Path path=Path.of(qaOutput);Files.createDirectories(path.getParent());Files.write(path,issued.getDocumentContent());}
        when(agreements.findById(linked.getId())).thenReturn(Optional.of(linked));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));
        AdditionalAgreement prepared=service.prepare(linked.getId(),"hr");
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right);
            assertFalse(text.contains("возложена функция классного руководителя"));
            assertTrue(text.contains("возложена функция «заведование кабинетом технологии»"));
        }
        String fullAgreementQa=System.getProperty("hr.compensation.full.qa.output");
        if(fullAgreementQa!=null&&!fullAgreementQa.isBlank()){Path path=Path.of(fullAgreementQa);Files.createDirectories(path.getParent());Files.write(path,prepared.getCurrentDocument());}
    }

    @Test void clause24UsesRequiredAgreementWordingVerbatim() {
        MemoRequest request=new MemoRequest("2025/2026",1L,10L,null,null,LocalDate.of(2025,9,1),
                "Заведование кабинетом технологии",null,null,"2.4",null,new BigDecimal("15000"),
                LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),false,false,null);

        HrServiceMemo memo=service.createMemo(request,"deputy");

        assertEquals("Внести изменения в пункт 2.4 раздела 2 «Оплата труда», изложив его в следующей редакции:\n"
                +"«2.4. Работнику выплачиваются ежемесячные компенсационные выплаты при условии, если на Работника:\n"
                +"- возложена функция «заведование кабинетом технологии», в размере 15 000 рублей 00 коп. "
                +"(пятнадцать тысяч рублей 00 коп.) в месяц».",memo.getAgreementText());
    }

    @Test void clause24IgnoresLegacyTextSubmittedFromCatalog() {
        HrCatalogItem item=new HrCatalogItem();item.setId(7L);item.setActive(true);
        item.setSchoolCode(SchoolCodeResolver.resolve());item.setName("Заведование кабинетом технологии");
        item.setContractClause("2.4");item.setDefaultAmount(new BigDecimal("15000"));
        item.setMemoText("Прошу Вас согласовать работнику старую формулировку.");
        item.setAgreementText("Изложить пункт 2.4 трудового договора в старой редакции.");
        when(catalog.findById(7L)).thenReturn(Optional.of(item));
        MemoRequest request=new MemoRequest("2025/2026",1L,10L,7L,null,LocalDate.of(2025,9,1),
                item.getName(),item.getMemoText(),item.getAgreementText(),"2.4",null,new BigDecimal("15000"),
                LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),false,false,null);

        HrServiceMemo memo=service.createMemo(request,"deputy");

        assertTrue(memo.getAssignmentText().startsWith(
                "Внести изменения в пункт 2.4 раздела 2 «Оплата труда», изложив его в следующей редакции:"));
        assertEquals(memo.getAssignmentText(),memo.getAgreementText());
        assertFalse(memo.getAssignmentText().contains("Прошу Вас согласовать"));
        assertFalse(memo.getAgreementText().contains("старой редакции"));
    }

    @Test void legacyClause24DraftIsRebuiltBeforeDownloadAndIssue() throws Exception {
        HrServiceMemo legacy=new HrServiceMemo();legacy.setId(50L);legacy.setStatus(HrServiceMemo.Status.DRAFT);
        legacy.setAcademicYear("2025/2026");legacy.setTeacherId(1L);legacy.setContractId(10L);
        legacy.setAssignmentName("Заведование кабинетом технологии");legacy.setContractClause("2.4");
        legacy.setAmount(new BigDecimal("15000"));legacy.setValidFrom(LocalDate.of(2025,9,1));
        legacy.setValidTo(LocalDate.of(2026,8,31));legacy.setAssignmentText("Прошу Вас согласовать старый текст");
        legacy.setAgreementText("Изложить пункт 2.4 трудового договора");
        when(memos.findById(50L)).thenReturn(Optional.of(legacy));

        HrServiceMemo downloaded=service.memoForDownload(50L);
        HrServiceMemo issued=service.memoStatus(50L,HrServiceMemo.Status.ISSUED,"deputy");

        assertTrue(downloaded.getAssignmentText().startsWith(
                "Внести изменения в пункт 2.4 раздела 2 «Оплата труда»"));
        assertEquals(downloaded.getAssignmentText(),downloaded.getAgreementText());
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(issued.getDocumentContent()))){
            String text=document.getParagraphs().stream().map(XWPFParagraph::getText)
                    .reduce("",(left,right)->left+"\n"+right);
            assertTrue(text.contains("Внести изменения в пункт 2.4 раздела 2 «Оплата труда»"));
            assertFalse(text.contains("Прошу Вас согласовать старый текст"));
        }
    }

    @Test void agreementDownloadNameContainsEmployeeNumberAndDocumentDate() {
        AdditionalAgreement agreement=draftAgreement();
        agreement.setInternalNumber("1 / 2025-2026");
        agreement.setDocumentDate(LocalDate.of(2025,9,1));

        assertEquals("Иванов Иван Иванович доп согл № 1 от 01.09.2025.docx",
                service.agreementDownloadFilename(agreement));
    }

    @Test void receivingDutyMemoReleasesLinkedAgreementDraft(){
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.SIGNED);
        AdditionalAgreement agreement=new AdditionalAgreement();agreement.setStatus(AdditionalAgreement.Status.WAITING_FOR_MEMO);
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(agreements.findAllByServiceMemoId(50L)).thenReturn(List.of(agreement));

        service.memoStatus(50L,HrServiceMemo.Status.RECEIVED_BY_HR,"hr");

        assertEquals(AdditionalAgreement.Status.DRAFT,agreement.getStatus());
        assertEquals(HrServiceMemo.Status.RECEIVED_BY_HR,memo.getStatus());
    }

    @Test void hrCannotReceiveDutyMemoBeforeDirectorSignature(){
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.ISSUED);
        when(memos.findById(50L)).thenReturn(Optional.of(memo));

        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.memoStatus(50L,HrServiceMemo.Status.RECEIVED_BY_HR,"hr"));

        assertTrue(error.getReason().contains("подписанную"));
        assertEquals(HrServiceMemo.Status.ISSUED,memo.getStatus());
    }

    @Test void editingIssuedDutyMemoUpdatesLinkedDraftAndRequiresReissue(){
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.ISSUED);
        memo.setAcademicYear("2025/2026");memo.setTeacherId(1L);memo.setContractId(10L);
        memo.setTitle("Заведование кабинетом");memo.setAssignmentName("Заведование кабинетом");
        memo.setAssignmentText("Старый автоматический текст");memo.setAgreementText("Старый текст соглашения");
        memo.setContractClause("2.4");memo.setAmount(new BigDecimal("15000"));
        memo.setValidFrom(LocalDate.of(2025,9,1));memo.setValidTo(LocalDate.of(2026,8,31));
        memo.setDocumentDate(LocalDate.of(2025,9,1));memo.setDocumentFilename("memo.docx");
        memo.setDocumentContent(new byte[]{1,2,3});memo.setIssuedBy("deputy");
        AdditionalAgreement linked=new AdditionalAgreement();linked.setId(100L);
        linked.setStatus(AdditionalAgreement.Status.DRAFT);linked.setServiceMemoId(50L);
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(agreements.findAllByServiceMemoId(50L)).thenReturn(List.of(linked));
        when(versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("SERVICE_MEMO",50L))
                .thenReturn(List.of());
        MemoRequest update=new MemoRequest("2025/2026",1L,10L,null,null,LocalDate.of(2025,9,1),
                "Заведование кабинетом",memo.getAssignmentText(),memo.getAgreementText(),"2.4",null,
                new BigDecimal("17000"),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),
                false,false,null);

        HrServiceMemo edited=service.editMemo(50L,update,"director");

        assertEquals(HrServiceMemo.Status.DRAFT,edited.getStatus());
        assertNull(edited.getIssuedBy());
        assertNull(edited.getSignedAt());
        assertEquals(new BigDecimal("17000"),linked.getTotalAmount());
        assertEquals(AdditionalAgreement.Status.WAITING_FOR_MEMO,linked.getStatus());
        assertTrue(edited.getAssignmentText().contains("17 000"));
        verify(versions).save(argThat(version->"SERVICE_MEMO".equals(version.getDocumentType())
                &&"ISSUED_BEFORE_EDIT".equals(version.getSource())));
    }

    @Test void annullingAgreementArchivesLinkedServiceMemosWithReason(){
        HrServiceMemo duty=new HrServiceMemo();duty.setId(50L);duty.setStatus(HrServiceMemo.Status.RECEIVED_BY_HR);
        ServiceMemo load=new ServiceMemo();load.setId(60L);load.setStatus(ServiceMemo.Status.RECEIVED_BY_HR);
        AdditionalAgreement agreement=new AdditionalAgreement();agreement.setId(100L);
        agreement.setInternalNumber("1 / 2025-2026");agreement.setServiceMemoId(50L);
        agreement.setLoadServiceMemoId(60L);agreement.setStatus(AdditionalAgreement.Status.ISSUED);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(memos.findById(50L)).thenReturn(Optional.of(duty));
        when(loadMemos.findById(60L)).thenReturn(Optional.of(load));

        service.annulAgreement(100L,"ошибочная сумма","hr");

        assertEquals(AdditionalAgreement.Status.ANNULLED,agreement.getStatus());
        assertEquals(HrServiceMemo.Status.ARCHIVED,duty.getStatus());
        assertEquals(ServiceMemo.Status.ARCHIVED,load.getStatus());
        assertTrue(duty.getArchiveReason().contains("1 / 2025-2026"));
        assertTrue(load.getArchiveReason().contains("ошибочная сумма"));
        verify(memos).save(duty);
        verify(loadMemos).save(load);
    }

    @Test void dutyMemoLifecycleEndsWithIssuableAgreementDocxWithoutManualMemoId() {
        List<AdditionalAgreement> storedAgreements=new ArrayList<>();
        doAnswer(invocation->{
            AdditionalAgreement agreement=invocation.getArgument(0);
            if(agreement.getId()==null)agreement.setId(100L);
            if(!storedAgreements.contains(agreement))storedAgreements.add(agreement);
            return agreement;
        }).when(agreements).save(any());
        when(agreements.findAllByServiceMemoId(50L)).thenAnswer(invocation->storedAgreements.stream()
                .filter(agreement->Objects.equals(agreement.getServiceMemoId(),50L)).toList());
        when(agreements.findById(100L)).thenAnswer(invocation->storedAgreements.stream()
                .filter(agreement->Objects.equals(agreement.getId(),100L)).findFirst());
        MemoRequest request=new MemoRequest("2025/2026",1L,10L,null,null,LocalDate.of(2025,9,1),
                "Заведование кабинетом",null,null,"2.4","Контролирует состояние кабинета",
                new BigDecimal("15000"),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),true,false,null);

        HrServiceMemo memo=service.createMemo(request,"deputy");
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        AdditionalAgreement linked=storedAgreements.get(0);
        assertEquals(50L,linked.getServiceMemoId());
        assertEquals(AdditionalAgreement.Status.WAITING_FOR_MEMO,linked.getStatus());

        service.memoStatus(50L,HrServiceMemo.Status.ISSUED,"deputy","Заместитель Директора","заместителя директора");
        service.memoStatus(50L,HrServiceMemo.Status.SIGNED,"director","Директор","директора");
        service.memoStatus(50L,HrServiceMemo.Status.RECEIVED_BY_HR,"hr","Кадровик","специалиста по кадрам");
        assertEquals(AdditionalAgreement.Status.DRAFT,linked.getStatus());

        service.editAgreement(linked.getId(),new AgreementEditRequest(10L,LocalDate.of(2025,9,2),
                linked.getValidFrom(),linked.getValidTo(),linked.getSummary(),linked.getConditionsJson(),
                linked.getTotalAmount(),false,null),"hr");
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));
        AdditionalAgreement prepared=service.prepare(linked.getId(),"hr");
        AdditionalAgreement issued=service.issue(linked.getId(),"hr");

        assertNotNull(prepared.getCurrentDocument());
        assertEquals(AdditionalAgreement.Status.ISSUED,issued.getStatus());
        assertEquals(50L,issued.getServiceMemoId());
    }

    @Test void dutyMemoCanBeCreatedBeforeEmploymentContractIsFilled(){
        MemoRequest request=new MemoRequest("2025/2026",1L,null,null,null,LocalDate.of(2025,9,1),"Кабинет",
                "Назначить ответственным",null,"2.4",null,new BigDecimal("5000"),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),false,false,null);

        HrServiceMemo memo=service.createMemo(request,"deputy");

        assertNull(memo.getContractId());
        assertNotNull(memo.getDocumentContent());
        verify(agreements,atLeastOnce()).save(argThat(a->Objects.equals(a.getTeacherId(),1L)
                &&a.getContractId()==null&&a.getStatus()==AdditionalAgreement.Status.WAITING_FOR_MEMO));
    }

    @Test void savingPrimaryContractAttachesMemosCreatedEarlier(){
        HrServiceMemo duty=new HrServiceMemo(); duty.setId(51L); duty.setTeacherId(1L); duty.setAcademicYear("2025/2026");
        duty.setAssignmentName("Заведование кабинетом"); duty.setAgreementText("Изложить пункт 2.4"); duty.setAmount(new BigDecimal("5000"));
        duty.setValidFrom(LocalDate.of(2025,9,1)); duty.setValidTo(LocalDate.of(2026,8,31)); duty.setCreatedBy("deputy");
        ServiceMemo load=new ServiceMemo(); load.setId(61L); load.setTeacherId(1L); load.setAcademicYear("2025/2026");
        load.setChangeStartDate(LocalDate.of(2025,10,1)); load.setCreatedBy("deputy");
        when(memos.findAllByTeacherIdAndContractIdIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of(duty));
        when(loadMemos.findAllByTeacherIdAndContractIdIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of(load));
        when(agreements.findAllByServiceMemoId(51L)).thenReturn(List.of());
        when(agreements.findAllByLoadServiceMemoId(61L)).thenReturn(List.of());

        service.saveContract(10L,new org.school.personalLoad.dto.HrDocumentDtos.ContractRequest(1L,"1-ТД",
                LocalDate.of(2025,1,1),"Учитель",LocalDate.of(2025,1,1),null,true,true,
                false,null,null));

        assertEquals(10L,duty.getContractId());
        assertEquals(10L,load.getContractId());
        verify(agreements,atLeastOnce()).save(argThat(a->Objects.equals(a.getServiceMemoId(),51L)));
        verify(agreements,atLeastOnce()).save(argThat(a->Objects.equals(a.getLoadServiceMemoId(),61L)));
    }

    @Test void loadMemoCreatesWaitingAgreementLinkedByMemoId(){
        ServiceMemo memo=new ServiceMemo();memo.setId(60L);memo.setAcademicYear("2025/2026");
        memo.setTeacherId(1L);
        memo.setChangeStartDate(LocalDate.of(2025,10,1));memo.setCreatedAt(java.time.LocalDateTime.of(2025,9,25,10,0));

        AdditionalAgreement agreement=service.createLoadChangeDraft(memo,contract,"deputy");

        assertEquals(60L,agreement.getLoadServiceMemoId());
        assertEquals(AdditionalAgreement.Status.WAITING_FOR_MEMO,agreement.getStatus());
        assertEquals(AdditionalAgreement.Kind.PAY_TERMS,agreement.getKind());
    }

    @Test void lobBackedListsAreReadInsideReadOnlyTransactions() throws Exception {
        Transactional catalogTransaction=HrDocumentService.class.getMethod("catalog").getAnnotation(Transactional.class);
        Transactional memoTransaction=HrDocumentService.class.getMethod("memos",String.class).getAnnotation(Transactional.class);

        assertNotNull(catalogTransaction);
        assertTrue(catalogTransaction.readOnly());
        assertNotNull(memoTransaction);
        assertFalse(memoTransaction.readOnly());
    }

    @Test void issuedMemoUsesSchoolTemplateAndIssuingAccountAsSigner() throws Exception {
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.DRAFT);
        memo.setAssignmentName("Заведование кабинетом");memo.setAssignmentText("Прошу Вас согласовать заведование кабинетом.");
        memo.setDocumentDate(LocalDate.of(2026,9,1));when(memos.findById(50L)).thenReturn(Optional.of(memo));

        HrServiceMemo issued=service.memoStatus(50L,HrServiceMemo.Status.ISSUED,"belyaev","Беляев Д.А.","старшего методиста");

        assertEquals("Беляев Д.А.",issued.getIssuedByFullName());
        assertEquals("старшего методиста",issued.getIssuedByPosition());
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(issued.getDocumentContent()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(a,b)->a+"\n"+b);
            assertTrue(text.contains("Директору"));
            assertTrue(text.contains("от старшего методиста"));
            assertTrue(text.contains("Беляев Д.А."));
            assertTrue(text.contains("01.09.2026"));
            assertTrue(text.contains("Прошу Вас согласовать заведование кабинетом."));
            assertFalse(text.contains("О назначении:"));
            assertTrue(document.getParagraphs().stream().flatMap(paragraph->paragraph.getRuns().stream())
                    .filter(run->run.text()!=null&&!run.text().isBlank())
                    .allMatch(run->run.getFontSize()>=14),
                    "Текст служебной записки должен быть не мельче 14 пунктов");
        }
    }

    @Test void catalogDeleteHidesPositionInsteadOfBreakingOldMemos() {
        HrCatalogItem item=new HrCatalogItem();item.setId(7L);item.setSchoolCode(org.school.personalLoad.config.SchoolCodeResolver.resolve());item.setActive(true);
        when(catalog.findById(7L)).thenReturn(Optional.of(item));

        service.deleteCatalogItem(7L);

        assertFalse(item.isActive());
        verify(catalog).save(item);
    }

    @Test void onlyAnnulledMemoCanBeDeleted() {
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.ANNULLED);
        when(memos.findById(50L)).thenReturn(Optional.of(memo));when(agreements.findAllByServiceMemoId(50L)).thenReturn(List.of());

        service.deleteAnnulledMemo(50L,"hr");

        verify(memos).delete(memo);
    }

    @Test void memoCanBeDeletedWhenItsAgreementWasRejected() {
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.RECEIVED_BY_HR);
        AdditionalAgreement agreement=new AdditionalAgreement();agreement.setId(70L);agreement.setServiceMemoId(50L);
        agreement.setStatus(AdditionalAgreement.Status.REJECTED);
        when(memos.findById(50L)).thenReturn(Optional.of(memo));when(agreements.findAllByServiceMemoId(50L)).thenReturn(List.of(agreement));

        service.deleteAnnulledMemo(50L,"hr");

        verify(agreements).delete(agreement);
        verify(memos).delete(memo);
    }

    @Test void schoolSevenUsesFirstFreeNumberAndIgnoresAnnulledDrafts() {
        AdditionalAgreement annulled=new AdditionalAgreement();annulled.setId(80L);annulled.setAcademicYear("2025/2026");
        annulled.setInternalNumber("1 / 2025-2026");annulled.setStatus(AdditionalAgreement.Status.ANNULLED);
        AdditionalAgreement active=new AdditionalAgreement();active.setId(81L);active.setAcademicYear("2025/2026");
        active.setInternalNumber("2 / 2025-2026");active.setStatus(AdditionalAgreement.Status.DRAFT);
        when(agreements.findAllByContractIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(active,annulled));

        AdditionalAgreement created=service.createAgreement(request(null),"hr");

        assertEquals("1 / 2025-2026",created.getInternalNumber());
    }

    @Test void annualLoadAgreementIsCreatedWithoutEmploymentContract() {
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(1);load.setNumberSchoolBuilding("1");
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of());
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(new ArrayList<>());

        var created=service.createAnnualDrafts(new BatchAgreementRequest("2025/2026",LocalDate.of(2025,9,1),null,List.of(),null),"hr");

        assertEquals(1,created.size());assertEquals(1L,created.get(0).getTeacherId());assertNull(created.get(0).getContractId());
        assertTrue(created.get(0).getInternalNumber().startsWith("БД-1-"));
    }

    @Test void annualAgreementIncludesClassroomLeadershipWithoutCabinetMemo() {
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(1);load.setNumberSchoolBuilding("1");
        ClassroomLeadershipEntry classroom=new ClassroomLeadershipEntry();classroom.setAcademicYear("2025/2026");
        classroom.setClassName("5 А");classroom.setTeacher(teacher);
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(classroomLeadership.findAllByAcademicYear("2025/2026")).thenReturn(List.of(classroom));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5-а",25));
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of());
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(new ArrayList<>());

        AdditionalAgreement created=service.createAnnualDrafts(new BatchAgreementRequest("2025/2026",
                LocalDate.of(2025,9,1),null,List.of(),null),"hr").get(0);

        assertTrue(created.getConditionsJson().contains("пункт 2.1"));
        assertTrue(created.getConditionsJson().contains("пункт 2.4"));
        assertTrue(created.getConditionsJson().contains("возложена функция классного руководителя"));
        assertTrue(created.getConditionsJson().contains("17 500 рублей 00 коп."));
        assertTrue(created.getSummary().contains("Нагрузка и должностной оклад"));
        assertTrue(created.getSummary().contains("Классное руководство"));
        assertTrue(created.isRegistryManaged());
        verify(memos,never()).save(any());
    }

    @Test void annualAgreementPlacesIupOnlyInClause24AndKeepsCoreSalarySeparate() {
        ManualLoadEntry core=new ManualLoadEntry();core.setId(11L);core.setTeacherId(1L);
        core.setAcademicYear("2025/2026");core.setSubjectName("Математика");
        core.setClassName("5А");core.setLoad(1);core.setNumberSchoolBuilding("1");

        ManualLoadEntry iup=new ManualLoadEntry();iup.setId(12L);iup.setTeacherId(1L);
        iup.setAcademicYear("2025/2026");iup.setSubjectName("Математика");
        iup.setClassName("ИУП-5-А-Иванов И.И.");iup.setLoad(2);
        iup.setPreciseLoadHours(new BigDecimal("2.00"));iup.setLoadSource(ManualLoadSource.IUP);
        iup.setIupStudentCategory(StudentCategory.K2);iup.setNumberSchoolBuilding("1");

        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(core,iup));
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L))
                .thenReturn(List.of());
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026"))
                .thenReturn(new ArrayList<>());

        AdditionalAgreement created=service.createAnnualDrafts(new BatchAgreementRequest(
                "2025/2026",LocalDate.of(2025,9,1),null,List.of(),null
        ),"hr").get(0);

        assertTrue(created.getConditionsJson().contains("пункт 2.1"));
        assertTrue(created.getConditionsJson().contains("пункт 2.4"));
        assertTrue(created.getConditionsJson().contains(
                "возложена работа с обучающимися с ограниченными возможностями здоровья, "
                        +"детьми-инвалидами, в размере 70 рублей 83 коп. "
                        +"(семьдесят рублей 83 коп.) в месяц"
        ));
        assertFalse(created.getConditionsJson().substring(
                0,
                created.getConditionsJson().indexOf("Внести изменения в пункт 2.4")
        ).contains("ИУП-5-А-Иванов"));
        assertEquals(new BigDecimal("3145.00"),created.getTotalAmount(),
                "Пункт 2.1 содержит только основную нагрузку; ИУП рассчитан отдельно для пункта 2.4");
        assertTrue(created.getSummary().contains("Работа с обучающимися с ОВЗ, детьми-инвалидами"));
    }

    @Test void iupOnlyAnnualAgreementDoesNotCreateClause21OrLoadSalary() {
        ManualLoadEntry iup=new ManualLoadEntry();iup.setId(12L);iup.setTeacherId(1L);
        iup.setAcademicYear("2025/2026");iup.setSubjectName("Математика");
        iup.setClassName("ИУП-5-А-Иванов И.И.");iup.setLoad(2);
        iup.setPreciseLoadHours(new BigDecimal("2.00"));iup.setLoadSource(ManualLoadSource.IUP);
        iup.setIupStudentCategory(StudentCategory.NORMAL);iup.setNumberSchoolBuilding("1");
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(iup));
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L))
                .thenReturn(List.of());
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026"))
                .thenReturn(new ArrayList<>());

        AdditionalAgreement created=service.createAnnualDrafts(new BatchAgreementRequest(
                "2025/2026",LocalDate.of(2025,9,1),null,List.of(),null
        ),"hr").get(0);

        assertFalse(created.getConditionsJson().contains("пункт 2.1"));
        assertTrue(created.getConditionsJson().contains("пункт 2.4"));
        assertTrue(created.getConditionsJson().contains("5 рублей 67 коп."));
        assertNull(created.getTotalAmount());
    }

    @Test void iupRefreshKeepsMergedClause24FunctionAndMarksIssuedAgreementForReissue() {
        ManualLoadEntry iup=new ManualLoadEntry();iup.setId(12L);iup.setTeacherId(1L);
        iup.setAcademicYear("2025/2026");iup.setSubjectName("Математика");
        iup.setClassName("ИУП-5-А-Иванов И.И.");iup.setPreciseLoadHours(new BigDecimal("2.00"));
        iup.setLoadSource(ManualLoadSource.IUP);iup.setIupStudentCategory(StudentCategory.K2);

        AdditionalAgreement annual=draftAgreement();
        annual.setRegistryManaged(true);
        annual.setServiceMemoId(50L);
        annual.setStatus(AdditionalAgreement.Status.ISSUED);
        annual.setSummary("Нагрузка и должностной оклад · Заведование кабинетом");

        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setTeacherId(1L);
        memo.setAcademicYear("2025/2026");memo.setAssignmentName("Заведование кабинетом");
        memo.setContractClause("2.4");memo.setAmount(new BigDecimal("5000.00"));

        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(iup));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026"))
                .thenReturn(List.of(annual));
        when(memos.findById(50L)).thenReturn(Optional.of(memo));

        service.markAnnualIupAgreementsChanged("2025/2026",Set.of(1L));

        assertTrue(annual.getConditionsJson().toLowerCase(Locale.ROOT)
                .contains("заведование кабинетом"));
        assertTrue(annual.getConditionsJson().contains(
                "возложена работа с обучающимися с ограниченными возможностями здоровья, детьми-инвалидами"
        ));
        assertTrue(annual.getSummary().contains("Работа с обучающимися с ОВЗ, детьми-инвалидами"));
        assertTrue(annual.isReissueRequired());
        verify(agreements).save(annual);
    }

    @Test void repeatedAnnualGenerationAddsClassroomLeadershipToExistingDraft() {
        AdditionalAgreement annual=draftAgreement();annual.setSummary("Нагрузка и должностной оклад");
        ClassroomLeadershipEntry classroom=new ClassroomLeadershipEntry();classroom.setAcademicYear("2025/2026");
        classroom.setClassName("5 А");classroom.setTeacher(teacher);
        when(classroomLeadership.findAllByAcademicYear("2025/2026")).thenReturn(List.of(classroom));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5-а",20));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(annual));

        List<AdditionalAgreement> created=service.createAnnualDrafts(new BatchAgreementRequest("2025/2026",
                LocalDate.of(2025,9,1),null,List.of(),null),"hr");

        assertTrue(created.isEmpty());
        assertTrue(annual.getConditionsJson().contains("пункт 2.4"));
        assertTrue(annual.getConditionsJson().contains("15 000 рублей 00 коп."));
        assertTrue(annual.getSummary().contains("Классное руководство"));
        assertTrue(annual.isRegistryManaged());
        verify(agreements).save(annual);
    }

    @Test void repeatedAnnualGenerationKeepsFirstDraftAndDeletesOnlyDuplicateUnissuedRegistryDraft() {
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(1);
        AdditionalAgreement first=draftAgreement();first.setId(101L);first.setInternalNumber("1 / 2025-2026");
        first.setRegistryManaged(true);
        AdditionalAgreement duplicate=draftAgreement();duplicate.setId(102L);duplicate.setInternalNumber("2 / 2025-2026");
        duplicate.setRegistryManaged(true);
        List<AdditionalAgreement> existing=new ArrayList<>(List.of(duplicate,first));
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(existing);

        List<AdditionalAgreement> created=service.createAnnualDrafts(new BatchAgreementRequest("2025/2026",
                LocalDate.of(2025,9,1),null,List.of(),null),"hr");

        assertTrue(created.isEmpty());
        verify(agreements).delete(duplicate);
        verify(agreements,never()).delete(first);
        verify(versions).deleteAll(anyList());
    }

    @Test void annualAgreementIsCreatedAgainWhenAllPreviousAgreementsAreAnnulled() {
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(2);
        ClassroomLeadershipEntry classroom=new ClassroomLeadershipEntry();classroom.setAcademicYear("2025/2026");
        classroom.setClassName("5 А");classroom.setTeacher(teacher);
        HrIncentive incentive=new HrIncentive();incentive.setAcademicYear("2025/2026");
        incentive.setTeacherId(1L);incentive.setAmount(new BigDecimal("7000"));
        AdditionalAgreement annulled=draftAgreement();annulled.setId(101L);
        annulled.setInternalNumber("1 / 2025-2026");annulled.setRegistryManaged(true);
        annulled.setStatus(AdditionalAgreement.Status.ANNULLED);
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(classroomLeadership.findAllByAcademicYear("2025/2026")).thenReturn(List.of(classroom));
        when(incentives.findAllByAcademicYear("2025/2026")).thenReturn(List.of(incentive));
        when(incentives.findByAcademicYearAndTeacherId("2025/2026",1L)).thenReturn(Optional.of(incentive));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026"))
                .thenReturn(new ArrayList<>(List.of(annulled)));
        when(agreements.findAllByContractIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(annulled));
        when(agreements.findById(101L)).thenReturn(Optional.of(annulled));
        contract.setPrimaryContract(true);contract.setActive(true);
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L))
                .thenReturn(List.of(contract));

        List<AdditionalAgreement> created=service.createAnnualDrafts(new BatchAgreementRequest("2025/2026",
                LocalDate.of(2025,9,1),null,List.of(),null),"hr");

        assertEquals(1,created.size());
        AdditionalAgreement replacement=created.get(0);
        assertEquals(AdditionalAgreement.Status.DRAFT,replacement.getStatus());
        assertEquals("1 / 2025-2026",replacement.getInternalNumber());
        assertEquals(2,replacement.getRevision());
        assertEquals(101L,replacement.getReplacesAgreementId());
        assertTrue(replacement.isRegistryManaged());
        assertTrue(replacement.getConditionsJson().contains("пункт 2.1"));
        assertTrue(replacement.getConditionsJson().contains("пункт 2.4"));
        assertTrue(replacement.getConditionsJson().contains("пункт 2.5"));
        assertNotEquals(annulled.getId(),replacement.getId());
    }

    @Test void teachersWithLoadAreAutomaticallyAddedToIncentiveTableByForeignKey() {
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setLoad(2);when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));

        var rows=service.incentiveRows("2025/2026","hr");

        assertEquals(1,rows.size());assertEquals(1L,rows.get(0).teacherId());
        assertEquals(BigDecimal.ZERO.setScale(2),rows.get(0).amount());assertTrue(rows.get(0).hasLoad());
        verify(incentives).save(argThat(item->Objects.equals(item.getTeacherId(),1L)
                &&Objects.equals(item.getAcademicYear(),"2025/2026")));
    }

    @Test void nonZeroIncentiveIsIncludedInAnnualAgreementWithoutChangingMemos() {
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(1);load.setNumberSchoolBuilding("1");
        HrIncentive incentive=new HrIncentive();incentive.setAcademicYear("2025/2026");incentive.setTeacherId(1L);
        incentive.setAmount(new BigDecimal("7500"));
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(incentives.findByAcademicYearAndTeacherId("2025/2026",1L)).thenReturn(Optional.of(incentive));
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of());
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(new ArrayList<>());

        AdditionalAgreement created=service.createAnnualDrafts(new BatchAgreementRequest("2025/2026",
                LocalDate.of(2025,9,1),null,List.of(),null),"hr").get(0);

        assertTrue(created.getConditionsJson().contains("пункт 2.5"));
        assertTrue(created.getConditionsJson().contains("Устанавливаются ежемесячные стимулирующие выплаты за результаты обучающихся по итогам учебного года"));
        assertTrue(created.getConditionsJson().contains("7 500 рублей 00 коп."));
        assertTrue(created.getConditionsJson().contains("Внести изменения в пункт 2.1. раздела 2 «Оплата труда»"));
        assertTrue(created.getConditionsJson().contains("За исполнение трудовых (должностных) обязанностей"));
        assertTrue(created.getSummary().contains("Стимулирующая выплата"));
        verify(memos,never()).save(any());
    }

    @Test void manuallyAddedTeacherWithoutLoadReceivesIncentiveOnlyAnnualAgreement() {
        HrIncentive incentive=new HrIncentive();incentive.setAcademicYear("2025/2026");incentive.setTeacherId(1L);
        incentive.setAmount(new BigDecimal("6000"));
        when(incentives.findAllByAcademicYear("2025/2026")).thenReturn(List.of(incentive));
        when(incentives.findByAcademicYearAndTeacherId("2025/2026",1L)).thenReturn(Optional.of(incentive));
        when(contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(1L)).thenReturn(List.of());
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(new ArrayList<>());

        AdditionalAgreement created=service.createAnnualDrafts(new BatchAgreementRequest("2025/2026",
                LocalDate.of(2025,9,1),null,List.of(),null),"hr").get(0);

        assertEquals("Стимулирующая выплата",created.getSummary());
        assertTrue(created.getConditionsJson().contains("пункт 2.5"));
        assertFalse(created.getConditionsJson().contains("пункт 2.1"));
        assertNull(created.getTotalAmount());
    }

    @Test void changingIncentiveUpdatesExistingUnissuedAnnualAgreementButNotServiceMemo() {
        AdditionalAgreement annual=draftAgreement();annual.setSummary("Нагрузка и должностной оклад");
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(annual));

        service.saveIncentive("2025/2026",1L,new BigDecimal("8100"),"hr");

        assertTrue(annual.getConditionsJson().contains("пункт 2.5"));
        assertTrue(annual.getConditionsJson().contains("8 100 рублей 00 коп."));
        verify(agreements).save(annual);
        verify(memos,never()).save(any());
    }

    @Test void registryIncentiveUpdatesOnlyAnnualRegistryAgreementAndNotSeparateMemoAgreement() {
        AdditionalAgreement annual=draftAgreement();annual.setId(101L);
        AdditionalAgreement cabinet=draftAgreement();cabinet.setId(102L);cabinet.setServiceMemoId(50L);
        cabinet.setSummary("Заведование кабинетом");
        cabinet.setConditionsJson("Внести изменения в пункт 2.4 раздела 2 «Оплата труда».");
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026"))
                .thenReturn(List.of(annual,cabinet));

        service.saveIncentive("2025/2026",1L,new BigDecimal("8100"),"hr");

        assertTrue(annual.getConditionsJson().contains("пункт 2.5"));
        assertTrue(annual.getSummary().contains("Стимулирующая выплата"));
        assertFalse(cabinet.getConditionsJson().contains("2.5"));
        assertEquals("Заведование кабинетом",cabinet.getSummary());
        verify(agreements).save(annual);
        verify(agreements,never()).save(cabinet);
    }

    @Test void agreementListRepairsPreviouslyMisappliedIncentiveInSeparateMemoDraft() {
        AdditionalAgreement cabinet=draftAgreement();cabinet.setId(102L);cabinet.setServiceMemoId(50L);
        cabinet.setSummary("Заведование кабинетом · Стимулирующая выплата");
        cabinet.setConditionsJson("Внести изменения в пункт 2.4 раздела 2 «Оплата труда».\n\n"
                +"Внести изменения в пункт 2.5 раздела 2 «Оплата труда»: стимулирующая выплата 8 100 рублей.");
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setContractClause("2.4");
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(cabinet));

        var rows=service.agreementRows("2025/2026");

        assertEquals(1,rows.size());
        assertFalse(cabinet.getConditionsJson().contains("2.5"));
        assertEquals("Заведование кабинетом",cabinet.getSummary());
        assertFalse(rows.get(0).agreement().registryManaged());
        verify(agreements).save(cabinet);
    }

    @Test void agreementListRemovesRegistryClassroomLeadershipFromExistingCabinetMemoDraft() {
        AdditionalAgreement cabinet=draftAgreement();cabinet.setId(104L);cabinet.setServiceMemoId(50L);
        cabinet.setSummary("Заведование кабинетом");
        cabinet.setConditionsJson("Внести изменения в пункт 2.4 раздела 2 \"Оплата труда\", изложив его в следующей редакции:\n"
                +"\"2.4. Работнику выплачиваются ежемесячные компенсационные выплаты при условии, если на Работника:\n"
                +"- возложена функция классного руководителя, в размере 16 500 рублей 00 коп. в месяц;\n"
                +"- возложена функция \"заведование кабинетом\", в размере 10 000 рублей 00 коп. в месяц\".");
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setContractClause("2.4");
        memo.setAssignmentName("Заведование кабинетом");memo.setAmount(new BigDecimal("10000"));
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(cabinet));

        service.agreementRows("2025/2026");

        assertFalse(cabinet.getConditionsJson().contains("классного руководителя"));
        assertTrue(cabinet.getConditionsJson().contains("«заведование кабинетом»"));
        verify(agreements).save(cabinet);
    }

    @Test void agreementListKeepsIncentiveInLegacyExplicitlyMergedRegistryDocument() {
        AdditionalAgreement merged=draftAgreement();merged.setId(103L);merged.setServiceMemoId(50L);
        merged.setSummary("Нагрузка · Заведование кабинетом · Стимулирующая выплата");
        merged.setConditionsJson("Изложить пункт 2.1 раздела 2 «Оплата труда».\n\n"
                +"Внести изменения в пункт 2.4 раздела 2 «Оплата труда».\n\n"
                +"Внести изменения в пункт 2.5 раздела 2 «Оплата труда»: стимулирующая выплата 8 100 рублей.");
        merged.setSourceSnapshotJson("{\"mergedAgreementIds\":[1,2]}");
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(merged));

        var rows=service.agreementRows("2025/2026");

        assertTrue(merged.getConditionsJson().contains("2.5"));
        assertTrue(rows.get(0).agreement().registryManaged());
        verify(agreements,never()).save(merged);
    }

    @Test void changingIncentiveMarksIssuedUnsignedAgreementForReissueAndKeepsOldFile() {
        AdditionalAgreement annual=draftAgreement();annual.setSummary("Нагрузка и должностной оклад");
        annual.setStatus(AdditionalAgreement.Status.ISSUED);
        annual.setIssuedAt(java.time.LocalDateTime.of(2026,7,27,10,0));
        annual.setCurrentDocument(new byte[]{1,2,3});annual.setGeneratedDocument(new byte[]{1,2,3});
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(annual));

        service.saveIncentive("2025/2026",1L,new BigDecimal("8100"),"hr");

        assertTrue(annual.getConditionsJson().contains("пункт 2.5"));
        assertTrue(annual.getConditionsJson().contains("8 100 рублей 00 коп."));
        assertEquals(AdditionalAgreement.Status.ISSUED,annual.getStatus());
        assertTrue(annual.isReissueRequired());
        assertArrayEquals(new byte[]{1,2,3},annual.getCurrentDocument(),
                "Старая выпущенная редакция хранится до подтверждённого перевыпуска");
        assertTrue(service.agreementView(annual).reissueRequired());
        verify(agreements).save(annual);
    }

    @Test void incentiveExcelExportAndImportUseTeacherId() throws Exception {
        HrIncentive incentive=new HrIncentive();incentive.setId(200L);incentive.setAcademicYear("2025/2026");
        incentive.setTeacherId(1L);incentive.setAmount(new BigDecimal("3500.00"));
        when(incentives.findAllByAcademicYear("2025/2026")).thenReturn(List.of(incentive));

        byte[] exported=service.exportIncentives("2025/2026","hr");
        try(Workbook workbook=new XSSFWorkbook(new ByteArrayInputStream(exported))){
            assertEquals("ID педагога",workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
            assertEquals(1D,workbook.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue());
            assertEquals(3500D,workbook.getSheetAt(0).getRow(1).getCell(3).getNumericCellValue());
        }

        byte[] imported;
        try(Workbook workbook=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            var sheet=workbook.createSheet("Стимул");sheet.createRow(0);
            var row=sheet.createRow(1);row.createCell(1).setCellValue(1);row.createCell(2).setCellValue(teacher.getFioTeacher());
            row.createCell(3).setCellValue(9200.50);workbook.write(out);imported=out.toByteArray();
        }
        when(incentives.findByAcademicYearAndTeacherId("2025/2026",1L)).thenReturn(Optional.of(incentive));
        var result=service.importIncentives("2025/2026",
                new MockMultipartFile("file","stimulus.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",imported),"hr");

        assertEquals(1,result.updated());assertEquals(0,result.skipped());
        assertEquals(new BigDecimal("9200.50"),incentive.getAmount());
    }

    @Test void agreementListIncludesDocumentsFromInactiveContracts() {
        contract.setActive(false);AdditionalAgreement agreement=new AdditionalAgreement();agreement.setId(90L);agreement.setContractId(10L);
        agreement.setAcademicYear("2025/2026");agreement.setInternalNumber("1 / 2025-2026");agreement.setValidFrom(LocalDate.of(2025,9,1));agreement.setValidTo(LocalDate.of(2026,8,31));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(agreement));

        var rows=service.agreementRows("2025/2026");

        assertEquals(1,rows.size());assertEquals(10L,rows.get(0).contractId());assertEquals(teacher.getFioTeacher(),rows.get(0).fio());
    }

    @Test void journalShowsOnlyActiveDocumentsWhileAgreementListKeepsHistory() {
        AdditionalAgreement active=draftAgreement();active.setId(91L);active.setInternalNumber("1 / 2025-2026");
        AdditionalAgreement annulled=draftAgreement();annulled.setId(92L);annulled.setInternalNumber("2 / 2025-2026");
        annulled.setStatus(AdditionalAgreement.Status.ANNULLED);
        when(contracts.findAllByActiveTrueOrderByTeacherIdAsc()).thenReturn(List.of(contract));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(annulled,active));

        var journalRows=service.journal("2025/2026");
        var historyRows=service.agreementRows("2025/2026");

        assertEquals(1,journalRows.size());assertEquals(1,journalRows.get(0).agreements().size());
        assertEquals("1 / 2025-2026",journalRows.get(0).agreements().get(0).internalNumber());
        assertEquals(2,historyRows.size());
    }

    @Test void unissuedManualAgreementCanBeDeletedAndItsNumberFreed() {
        AdditionalAgreement agreement=draftAgreement();
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026")).thenReturn(List.of(agreement));

        service.deleteAgreement(100L);

        verify(agreements).delete(agreement);
        verify(versions).deleteAll(anyList());
    }

    @Test void annulledIssuedAgreementCanBeDeletedAndReplacementLinkIsCleared() {
        AdditionalAgreement annulled=draftAgreement();
        annulled.setStatus(AdditionalAgreement.Status.ANNULLED);
        annulled.setIssuedAt(LocalDateTime.now().minusDays(1));
        annulled.setServiceMemoId(50L);
        AdditionalAgreement replacement=draftAgreement();
        replacement.setId(101L);
        replacement.setReplacesAgreementId(100L);
        HrServiceMemo archivedMemo=new HrServiceMemo();
        archivedMemo.setId(50L);
        archivedMemo.setStatus(HrServiceMemo.Status.ARCHIVED);
        when(agreements.findById(100L)).thenReturn(Optional.of(annulled));
        when(agreements.findAllByAcademicYearOrderByCreatedAtDesc("2025/2026"))
                .thenReturn(List.of(replacement,annulled));
        when(memos.findById(50L)).thenReturn(Optional.of(archivedMemo));

        service.deleteAgreement(100L);

        assertNull(replacement.getReplacesAgreementId());
        verify(agreements).save(replacement);
        verify(agreements).delete(annulled);
        verify(versions).deleteAll(anyList());
    }

    @Test void loadAndCompensationDraftsCanBeMergedIntoOneAgreement() throws Exception {
        AdditionalAgreement load=draftAgreement();load.setId(101L);load.setInternalNumber("3 / 2025-2026");
        load.setTotalAmount(new BigDecimal("50000"));
        AdditionalAgreement function=draftAgreement();function.setId(102L);function.setInternalNumber("4 / 2025-2026");
        function.setSummary("Заведование кабинетом");function.setServiceMemoId(50L);
        function.setConditionsJson("Внести изменения в пункт 2.4. раздела 2 «Оплата труда», изложив его в следующей редакции:\n"
                +"«2.4. Работнику выплачиваются ежемесячные компенсационные выплаты:\n"
                +"- возложена функция «заведование кабинетом», в размере 15 000 рублей 00 коп. в месяц».");
        function.setTotalAmount(new BigDecimal("15000"));
        AdditionalAgreement annulled=new AdditionalAgreement();annulled.setId(90L);annulled.setAcademicYear("2025/2026");
        annulled.setInternalNumber("1 / 2025-2026");annulled.setStatus(AdditionalAgreement.Status.ANNULLED);
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.RECEIVED_BY_HR);
        memo.setAssignmentName("Заведование кабинетом");memo.setAmount(new BigDecimal("15000"));
        memo.setContractClause("2.4");memo.setValidFrom(LocalDate.of(2025,9,1));
        ClassroomLeadershipEntry classroom=new ClassroomLeadershipEntry();classroom.setAcademicYear("2025/2026");
        classroom.setClassName("5 А");classroom.setTeacher(teacher);
        when(agreements.findById(101L)).thenReturn(Optional.of(load));when(agreements.findById(102L)).thenReturn(Optional.of(function));
        when(agreements.findAllByContractIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(function,load,annulled));
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(classroomLeadership.findAllByAcademicYear("2025/2026")).thenReturn(List.of(classroom));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));
        ManualLoadEntry row=new ManualLoadEntry();row.setTeacherId(1L);row.setAcademicYear("2025/2026");
        row.setSubjectName("Математика");row.setClassName("5 А");row.setLoad(2);
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(row));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5-а",25));

        AdditionalAgreement merged=service.mergeAgreements(List.of(101L,102L),"hr");

        assertEquals(101L,merged.getId());assertEquals("1 / 2025-2026",merged.getInternalNumber());
        assertEquals(50L,merged.getServiceMemoId());assertEquals(new BigDecimal("50000"),merged.getTotalAmount());
        assertTrue(merged.isRegistryManaged(),"После явного объединения документ остаётся сводным");
        assertTrue(merged.getConditionsJson().contains("пункт 2.1"));
        assertTrue(merged.getConditionsJson().contains("пункт 2.4"));
        verify(agreements).delete(function);

        AdditionalAgreement prepared=service.prepare(101L,"hr");
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right);
            assertTrue(text.contains("пункт 2.1"));assertTrue(text.contains("пункт 2.4"));
            assertTrue(text.contains("возложена функция классного руководителя"));
            assertTrue(text.contains("возложена функция «заведование кабинетом»"));
            assertTrue(text.contains("Приложение № 1"));
        }
    }

    @Test void issuedUnsignedAgreementCanBeMergedWithNewMemoAndReissued() {
        AdditionalAgreement issued=draftAgreement();issued.setId(201L);issued.setInternalNumber("1 / 2025-2026");
        issued.setConditionsJson("Изложить пункт 2.1 раздела «Оплата труда» в новой редакции.");
        issued.setTotalAmount(new BigDecimal("50000"));issued.setStatus(AdditionalAgreement.Status.ISSUED);
        issued.setRevision(2);issued.setIssuedAt(java.time.LocalDateTime.of(2025,9,1,10,30));
        issued.setIssuedBy("hr");issued.setCurrentDocument(new byte[]{1,2,3});
        issued.setCurrentFilename("issued.docx");
        AdditionalAgreement function=draftAgreement();function.setId(202L);function.setInternalNumber("2 / 2025-2026");
        function.setStatus(AdditionalAgreement.Status.WAITING_FOR_MEMO);function.setServiceMemoId(50L);
        function.setSummary("Заведование кабинетом");
        function.setConditionsJson("Изложить пункт 2.4 раздела «Оплата труда» в новой редакции.");
        when(agreements.findById(201L)).thenReturn(Optional.of(issued));
        when(agreements.findById(202L)).thenReturn(Optional.of(function));
        when(versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",201L)).thenReturn(List.of());
        when(versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",202L)).thenReturn(List.of());

        AdditionalAgreement merged=service.mergeAgreements(List.of(201L,202L),"hr");

        assertSame(issued,merged);
        assertEquals("1 / 2025-2026",merged.getInternalNumber(),"При перевыпуске сохраняется номер выпущенного документа");
        assertEquals(AdditionalAgreement.Status.WAITING_FOR_MEMO,merged.getStatus());
        assertEquals(50L,merged.getServiceMemoId());
        assertNull(merged.getIssuedAt());assertNull(merged.getIssuedBy());
        assertNull(merged.getCurrentDocument(),"После объединения нужно сформировать новую редакцию DOCX");
        assertTrue(merged.getConditionsJson().contains("2.1"));assertTrue(merged.getConditionsJson().contains("2.4"));
        assertTrue(merged.getSourceSnapshotJson().contains("\"reissue\":true"));
        assertTrue(merged.getSourceSnapshotJson().contains("\"previousRevision\":2"));
        verify(versions).save(argThat(version->version.getDocumentId().equals(201L)
                &&"ISSUED_BEFORE_REISSUE".equals(version.getSource())
                &&Arrays.equals(new byte[]{1,2,3},version.getContent())));
        verify(agreements).delete(function);
        verify(agreements).save(issued);
    }

    @Test void signedAgreementCannotBeMergedForReissue() {
        AdditionalAgreement signed=draftAgreement();signed.setId(211L);
        signed.setStatus(AdditionalAgreement.Status.SIGNED);
        AdditionalAgreement draft=draftAgreement();draft.setId(212L);
        draft.setConditionsJson("Изложить пункт 2.4 в новой редакции.");
        when(agreements.findById(211L)).thenReturn(Optional.of(signed));
        when(agreements.findById(212L)).thenReturn(Optional.of(draft));

        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.mergeAgreements(List.of(211L,212L),"hr"));

        assertTrue(error.getReason().contains("Подписанное"));
        verify(agreements,never()).delete(any());
    }

    @Test void contractlessAgreementCanBeEditedButCannotBePrepared() {
        AdditionalAgreement agreement=draftAgreement();agreement.setContractId(null);agreement.setInternalNumber("БД-1-1");
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));

        service.editAgreement(100L,new AgreementEditRequest(null,LocalDate.of(2025,9,1),
                LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),"Нагрузка",
                "Изложить пункт 2.1 в новой редакции.",new BigDecimal("50000"),false,null),"hr");

        assertEquals("Изложить пункт 2.1 в новой редакции.",agreement.getConditionsJson());
        assertNull(agreement.getCurrentDocument());
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.prepare(100L,"hr"));
        assertTrue(error.getReason().contains("трудовой договор"));
    }

    @Test void issuedUnsignedAgreementCanBeEditedAndIsMarkedForReissue() {
        AdditionalAgreement agreement=draftAgreement();agreement.setStatus(AdditionalAgreement.Status.ISSUED);
        agreement.setIssuedAt(java.time.LocalDateTime.of(2026,7,27,10,0));
        agreement.setCurrentDocument(new byte[]{4,5,6});agreement.setGeneratedDocument(new byte[]{4,5,6});
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));

        service.editAgreement(100L,new AgreementEditRequest(10L,LocalDate.of(2025,9,1),
                agreement.getValidFrom(),agreement.getValidTo(),"Нагрузка и стимул",
                "Изложить пункт 2.1 в новой редакции.\n\nВнести изменения в пункт 2.5.",
                new BigDecimal("85000"),false,null),"hr");

        assertEquals(AdditionalAgreement.Status.ISSUED,agreement.getStatus());
        assertTrue(agreement.isReissueRequired());
        assertArrayEquals(new byte[]{4,5,6},agreement.getCurrentDocument());
    }

    @Test void editedAgreementCanUpdateReusableCatalogTemplate() {
        AdditionalAgreement agreement=draftAgreement();agreement.setServiceMemoId(50L);
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.RECEIVED_BY_HR);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(catalog.findFirstBySchoolCodeAndNameIgnoreCase(anyString(),eq("Заведование кабинетом"))).thenReturn(Optional.empty());
        when(catalog.save(any())).thenAnswer(invocation->{HrCatalogItem item=invocation.getArgument(0);item.setId(77L);return item;});

        service.editAgreement(100L,new AgreementEditRequest(10L,LocalDate.of(2025,9,1),
                agreement.getValidFrom(),agreement.getValidTo(),"Заведование кабинетом",
                "Работнику поручается заведование кабинетом.",new BigDecimal("15000"),true,"Заведование кабинетом"),"hr");

        assertEquals(77L,memo.getCatalogItemId());
        verify(catalog).save(argThat(item->"Работнику поручается заведование кабинетом.".equals(item.getAgreementText())
                &&item.getCategory()==HrCatalogItem.Category.COMPENSATION));
    }

    @Test void preparationCreatesSchoolAgreementDocxAndMovesDraftToReady() throws Exception {
        AdditionalAgreement agreement=draftAgreement();agreement.setTotalAmount(new BigDecimal("50000"));
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(2);
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5-а",25));
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        assertEquals(AdditionalAgreement.Status.READY,prepared.getStatus());assertNotNull(prepared.getCurrentDocument());
        assertEquals(new BigDecimal("5241.67"),prepared.getTotalAmount(),
                "Для пункта 2.1 сохранённая сумма заменяется итогом всех строк приложения");
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(table->table.getText()).reduce("",String::concat);
            assertTrue(text.contains("ДОПОЛНИТЕЛЬНОЕ СОГЛАШЕНИЕ"));
            assertTrue(text.contains("Внести изменения в пункт 2.1. раздела 2 «Оплата труда»"));
            assertTrue(text.contains("За исполнение трудовых (должностных) обязанностей"));
            assertTrue(text.contains("должностного оклада в размере 5 241 рубль 67 коп. "
                    +"(пять тысяч двести сорок один рубль 67 коп.)"));
            assertTrue(text.contains("на основании «ученико-часа» в размере 37 рублей"));
            assertTrue(text.contains("педагогической нагрузки в размере 2 часа"));
            assertTrue(text.contains("2. Срок действия настоящего дополнительного соглашения: с «01» сентября 2025 года по «31» августа 2026 года."));
            assertTrue(text.contains("3. Настоящее дополнительное соглашение является неотъемлемой частью Трудового договора"));
            assertTrue(text.contains("4. Другие положения Трудового договора"));
            assertFalse(text.contains("Размер оплаты труда с учетом условий настоящего дополнительного соглашения"));
            assertFalse(text.contains("Адреса и реквизиты сторон"));
            assertTrue(text.contains("РАБОТОДАТЕЛЬ"));assertTrue(text.contains("РАБОТНИК"));
            assertTrue(text.contains("Приложение № 1"));assertTrue(text.contains("Математика"));assertTrue(text.contains("Численность"));
            assertTrue(text.contains("25"));
            XWPFTable annex=document.getTables().stream()
                    .filter(table->table.getText().contains("Предмет")
                            &&table.getText().contains("Класс/группа")).findFirst().orElseThrow();
            assertFalse(annex.getText().contains("В ставке"),
                    "Для обычной нагрузки лишняя колонка часов внутри ставки не нужна");
            assertFalse(annex.getText().contains("Пояснение"),
                    "Связанная пустая колонка пояснения также должна быть скрыта");
            assertEquals(8,annex.getRow(0).getTableCells().size());
            XWPFTableRow annexTotal=annex.getRow(annex.getNumberOfRows()-1);
            assertEquals("Итого",annexTotal.getCell(0).getText());
            assertEquals("2",annexTotal.getCell(2).getText(),
                    "В строке «Итого» приложения должна суммироваться учебная нагрузка");
            XWPFTable details=document.getTables().stream()
                    .filter(table->table.getText().contains("РАБОТОДАТЕЛЬ")).findFirst().orElseThrow();
            assertEquals(2,details.getCTTbl().getTblGrid().sizeOfGridColArray());
            assertEquals(details.getCTTbl().getTblGrid().getGridColArray(0).getW(),
                    details.getCTTbl().getTblGrid().getGridColArray(1).getW(),
                    "Колонки реквизитов работодателя и работника должны быть одинаковой ширины");
            assertTrue(details.getRows().stream().allMatch(row->
                            row.getCell(0).getCTTc().getTcPr().getTcW().getW()
                                    .equals(row.getCell(1).getCTTc().getTcPr().getTcW().getW())),
                    "Одинаковая ширина должна быть закреплена у ячеек каждой строки");
            XWPFParagraph receipt=document.getParagraphs().stream()
                    .filter(paragraph->paragraph.getText().startsWith("Экземпляр дополнительного соглашения получил(а)"))
                    .findFirst().orElseThrow();
            assertTrue(receipt.getIndentationLeft()>0);
            assertFalse(receipt.getText().contains("дата и подпись работника"),
                    "Подпись к строке получения должна быть отдельным выровненным абзацем");
            XWPFParagraph receiptCaption=document.getParagraphs().stream()
                    .filter(paragraph->paragraph.getText().equals("(дата и подпись работника)"))
                    .findFirst().orElseThrow();
            assertEquals(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT,receiptCaption.getAlignment());
        }
        String qaOutput=System.getProperty("hr.agreement.qa.output");
        if(qaOutput!=null&&!qaOutput.isBlank()){Path path=Path.of(qaOutput);Files.createDirectories(path.getParent());Files.write(path,prepared.getCurrentDocument());}
        verify(versions).save(argThat(version->"PREPARED".equals(version.getSource())));
    }

    @Test void agreementSeparatesHoursInsideRateFromPaidHours() throws Exception {
        contract.setLoadHoursMayBeIncludedInRate(true);
        AdditionalAgreement agreement=draftAgreement();
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("ОБЗР");load.setClassName("7-А");load.setLoad(10);
        load.setIncludedInRateHours(new BigDecimal("4"));load.setInRateAllocationConfirmed(true);
        load.setInRateReason("внутри ставки преподавателя ОБЗР");
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("7-а",20));
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        assertEquals(new BigDecimal("12580.00"),prepared.getTotalAmount());
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(XWPFParagraph::getText)
                    .reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(XWPFTable::getText).reduce("",String::concat);
            assertTrue(text.contains("педагогической нагрузки в размере 6 часов"));
            assertTrue(text.contains("В педагогическую нагрузку Работника также включено 4 часа"));
            assertTrue(text.contains("внутри ставки преподавателя ОБЗР"));
            XWPFTable annex=document.getTables().stream()
                    .filter(table->table.getText().contains("В ставке")&&table.getText().contains("К оплате"))
                    .findFirst().orElseThrow();
            assertEquals("10",annex.getRow(1).getCell(2).getText());
            assertEquals("4",annex.getRow(1).getCell(3).getText());
            assertEquals("6",annex.getRow(1).getCell(4).getText());
        }
    }

    @Test void agreementCannotBePreparedBeforeInRateAllocationIsConfirmed() {
        contract.setLoadHoursMayBeIncludedInRate(true);
        AdditionalAgreement agreement=draftAgreement();
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("ОБЗР");load.setClassName("7-А");load.setLoad(5);
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));

        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.prepare(100L,"hr"));

        assertTrue(error.getReason().contains("Сначала распределите часы внутри ставки"));
    }

    @Test void combinedPayAgreementUsesAllWordingFromProvidedSchoolSample() throws Exception {
        AdditionalAgreement agreement=draftAgreement();agreement.setSummary("Нагрузка, дополнительные функции и стимул");
        agreement.setTotalAmount(new BigDecimal("133348"));
        agreement.setConditionsJson("Изложить пункт 2.1 раздела «Оплата труда» в новой редакции.\n\n"
                +"Внести изменения в пункт 2.4. раздела 2 «Оплата труда», изложив его в следующей редакции:\n"
                +"«2.4. Работнику выплачиваются ежемесячные компенсационные выплаты при условии, если на Работника:\n"
                +"- возложена функция классного руководителя, в размере 5 000 рублей 00 коп. за 1 класс "
                +"(но не более чем за 2 класса) за счет федерального бюджета и 500 рублей за 1 обучающегося "
                +"в классе за счет бюджета города Москвы - 16 000 рублей 00 коп. "
                +"(шестнадцать тысяч рублей 00 коп.) в месяц;\n"
                +"- возложена работа с обучающимися с ограниченными возможностями здоровья, детьми-инвалидами "
                +"и инвалидами, в размере 6 134 рубля 01 коп. (шесть тысяч сто тридцать четыре рубля 01 коп.) в месяц».");
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Технология");load.setClassName("5А");load.setLoad(30);
        HrIncentive incentive=new HrIncentive();incentive.setAcademicYear("2025/2026");incentive.setTeacherId(1L);
        incentive.setAmount(new BigDecimal("21504.78"));
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5-а",25));
        when(incentives.findByAcademicYearAndTeacherId("2025/2026",1L)).thenReturn(Optional.of(incentive));
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        assertEquals(new BigDecimal("78625.00"),prepared.getTotalAmount());
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(table->table.getText()).reduce("",String::concat);
            assertTrue(text.contains("1. Внести изменения в пункт 2.1. раздела 2 «Оплата труда»"));
            assertTrue(text.contains("должностного оклада в размере 78 625 рублей 00 коп. "
                    +"(семьдесят восемь тысяч шестьсот двадцать пять рублей 00 коп.)"));
            assertTrue(text.contains("педагогической нагрузки в размере 30 часов"));
            assertTrue(text.contains("2. Внести изменения в пункт 2.4. раздела 2 «Оплата труда»"));
            assertTrue(text.contains("500 рублей за 1 обучающегося"));
            assertTrue(text.contains("3. Внести изменения в пункт 2.5. раздела 2 «Оплата труда»"));
            assertTrue(text.contains("Устанавливаются ежемесячные стимулирующие выплаты за результаты обучающихся "
                    +"по итогам учебного года - 21 504 рубля 78 коп."));
            assertTrue(text.contains("4. Срок действия настоящего дополнительного соглашения"));
            assertTrue(text.contains("5. Настоящее дополнительное соглашение является неотъемлемой частью"));
            assertTrue(text.contains("6. Другие положения Трудового договора"));
            assertTrue(text.contains("И.И. Иванов"));
        }
        String qaOutput=System.getProperty("hr.sample.agreement.qa.output");
        if(qaOutput!=null&&!qaOutput.isBlank()){Path path=Path.of(qaOutput);Files.createDirectories(path.getParent());Files.write(path,prepared.getCurrentDocument());}
    }

    @Test void existingExpandedLoadClauseAndAnnexUseSameRecalculatedTotal() throws Exception {
        AdditionalAgreement agreement=draftAgreement();
        agreement.setTotalAmount(new BigDecimal("4298.17"));
        agreement.setConditionsJson("Внести изменения в пункт 2.1. раздела 2 «Оплата труда», изложив его в следующей редакции:\n"
                +"«2.1. За исполнение трудовых (должностных) обязанностей, предусмотренных должностной инструкцией "
                +"и настоящим Трудовым договором, Работнику выплачивается заработная плата, которая состоит из:\n"
                +"- должностного оклада в размере 4 298 рублей 17 коп. "
                +"(четыре тысячи двести девяносто восемь рублей 17 коп.) в месяц, определяемого исходя из учебной "
                +"нагрузки по формуле, установленной в п. 2.3. настоящего договора, на основании «ученико-часа» "
                +"в размере 37 рублей и педагогической нагрузки в размере 32 часа».");
        ManualLoadEntry common=new ManualLoadEntry();common.setTeacherId(1L);common.setAcademicYear("2025/2026");
        common.setSubjectName("Россия – мои горизонты");common.setClassName("9-Е");common.setLoad(2);
        ManualLoadEntry biology=new ManualLoadEntry();biology.setTeacherId(1L);biology.setAcademicYear("2025/2026");
        biology.setSubjectName("Биология");biology.setClassName("9-Б");biology.setLoad(43);
        SubjectLevelCoefficientEntry biologyCoefficient=new SubjectLevelCoefficientEntry();
        biologyCoefficient.setSubjectName("Биология");biologyCoefficient.setEducationStage(EducationStage.OOO);
        biologyCoefficient.setCoefficient(new BigDecimal("1.3"));
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(common,biology));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("9-е",23,"9-б",17));
        when(coefficients.findBySubjectNameIgnoreCaseAndEducationStage("Биология",EducationStage.OOO))
                .thenReturn(Optional.of(biologyCoefficient));
        when(coefficients.findAll()).thenReturn(List.of(biologyCoefficient));
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        assertEquals(new BigDecimal("104445.45"),prepared.getTotalAmount());
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(table->table.getText()).reduce("",String::concat);
            assertTrue(text.contains("должностного оклада в размере 104 445 рублей 45 коп. "
                    +"(сто четыре тысячи четыреста сорок пять рублей 45 коп.)"));
            assertTrue(text.contains("Итоговая сумма: 104 445,45 руб. "
                    +"(сто четыре тысячи четыреста сорок пять рублей 45 копеек)"));
            assertEquals(2,text.split(java.util.regex.Pattern.quote("104 445,45"),-1).length-1,
                    "Одинаковый итог должен быть и в строке таблицы, и под приложением");
            assertFalse(text.contains("4 298 рублей 17 коп."));
        }
        String qaOutput=System.getProperty("hr.load-total.qa.output");
        if(qaOutput!=null&&!qaOutput.isBlank()){
            Path path=Path.of(qaOutput);Files.createDirectories(path.getParent());Files.write(path,prepared.getCurrentDocument());
        }
    }

    @Test void issuedUnsignedAgreementCanBeReopenedForCorrectionAndReissue() {
        AdditionalAgreement agreement=draftAgreement();agreement.setStatus(AdditionalAgreement.Status.ISSUED);
        agreement.setIssuedAt(java.time.LocalDateTime.of(2026,7,25,10,0));agreement.setIssuedBy("hr");
        agreement.setCurrentDocument(new byte[]{4,2,9,8});agreement.setGeneratedDocument(new byte[]{4,2,9,8});
        agreement.setCurrentFilename("issued.docx");agreement.setRevision(2);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",100L)).thenReturn(List.of());

        AdditionalAgreement reopened=service.reopenIssuedAgreement(100L,"hr");

        assertEquals(AdditionalAgreement.Status.DRAFT,reopened.getStatus());
        assertFalse(reopened.isReissueRequired());
        assertNull(reopened.getIssuedAt());assertNull(reopened.getIssuedBy());
        assertNull(reopened.getGeneratedDocument());assertNull(reopened.getCurrentDocument());
        verify(versions).save(argThat(version->"ISSUED_BEFORE_REISSUE".equals(version.getSource())
                &&Arrays.equals(new byte[]{4,2,9,8},version.getContent())));
        verify(agreements).save(agreement);
    }

    @Test void signedAgreementCannotBeReopenedForCorrection() {
        AdditionalAgreement agreement=draftAgreement();agreement.setStatus(AdditionalAgreement.Status.SIGNED);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));

        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.reopenIssuedAgreement(100L,"hr"));

        assertTrue(error.getReason().contains("Подписанное"));
        verify(agreements,never()).save(any());
    }

    @Test void staleIssuedAgreementCannotBeMarkedSignedBeforeReissue() {
        AdditionalAgreement agreement=draftAgreement();agreement.setStatus(AdditionalAgreement.Status.ISSUED);
        agreement.setReissueRequired(true);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));

        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.agreementStatus(100L,AdditionalAgreement.Status.SIGNED));

        assertTrue(error.getReason().contains("перевыпустите"));
        verify(agreements,never()).save(any());
    }

    @Test void additionalWorkAgreementFollowsSchoolSampleStructure() throws Exception {
        AdditionalAgreement agreement=draftAgreement();agreement.setKind(AdditionalAgreement.Kind.ADDITIONAL_WORK);
        agreement.setServiceMemoId(50L);agreement.setSummary("Заведование кабинетом");agreement.setTotalAmount(new BigDecimal("15000"));
        agreement.setConditionsJson("Работнику поручается выполнение дополнительной работы «Заведование кабинетом» без освобождения от основной работы.");
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.RECEIVED_BY_HR);
        memo.setAssignmentName("Заведование кабинетом");memo.setDutiesText("Обеспечивает сохранность имущества\nКонтролирует доступ в кабинет");
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(table->table.getText()).reduce("",String::concat);
            assertTrue(text.contains("об увеличении объема работ"));assertTrue(text.contains("Обеспечивает сохранность имущества"));
            assertTrue(text.contains("три рабочих дня"));assertTrue(text.contains("15 000,00 руб."));
        }
        String qaOutput=System.getProperty("hr.additional.agreement.qa.output");
        if(qaOutput!=null&&!qaOutput.isBlank()){Path path=Path.of(qaOutput);Files.createDirectories(path.getParent());Files.write(path,prepared.getCurrentDocument());}
    }

    @Test void clause24AgreementFollowsSampleAndDoesNotDuplicateCompensationAmount() throws Exception {
        AdditionalAgreement agreement=draftAgreement();agreement.setServiceMemoId(50L);
        agreement.setSummary("Заведование кабинетом технологии");agreement.setTotalAmount(new BigDecimal("15000"));
        agreement.setConditionsJson("Внести изменения в пункт 2.4 раздела 2 \"Оплата труда\", изложив его в следующей редакции:\n"
                +"\"2.4. Работнику выплачиваются ежемесячные компенсационные выплаты при условии, если на Работника:\n"
                +"- возложена функция \"заведование кабинетом технологии\", в размере 15 000 рублей 00 коп. "
                +"(пятнадцать тысяч рублей 00 коп.) в месяц\".");
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.RECEIVED_BY_HR);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(table->table.getText()).reduce("",String::concat);
            assertTrue(text.contains("к Трудовому договору от 01.01.2025 г. № 1-ТД"));
            assertTrue(text.contains("Внести изменения в пункт 2.4 раздела 2 «Оплата труда»"));
            assertTrue(text.contains("Работнику выплачиваются ежемесячные компенсационные выплаты"));
            assertEquals(1,text.split(java.util.regex.Pattern.quote("15 000 рублей 00 коп."),-1).length-1);
            assertTrue(text.contains("Другие положения Трудового договора"));
        }
        String qaOutput=System.getProperty("hr.compensation.agreement.qa.output");
        if(qaOutput!=null&&!qaOutput.isBlank()){Path path=Path.of(qaOutput);Files.createDirectories(path.getParent());Files.write(path,prepared.getCurrentDocument());}
    }

    @Test void dutyCompensationAgreementDoesNotReceiveUnrelatedLoadAnnex() throws Exception {
        AdditionalAgreement agreement=draftAgreement();agreement.setServiceMemoId(50L);
        agreement.setSummary("Заведование кабинетом");agreement.setTotalAmount(new BigDecimal("5000"));
        agreement.setConditionsJson("Изложить пункт 2.4 раздела «Оплата труда» в новой редакции.");
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.RECEIVED_BY_HR);
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(2);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5А",25));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(table->table.getText()).reduce("",String::concat);
            assertFalse(text.contains("Подробный расчет приведен в Приложении № 1"));
            assertFalse(text.contains("Расчёт должностного оклада по педагогической нагрузке"));
        }
    }

    @Test void incentiveAgreementDoesNotReceiveLoadAnnexEvenWhenTeacherHasLoad() throws Exception {
        AdditionalAgreement agreement=draftAgreement();
        agreement.setSummary("Стимулирующая выплата");
        agreement.setConditionsJson("Изложить пункт 2.5 раздела «Оплата труда» в новой редакции.");
        agreement.setTotalAmount(new BigDecimal("5000"));
        ManualLoadEntry load=new ManualLoadEntry();load.setTeacherId(1L);load.setAcademicYear("2025/2026");
        load.setSubjectName("Математика");load.setClassName("5А");load.setLoad(2);
        when(agreements.findById(100L)).thenReturn(Optional.of(agreement));
        when(personal.findByTeacherId(1L)).thenReturn(Optional.of(completePersonal()));
        when(loads.findAllByAcademicYear("2025/2026")).thenReturn(List.of(load));
        when(sizes.effectiveClassSizes("2025/2026")).thenReturn(Map.of("5А",25));

        AdditionalAgreement prepared=service.prepare(100L,"hr");

        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(prepared.getCurrentDocument()))){
            String text=document.getParagraphs().stream().map(p->p.getText()).reduce("",(left,right)->left+"\n"+right)
                    +document.getTables().stream().map(table->table.getText()).reduce("",String::concat);
            assertTrue(text.contains("пункт 2.5"));
            assertFalse(text.contains("Подробный расчет приведен в Приложении № 1"));
            assertFalse(text.contains("Расчёт должностного оклада по педагогической нагрузке"));
        }
    }

    private AdditionalAgreement draftAgreement(){
        AdditionalAgreement agreement=new AdditionalAgreement();agreement.setId(100L);agreement.setTeacherId(1L);agreement.setContractId(10L);
        agreement.setAcademicYear("2025/2026");agreement.setInternalNumber("1 / 2025-2026");agreement.setStatus(AdditionalAgreement.Status.DRAFT);
        agreement.setKind(AdditionalAgreement.Kind.PAY_TERMS);agreement.setSummary("Нагрузка");agreement.setConditionsJson("Изложить пункт 2.1 раздела «Оплата труда» в новой редакции.");
        agreement.setDocumentDate(LocalDate.of(2025,9,1));agreement.setValidFrom(LocalDate.of(2025,9,1));agreement.setValidTo(LocalDate.of(2026,8,31));agreement.setCreatedBy("hr");return agreement;
    }

    private HrPersonalData completePersonal(){
        HrPersonalData data=new HrPersonalData();data.setTeacherId(1L);data.setPassportSeries("4510");data.setPassportNumber("123456");
        data.setPassportIssuedBy("ОВД района");data.setPassportIssueDate(LocalDate.of(2015,1,1));data.setPassportDepartmentCode("770-001");
        data.setRegistrationAddress("г. Москва, ул. Примерная, д. 1");data.setActualAddress("г. Москва, ул. Примерная, д. 1");data.setPhone("+7 999 000-00-00");return data;
    }

    private AgreementRequest request(Long old){return new AgreementRequest(10L,null,"2025/2026",LocalDate.of(2025,9,1),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),AdditionalAgreement.Kind.PAY_TERMS,AdditionalAgreement.ChangeMode.AMEND,"Нагрузка","Пункт 2.1",null,old);}
}
