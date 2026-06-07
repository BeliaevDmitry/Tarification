package org.school.personalLoad.service.auth.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.TabPermissionSnapshot;
import org.school.personalLoad.dto.auth.CreateUserRequest;
import org.school.personalLoad.dto.auth.UpdateUserRequest;
import org.school.personalLoad.dto.auth.UserTabPermissionRequest;
import org.school.personalLoad.model.BuildingGroup;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.repository.auth.AppUserTabPermissionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private BuildingGroupRepository buildingGroupRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private TeacherDirectoryRepository teacherDirectoryRepository;
    @Mock
    private AppUserTabPermissionRepository tabPermissionRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void updateUserFlushesDeletedTabPermissionsBeforeInsert() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(2L);
        user.setRole(UserRole.BUILDING_HEAD);
        user.setCanView(true);
        user.setCanEdit(true);
        user.setActive(true);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(user));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        when(tabPermissionRepository.findAllByUserIdOrderByTabAsc(2L)).thenReturn(List.of());

        UserTabPermissionRequest permissionRequest = new UserTabPermissionRequest();
        permissionRequest.setTab(AppTab.BUILDINGS);
        permissionRequest.setCanView(true);
        permissionRequest.setCanEdit(true);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setTabPermissions(List.of(permissionRequest));

        service.updateUser(2L, request);

        verify(tabPermissionRepository).deleteAllByUserId(2L);
        verify(tabPermissionRepository).flush();
    }

    @Test
    void changeOwnPasswordUpdatesHashWhenCurrentPasswordIsValid() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(7L);
        user.setPasswordHash(passwordEncoder.encode("old-password"));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));

        service.changeOwnPassword(7L, "old-password", "new-password-123");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertTrue(passwordEncoder.matches("new-password-123", captor.getValue().getPasswordHash()));
    }

    @Test
    void changeOwnPasswordThrowsWhenCurrentPasswordIsWrong() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(8L);
        user.setPasswordHash(passwordEncoder.encode("old-password"));
        when(appUserRepository.findById(8L)).thenReturn(Optional.of(user));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.changeOwnPassword(8L, "wrong-password", "new-password-123"));

        assertEquals("Текущий пароль введён неверно", exception.getMessage());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void changeOwnPasswordThrowsWhenNewPasswordIsTooShort() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(9L);
        user.setPasswordHash(passwordEncoder.encode("old-password"));
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(user));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.changeOwnPassword(9L, "old-password", "short"));

        assertEquals("Новый пароль должен содержать минимум 8 символов", exception.getMessage());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void changeOwnPasswordThrowsWhenNewPasswordMatchesCurrent() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(10L);
        user.setPasswordHash(passwordEncoder.encode("same-password"));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(user));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.changeOwnPassword(10L, "same-password", "same-password"));

        assertEquals("Новый пароль должен отличаться от текущего", exception.getMessage());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void updateUserAcceptsBuildingCodeWithoutHyphenWhenCatalogCodeHasHyphen() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(11L);
        user.setRole(UserRole.BUILDING_HEAD);
        user.setCanView(true);
        user.setCanEdit(true);
        user.setActive(true);
        SchoolBuilding building = new SchoolBuilding();
        building.setCode("СП-3");

        when(appUserRepository.findById(11L)).thenReturn(Optional.of(user));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of(building));
        when(appUserRepository.findAll()).thenReturn(List.of(user));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tabPermissionRepository.findAllByUserIdOrderByTabAsc(11L)).thenReturn(List.of());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setManagedBuildingCode("СП3");

        AppUser updated = service.updateUser(11L, request);

        assertEquals("СП3", updated.getManagedBuildingCode());
    }


    @Test
    void updateUserAcceptsManagedBuildingGroupCode() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(13L);
        user.setRole(UserRole.BUILDING_HEAD);
        user.setCanView(true);
        user.setCanEdit(true);
        user.setActive(true);
        BuildingGroup buildingGroup = new BuildingGroup();
        buildingGroup.setCode("МЕХМАТ");

        when(appUserRepository.findById(13L)).thenReturn(Optional.of(user));
        when(buildingGroupRepository.findAll()).thenReturn(List.of(buildingGroup));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        when(appUserRepository.findAll()).thenReturn(List.of(user));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tabPermissionRepository.findAllByUserIdOrderByTabAsc(13L)).thenReturn(List.of());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setManagedBuildingCode("мехмат");

        AppUser updated = service.updateUser(13L, request);

        assertEquals("МЕХМАТ", updated.getManagedBuildingCode());
    }

    @Test
    void createUserPersistsManagedBuildingGroupCode() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        BuildingGroup buildingGroup = new BuildingGroup();
        buildingGroup.setCode("МЕХМАТ");
        org.school.personalLoad.model.TeacherDirectoryEntry teacher = new org.school.personalLoad.model.TeacherDirectoryEntry();
        teacher.setFioTeacher("Иванов Иван Иванович");

        when(appUserRepository.existsByUsernameIgnoreCase("ivanov")).thenReturn(false);
        when(buildingGroupRepository.findAll()).thenReturn(List.of(buildingGroup));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        when(teacherDirectoryRepository.findByFioTeacherIgnoreCase("Иванов Иван Иванович")).thenReturn(Optional.of(teacher));
        when(appUserRepository.findAll()).thenReturn(List.of());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            saved.setId(14L);
            return saved;
        });
        when(tabPermissionRepository.findAllByUserIdOrderByTabAsc(14L)).thenReturn(List.of());

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("ivanov");
        request.setFullName("Иванов Иван Иванович");
        request.setRole(UserRole.BUILDING_HEAD);
        request.setManagedBuildingCode("МЕХМАТ");
        request.setCanView(true);
        request.setCanEdit(true);

        AppUser created = service.createUser(request);

        assertEquals("МЕХМАТ", created.getManagedBuildingCode());
    }

    @Test
    void updateUserKeepsLegacyManagedBuildingAccessCodeSupported() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(15L);
        user.setRole(UserRole.BUILDING_HEAD);
        user.setCanView(true);
        user.setCanEdit(true);
        user.setActive(true);
        SchoolBuilding building = new SchoolBuilding();
        building.setCode("СП3");
        building.setAddress("Марии Ульяновой, д.17, корп.1");

        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));
        when(buildingGroupRepository.findAll()).thenReturn(List.of());
        when(schoolBuildingRepository.findAll()).thenReturn(List.of(building));
        when(appUserRepository.findAll()).thenReturn(List.of(user));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tabPermissionRepository.findAllByUserIdOrderByTabAsc(15L)).thenReturn(List.of());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setManagedBuildingCode("СП3|МАРИИУЛЬЯНОВОЙ,Д.17,КОРП.1");

        AppUser updated = service.updateUser(15L, request);

        assertEquals("СП3|МАРИИУЛЬЯНОВОЙ,Д.17,КОРП.1", updated.getManagedBuildingCode());
    }


    @Test
    void updateMethodistAcceptsBuildingGroupCodeAsLoadEditableScope() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(16L);
        user.setRole(UserRole.METHODIST);
        user.setCanView(true);
        user.setCanEdit(true);
        user.setActive(true);
        BuildingGroup buildingGroup = new BuildingGroup();
        buildingGroup.setCode("МЕХМАТ");

        when(appUserRepository.findById(16L)).thenReturn(Optional.of(user));
        when(buildingGroupRepository.findAll()).thenReturn(List.of(buildingGroup));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tabPermissionRepository.findAllByUserIdOrderByTabAsc(16L)).thenReturn(List.of());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setLoadEditableBuildingCodes(List.of("мехмат"));

        AppUser updated = service.updateUser(16L, request);

        assertEquals(new java.util.LinkedHashSet<>(List.of("МЕХМАТ")), updated.getLoadEditableBuildingCodes());
    }

    @Test
    void updateUserAcceptsSiteScopedLoadEditableScopeWhenPhysicalSiteExists() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(17L);
        user.setRole(UserRole.METHODIST);
        user.setCanView(true);
        user.setCanEdit(true);
        user.setActive(true);
        BuildingGroup buildingGroup = new BuildingGroup();
        buildingGroup.setCode("МЕХМАТ");
        SchoolBuilding building = new SchoolBuilding();
        building.setId(38L);
        building.setCode("МЕХМАТ");
        building.setAddress("Кравченко, д.14, корп.1");

        when(appUserRepository.findById(17L)).thenReturn(Optional.of(user));
        when(buildingGroupRepository.findAll()).thenReturn(List.of(buildingGroup));
        when(schoolBuildingRepository.findAll()).thenReturn(List.of(building));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tabPermissionRepository.findAllByUserIdOrderByTabAsc(17L)).thenReturn(List.of());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setLoadEditableBuildingCodes(List.of("мехмат::38"));

        AppUser updated = service.updateUser(17L, request);

        assertEquals(new java.util.LinkedHashSet<>(List.of("МЕХМАТ::38")), updated.getLoadEditableBuildingCodes());
    }

    @Test
    void updateUserRejectsUnknownLoadEditableScopeWithExistingError() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                buildingGroupRepository,
                schoolBuildingRepository,
                teacherDirectoryRepository,
                tabPermissionRepository,
                passwordEncoder
        );
        AppUser user = new AppUser();
        user.setId(18L);
        user.setRole(UserRole.METHODIST);
        user.setCanView(true);
        user.setCanEdit(true);
        user.setActive(true);

        when(appUserRepository.findById(18L)).thenReturn(Optional.of(user));
        when(buildingGroupRepository.findAll()).thenReturn(List.of());
        when(schoolBuildingRepository.findAll()).thenReturn(List.of());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setLoadEditableBuildingCodes(List.of("НЕСУЩЕСТВУЕТ"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateUser(18L, request));

        assertEquals("Корпус для редактирования нагрузки не найден: НЕСУЩЕСТВУЕТ", error.getMessage());
        verify(appUserRepository, never()).save(any());
    }


    @Test
    void sessionLoadPermissionTreatsSiteScopedBuildingCodesAsExactScope() {
        SessionUser user = new SessionUser(
                19L,
                "methodist",
                "Методист",
                null,
                null,
                UserRole.METHODIST,
                true,
                true,
                true,
                null,
                false,
                new java.util.LinkedHashSet<>(List.of("МЕХМАТ::38")),
                List.of(new TabPermissionSnapshot(AppTab.LOAD, true, true, true, true))
        );

        assertTrue(user.canEditLoadBuilding("МЕХМАТ::38"));
        assertFalse(user.canEditLoadBuilding("МЕХМАТ::39"));
        assertFalse(user.canEditLoadBuilding("МЕХМАТ"));
    }

    @Test
    void sessionLoadPermissionTreatsHyphenatedAndCompactBuildingCodesAsSameGroup() {
        SessionUser user = new SessionUser(
                12L,
                "teacher",
                "Педагог",
                null,
                null,
                UserRole.METHODIST,
                true,
                true,
                true,
                null,
                false,
                new java.util.LinkedHashSet<>(List.of("СП3")),
                List.of(new TabPermissionSnapshot(AppTab.LOAD, true, true, true, true))
        );

        assertTrue(user.canEditLoadBuilding("СП-3|УЛ.ЕЛАГИНА,Д.1"));
    }

}
