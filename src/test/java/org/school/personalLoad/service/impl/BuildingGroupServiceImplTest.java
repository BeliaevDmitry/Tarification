package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.dto.BuildingGroupCreateRequest;
import org.school.personalLoad.dto.BuildingGroupCreateResponse;
import org.school.personalLoad.dto.BuildingGroupUpdateRequest;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
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
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private ManualLoadEntryRepository manualLoadEntryRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;

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
        service = new BuildingGroupServiceImpl(
                buildingGroupRepository,
                schoolBuildingService,
                schoolBuildingRepository,
                appUserRepository,
                classroomLeadershipRepository,
                metaGroupRepository,
                curriculumPlanEntryRepository,
                manualLoadEntryRepository,
                teacherDirectoryRepository
        );
    }


    @Test
    void findAllReturnsOrganizationalBuildingWithoutPhysicalSiteAndManagerFromAccessModel() {
        BuildingGroup group = new BuildingGroup();
        group.setId(19L);
        group.setCode("СП3 МЕХМАТ");
        group.setName("СП3 мехмат");
        AppUser manager = new AppUser();
        manager.setRole(UserRole.BUILDING_HEAD);
        manager.setManagedBuildingCode("сп3 мехмат");
        manager.setFullName("Иванов И.И.");

        when(buildingGroupRepository.findAll()).thenReturn(List.of(group));
        when(appUserRepository.findAll()).thenReturn(List.of(manager));

        List<BuildingGroup> result = service.findAll();

        assertEquals(1, result.size());
        assertEquals("СП3 МЕХМАТ", result.get(0).getCode());
        assertEquals("Иванов И.И.", result.get(0).getManagerFio());
    }


    @Test
    void findAllReturnsManagerFioForBuildingHeadAssignedByBuildingGroupCode() {
        BuildingGroup group = new BuildingGroup();
        group.setId(20L);
        group.setCode("МЕХМАТ");
        group.setName("МЕХМАТ");
        AppUser manager = new AppUser();
        manager.setRole(UserRole.BUILDING_HEAD);
        manager.setManagedBuildingCode("МЕХМАТ");
        manager.setFullName("Петров Пётр Петрович");

        when(buildingGroupRepository.findAll()).thenReturn(List.of(group));
        when(appUserRepository.findAll()).thenReturn(List.of(manager));

        List<BuildingGroup> result = service.findAll();

        assertEquals(1, result.size());
        assertEquals("МЕХМАТ", result.get(0).getCode());
        assertEquals("Петров Пётр Петрович", result.get(0).getManagerFio());
    }

    @Test
    void updateAllowsNameButRejectsCodeChange() {
        BuildingGroup group = new BuildingGroup();
        group.setId(19L);
        group.setCode("СП3 МЕХМАТ");
        group.setName("Старое");
        BuildingGroupUpdateRequest request = new BuildingGroupUpdateRequest();
        request.setCode("СП3 МЕХМАТ");
        request.setName("Новое название");

        when(buildingGroupRepository.findById(19L)).thenReturn(Optional.of(group));
        when(buildingGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findAll()).thenReturn(List.of());

        BuildingGroup result = service.update(19L, request);

        assertEquals("Новое название", result.getName());

        BuildingGroupUpdateRequest invalid = new BuildingGroupUpdateRequest();
        invalid.setCode("СП9");
        invalid.setName("Новое название");
        assertThrows(IllegalArgumentException.class, () -> service.update(19L, invalid));
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
    void createWithoutOwnPhysicalSiteCreatesOnlyOrganizationalBuildingAndReturnsBaseSite() {
        BuildingGroupCreateRequest request = request(" СП3 МЕХМАТ ", "СП3 мехмат", "", "");
        request.setCreateInitialSite(false);
        request.setBaseSchoolBuildingId(38L);
        SchoolBuilding baseSite = new SchoolBuilding();
        baseSite.setId(38L);
        baseSite.setName("СП3");
        baseSite.setAddress("Кравченко, д.14, корп.1");

        when(buildingGroupRepository.findByCodeIgnoreCase("СП3 МЕХМАТ")).thenReturn(Optional.empty());
        when(schoolBuildingRepository.findById(38L)).thenReturn(Optional.of(baseSite));
        when(buildingGroupRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            BuildingGroup group = invocation.getArgument(0);
            group.setId(19L);
            return group;
        });

        BuildingGroupCreateResponse response = service.createWithInitialSite(request);

        assertEquals("СП3 МЕХМАТ", response.getBuildingGroup().getCode());
        assertEquals("СП3 мехмат", response.getBuildingGroup().getName());
        assertEquals(38L, response.getBaseSchoolBuilding().getId());
        assertEquals(null, response.getSchoolBuilding());
        verify(schoolBuildingRepository, never()).save(any());
    }

    @Test
    void createWithoutOwnPhysicalSiteRejectsUnknownBaseSiteBeforeCreatingGroup() {
        BuildingGroupCreateRequest request = request("СП3 МЕХМАТ", "СП3 мехмат", "", "");
        request.setCreateInitialSite(false);
        request.setBaseSchoolBuildingId(404L);

        when(schoolBuildingRepository.findById(404L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createWithInitialSite(request));

        assertTrue(error.getMessage().contains("Базовая физическая площадка не найдена: 404"));
        verify(buildingGroupRepository, never()).saveAndFlush(any());
        verify(schoolBuildingRepository, never()).save(any());
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


    @Test
    void deleteUnusedOrganizationalBuildingIsAllowed() {
        BuildingGroup group = new BuildingGroup();
        group.setId(21L);
        group.setCode("МЕХМАТ");
        group.setName("МЕХМАТ");

        when(buildingGroupRepository.findById(21L)).thenReturn(Optional.of(group));
        when(appUserRepository.findAll()).thenReturn(List.of());

        service.deleteById(21L);

        verify(buildingGroupRepository).deleteById(21L);
    }

    @Test
    void deleteOrganizationalBuildingWithPhysicalSitesIsRejected() {
        BuildingGroup group = new BuildingGroup();
        group.setId(21L);
        group.setCode("МЕХМАТ");
        group.setName("МЕХМАТ");

        when(buildingGroupRepository.findById(21L)).thenReturn(Optional.of(group));
        when(schoolBuildingRepository.existsByBuildingGroup_Id(21L)).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.deleteById(21L));

        assertTrue(error.getMessage().contains("у него есть физические площадки"));
        verify(buildingGroupRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrganizationalBuildingWithCurriculumOrLoadIsRejected() {
        BuildingGroup group = new BuildingGroup();
        group.setId(21L);
        group.setCode("МЕХМАТ");
        group.setName("МЕХМАТ");

        when(buildingGroupRepository.findById(21L)).thenReturn(Optional.of(group));
        when(curriculumPlanEntryRepository.existsByBuildingGroup_Id(21L)).thenReturn(true);

        IllegalStateException curriculumError = assertThrows(IllegalStateException.class, () -> service.deleteById(21L));
        assertTrue(curriculumError.getMessage().contains("учебный план"));

        when(curriculumPlanEntryRepository.existsByBuildingGroup_Id(21L)).thenReturn(false);
        when(manualLoadEntryRepository.existsByBuildingGroup_Id(21L)).thenReturn(true);

        IllegalStateException loadError = assertThrows(IllegalStateException.class, () -> service.deleteById(21L));
        assertTrue(loadError.getMessage().contains("нагрузка"));
        verify(buildingGroupRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrganizationalBuildingWithAssignedHeadIsRejected() {
        BuildingGroup group = new BuildingGroup();
        group.setId(21L);
        group.setCode("МЕХМАТ");
        group.setName("МЕХМАТ");
        AppUser manager = new AppUser();
        manager.setRole(UserRole.BUILDING_HEAD);
        manager.setManagedBuildingCode("мехмат");

        when(buildingGroupRepository.findById(21L)).thenReturn(Optional.of(group));
        when(appUserRepository.findAll()).thenReturn(List.of(manager));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.deleteById(21L));

        assertTrue(error.getMessage().contains("назначен руководитель"));
        verify(buildingGroupRepository, never()).deleteById(any());
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
