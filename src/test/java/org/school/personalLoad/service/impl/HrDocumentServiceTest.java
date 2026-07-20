package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.HrDocumentDtos.AgreementRequest;
import org.school.personalLoad.dto.HrDocumentDtos.MemoRequest;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    ClassSizeService sizes=mock(ClassSizeService.class);
    HrDocumentService service;
    EmploymentContract contract;
    TeacherDirectoryEntry teacher;

    @BeforeEach void setUp(){
        service=new HrDocumentService(contracts,personal,memos,loadMemos,agreements,versions,catalog,teachers,loads,salary,coefficients,groups,sizes,new ObjectMapper());
        contract=new EmploymentContract(); contract.setId(10L); contract.setTeacherId(1L); contract.setContractNumber("1-ТД");
        contract.setContractDate(LocalDate.of(2025,1,1)); contract.setPositionName("Учитель");
        teacher=new TeacherDirectoryEntry(); teacher.setId(1L); teacher.setFioTeacher("Иванов Иван Иванович");
        when(contracts.findById(10L)).thenReturn(Optional.of(contract)); when(teachers.findById(1L)).thenReturn(Optional.of(teacher));
        when(contracts.save(any())).thenAnswer(x->x.getArgument(0));
        when(loads.findAllByAcademicYear(anyString())).thenReturn(List.of()); when(sizes.effectiveClassSizes(anyString())).thenReturn(Map.of());
        when(agreements.save(any())).thenAnswer(x->{AdditionalAgreement a=x.getArgument(0);if(a.getId()==null)a.setId(100L);return a;});
        when(memos.save(any())).thenAnswer(x->{HrServiceMemo m=x.getArgument(0);if(m.getId()==null)m.setId(50L);return m;});
    }

    @Test void annulledNumberIsReusedWithNewTechnicalRevision(){
        AdditionalAgreement old=new AdditionalAgreement(); old.setId(9L); old.setStatus(AdditionalAgreement.Status.ANNULLED);
        old.setInternalNumber("3 / 2025-2026"); old.setRevision(2); when(agreements.findById(9L)).thenReturn(Optional.of(old));
        AdditionalAgreement a=service.createAgreement(request(9L),"hr");
        assertEquals("3 / 2025-2026",a.getInternalNumber()); assertEquals(3,a.getRevision()); assertEquals(9L,a.getReplacesAgreementId()); verify(versions).save(any());
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
        assertTrue(memo.getAssignmentText().contains("Прошу Вас согласовать поручение работнику Иванов Иван Иванович"));
        assertTrue(memo.getAssignmentText().contains("15000.00 руб."));
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

    @Test void receivingDutyMemoReleasesLinkedAgreementDraft(){
        HrServiceMemo memo=new HrServiceMemo();memo.setId(50L);memo.setStatus(HrServiceMemo.Status.ISSUED);
        AdditionalAgreement agreement=new AdditionalAgreement();agreement.setStatus(AdditionalAgreement.Status.WAITING_FOR_MEMO);
        when(memos.findById(50L)).thenReturn(Optional.of(memo));
        when(agreements.findAllByServiceMemoId(50L)).thenReturn(List.of(agreement));

        service.memoStatus(50L,HrServiceMemo.Status.RECEIVED_BY_HR,"hr");

        assertEquals(AdditionalAgreement.Status.DRAFT,agreement.getStatus());
        assertEquals(HrServiceMemo.Status.RECEIVED_BY_HR,memo.getStatus());
    }

    @Test void dutyMemoCanBeCreatedBeforeEmploymentContractIsFilled(){
        MemoRequest request=new MemoRequest("2025/2026",1L,null,null,null,LocalDate.of(2025,9,1),"Кабинет",
                "Назначить ответственным",null,"2.4",null,new BigDecimal("5000"),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),false,false,null);

        HrServiceMemo memo=service.createMemo(request,"deputy");

        assertNull(memo.getContractId());
        assertNotNull(memo.getDocumentContent());
        verify(agreements,never()).save(any());
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
                LocalDate.of(2025,1,1),"Учитель",LocalDate.of(2025,1,1),null,true,true));

        assertEquals(10L,duty.getContractId());
        assertEquals(10L,load.getContractId());
        verify(agreements,atLeastOnce()).save(argThat(a->Objects.equals(a.getServiceMemoId(),51L)));
        verify(agreements,atLeastOnce()).save(argThat(a->Objects.equals(a.getLoadServiceMemoId(),61L)));
    }

    @Test void loadMemoCreatesWaitingAgreementLinkedByMemoId(){
        ServiceMemo memo=new ServiceMemo();memo.setId(60L);memo.setAcademicYear("2025/2026");
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
        assertTrue(memoTransaction.readOnly());
    }

    private AgreementRequest request(Long old){return new AgreementRequest(10L,null,"2025/2026",LocalDate.of(2025,9,1),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),AdditionalAgreement.Kind.PAY_TERMS,AdditionalAgreement.ChangeMode.AMEND,"Нагрузка","Пункт 2.1",null,old);}
}
