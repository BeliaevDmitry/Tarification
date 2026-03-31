package org.school.personalLoad.service.auth.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.repository.auth.AppUserTabPermissionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private AppUserTabPermissionRepository tabPermissionRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void changeOwnPasswordUpdatesHashWhenCurrentPasswordIsValid() {
        AppUserServiceImpl service = new AppUserServiceImpl(
                appUserRepository,
                schoolBuildingRepository,
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
                schoolBuildingRepository,
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
                schoolBuildingRepository,
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
                schoolBuildingRepository,
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
}
