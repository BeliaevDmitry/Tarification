package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.BuildingGroupCreateRequest;
import org.school.personalLoad.dto.BuildingGroupCreateResponse;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildingGroupServiceImplTest {

    @Mock
    private BuildingGroupRepository buildingGroupRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ClassroomLeadershipRepository classroomLeadershipRepository;
    @Mock
    private MetaGroupRepository metaGroupRepository;

    private BuildingGroupServiceImpl service;

    @BeforeEach
    void setUp() {
        SchoolBuildingServiceImpl schoolBuildingService = new SchoolBuildingServiceImpl(
                schoolBuildingRepository,
                buildingGroupRepository,
                appUserRepository,
                classroomLeadershipRepository,
                metaGroupRepository
        );
        service = new BuildingGroupServiceImpl(buildingGroupRepository, schoolBuildingService);
    }

    @Test
    void createWithInitialSiteCreatesOrganizationalBuildingAndCanonicalPhysicalSite() {
        BuildingGroupCreateRequest request = request(" мехмат ", "Мехмат", "Мехмат", " Адрес Мехмата ");
        when(buildingGroupRepository.findByCodeIgnoreCase("МЕХМАТ")).thenReturn(Optional.empty());
        when(buildingGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            BuildingGroup group = invocation.getArgument(0);
            group.setId(77L);
            return group;
        });
        when(buildingGroupRepository.findById(77L)).thenAnswer(invocation -> {
            BuildingGroup group = new BuildingGroup();
            group.setId(77L);
            group.setCode("МЕХМАТ");
            group.setName("Мехмат");
            return Optional.of(group);
        });
        when(schoolBuildingRepository.findAllByCodeIgnoreCase("мехмат|адрес мехмата")).thenReturn(List.of());
        when(schoolBuildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BuildingGroupCreateResponse response = service.createWithInitialSite(request);

        assertEquals("МЕХМАТ", response.getBuildingGroup().getCode());
        assertEquals("Мехмат", response.getBuildingGroup().getName());
        assertEquals(77L, response.getSchoolBuilding().getBuildingGroupId());
        assertEquals("Мехмат", response.getSchoolBuilding().getName());
        assertEquals("Адрес Мехмата", response.getSchoolBuilding().getAddress());
        assertEquals("мехмат|адрес мехмата", response.getSchoolBuilding().getCode());
    }

    @Test
    void createWithInitialSiteRejectsCaseInsensitiveDuplicateCode() {
        BuildingGroup existing = new BuildingGroup();
        existing.setId(1L);
        existing.setCode("МЕХМАТ");
        when(buildingGroupRepository.findByCodeIgnoreCase("МЕХМАТ")).thenReturn(Optional.of(existing));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createWithInitialSite(request("мехмат", "Мехмат", "Мехмат", "адрес")));

        assertTrue(error.getMessage().contains("Основной корпус с кодом «МЕХМАТ» уже существует"));
        verify(buildingGroupRepository, never()).saveAndFlush(any());
        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void createWithInitialSiteIsTransactionalSoInitialSiteFailureRollsBackGroup() throws Exception {
        Method method = BuildingGroupServiceImpl.class.getMethod("createWithInitialSite", BuildingGroupCreateRequest.class);
        assertNotNull(method.getAnnotation(Transactional.class));

        when(buildingGroupRepository.findByCodeIgnoreCase("МЕХМАТ")).thenReturn(Optional.empty());
        when(buildingGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            BuildingGroup group = invocation.getArgument(0);
            group.setId(77L);
            return group;
        });
        when(buildingGroupRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.createWithInitialSite(request("МЕХМАТ", "Мехмат", "Мехмат", "адрес")));

        ArgumentCaptor<BuildingGroup> groupCaptor = ArgumentCaptor.forClass(BuildingGroup.class);
        verify(buildingGroupRepository).saveAndFlush(groupCaptor.capture());
        assertEquals("МЕХМАТ", groupCaptor.getValue().getCode());
        verify(schoolBuildingRepository, never()).save(any());
    }

    private BuildingGroupCreateRequest request(String code, String name, String initialSiteName, String initialAddress) {
        BuildingGroupCreateRequest request = new BuildingGroupCreateRequest();
        request.setCode(code);
        request.setName(name);
        request.setInitialSiteName(initialSiteName);
        request.setInitialAddress(initialAddress);
        return request;
    }
}
