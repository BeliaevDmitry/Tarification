package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.SubjectLevelCoefficientRequest;
import org.school.personalLoad.model.EducationStage;
import org.school.personalLoad.model.SubjectLevelCoefficientEntry;
import org.school.personalLoad.repository.SubjectLevelCoefficientRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class SubjectLevelCoefficientServiceImplTest {

    @Mock
    private SubjectLevelCoefficientRepository repository;

    @Test
    void saveUpdatesExistingSubjectLevelCoefficient() {
        SubjectLevelCoefficientEntry existing = new SubjectLevelCoefficientEntry();
        existing.setId(7L);
        existing.setSubjectName("Алгебра");
        existing.setEducationStage(EducationStage.OOO);
        existing.setCoefficient(BigDecimal.ONE);
        SubjectLevelCoefficientRequest request = new SubjectLevelCoefficientRequest();
        request.setSubjectName(" Алгебра ");
        request.setEducationStage(EducationStage.OOO);
        request.setCoefficient(new BigDecimal("1.5"));
        when(repository.findBySubjectNameIgnoreCaseAndEducationStage("Алгебра", EducationStage.OOO)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubjectLevelCoefficientServiceImpl service = new SubjectLevelCoefficientServiceImpl(repository);

        SubjectLevelCoefficientEntry saved = service.save(request);

        assertEquals(7L, saved.getId());
        assertEquals("Алгебра", saved.getSubjectName());
        assertEquals(EducationStage.OOO, saved.getEducationStage());
        assertEquals(new BigDecimal("1.5"), saved.getCoefficient());
        ArgumentCaptor<SubjectLevelCoefficientEntry> captor = ArgumentCaptor.forClass(SubjectLevelCoefficientEntry.class);
        verify(repository).save(captor.capture());
        assertEquals(7L, captor.getValue().getId());
    }
}
