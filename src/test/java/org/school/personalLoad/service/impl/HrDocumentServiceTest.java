package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.dto.HrDocumentDtos.AgreementRequest;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrDocumentServiceTest {
    EmploymentContractRepository contracts=mock(EmploymentContractRepository.class); HrPersonalDataRepository personal=mock(HrPersonalDataRepository.class);
    HrServiceMemoRepository memos=mock(HrServiceMemoRepository.class); AdditionalAgreementRepository agreements=mock(AdditionalAgreementRepository.class);
    HrDocumentVersionRepository versions=mock(HrDocumentVersionRepository.class); TeacherDirectoryRepository teachers=mock(TeacherDirectoryRepository.class);
    ManualLoadEntryRepository loads=mock(ManualLoadEntryRepository.class); SalarySettingsRepository salary=mock(SalarySettingsRepository.class);
    SubjectLevelCoefficientRepository coefficients=mock(SubjectLevelCoefficientRepository.class); SalaryGroupCoefficientSubjectRepository groups=mock(SalaryGroupCoefficientSubjectRepository.class);
    ClassSizeService sizes=mock(ClassSizeService.class); HrDocumentService service;
    EmploymentContract contract; TeacherDirectoryEntry teacher;
    @BeforeEach void setUp(){service=new HrDocumentService(contracts,personal,memos,agreements,versions,teachers,loads,salary,coefficients,groups,sizes,new ObjectMapper());contract=new EmploymentContract();contract.setId(10L);contract.setTeacherId(1L);contract.setContractNumber("1-ТД");contract.setContractDate(LocalDate.of(2025,1,1));contract.setPositionName("Учитель");teacher=new TeacherDirectoryEntry();teacher.setId(1L);teacher.setFioTeacher("Иванов Иван Иванович");when(contracts.findById(10L)).thenReturn(Optional.of(contract));when(teachers.findById(1L)).thenReturn(Optional.of(teacher));when(loads.findAllByAcademicYear(anyString())).thenReturn(List.of());when(sizes.effectiveClassSizes(anyString())).thenReturn(Map.of());when(agreements.save(any())).thenAnswer(x->{AdditionalAgreement a=x.getArgument(0);if(a.getId()==null)a.setId(100L);return a;});}
    @Test void annulledNumberIsReusedWithNewTechnicalRevision(){AdditionalAgreement old=new AdditionalAgreement();old.setId(9L);old.setStatus(AdditionalAgreement.Status.ANNULLED);old.setInternalNumber("3 / 2025-2026");old.setRevision(2);when(agreements.findById(9L)).thenReturn(Optional.of(old));AdditionalAgreement a=service.createAgreement(request(9L),"hr");assertEquals("3 / 2025-2026",a.getInternalNumber());assertEquals(3,a.getRevision());assertEquals(9L,a.getReplacesAgreementId());verify(versions).save(any());}
    @Test void issueIsBlockedUntilPersonalDataIsComplete(){AdditionalAgreement a=new AdditionalAgreement();a.setId(5L);a.setContractId(10L);a.setStatus(AdditionalAgreement.Status.DRAFT);when(agreements.findById(5L)).thenReturn(Optional.of(a));when(personal.findByTeacherId(1L)).thenReturn(Optional.empty());assertThrows(ResponseStatusException.class,()->service.issue(5L,"hr"));}
    private AgreementRequest request(Long old){return new AgreementRequest(10L,null,"2025/2026",LocalDate.of(2025,9,1),LocalDate.of(2025,9,1),LocalDate.of(2026,8,31),AdditionalAgreement.Kind.PAY_TERMS,AdditionalAgreement.ChangeMode.AMEND,"Нагрузка","Пункт 2.1",null,old);}
}
