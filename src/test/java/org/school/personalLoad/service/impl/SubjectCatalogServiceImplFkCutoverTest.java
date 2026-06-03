package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.SubjectCreateRequest;
import org.school.personalLoad.model.SubjectArea;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.SubjectType;
import org.school.personalLoad.repository.SubjectAreaRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class SubjectCatalogServiceImplFkCutoverTest {

    @Mock
    private SubjectCatalogRepository subjectCatalogRepository;
    @Mock
    private SubjectAreaRepository subjectAreaRepository;

    @Test
    void updateSubjectAreaUsesSubjectAreaIdAndKeepsRelationAfterAreaRename() {
        SubjectCatalogEntry existing = new SubjectCatalogEntry();
        existing.setId(5L);
        existing.setSubjectName("Математика");
        existing.setSubjectType(SubjectType.CORE);
        existing.setSubjectAreaRef(area(7L, "Старое название"));
        existing.setSubjectAreaName("Старое название");
        SubjectArea renamedArea = area(7L, "Математика и информатика");
        SubjectCreateRequest request = new SubjectCreateRequest();
        request.setSubjectName("Математика");
        request.setSubjectType(SubjectType.CORE);
        request.setSubjectAreaId(7L);
        request.setSubjectAreaName("Любой legacy текст");
        SubjectCatalogServiceImpl service = new SubjectCatalogServiceImpl(subjectCatalogRepository, subjectAreaRepository);
        when(subjectCatalogRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(subjectCatalogRepository.findBySubjectNameAndSubjectType("Математика", SubjectType.CORE)).thenReturn(Optional.of(existing));
        when(subjectAreaRepository.findById(7L)).thenReturn(Optional.of(renamedArea));
        when(subjectCatalogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectCatalogEntry saved = service.update(5L, request);

        assertEquals(7L, saved.getSubjectAreaId());
        assertEquals("Математика и информатика", saved.getSubjectAreaName());
        ArgumentCaptor<SubjectCatalogEntry> captor = ArgumentCaptor.forClass(SubjectCatalogEntry.class);
        verify(subjectCatalogRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getSubjectAreaId());
    }

    private SubjectArea area(Long id, String name) {
        SubjectArea area = new SubjectArea();
        area.setId(id);
        area.setName(name);
        return area;
    }
}
