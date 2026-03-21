package org.school.personalLoad.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private AppUserRepository userRepository;
    @Mock private SchoolBuildingRepository buildingRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CurrentUserService currentUserService;
    @Mock private AuditService auditService;

    @InjectMocks private UserServiceImpl userService;

    private AppUser admin;

    @BeforeEach
    void setUp() {
        admin = new AppUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole(RoleName.ADMIN);
    }

    @Test
    void createAssignsBuildingHeadToSelectedBuilding() {
        UserDtos.CreateUserRequest request = new UserDtos.CreateUserRequest();
        request.setUsername("head1");
        request.setPassword("secret");
        request.setRole(RoleName.BUILDING_HEAD);
        request.setBuildingId(10L);
        request.setEnabled(true);

        SchoolBuilding building = new SchoolBuilding();
        building.setId(10L);
        building.setCode("B1");
        when(passwordEncoder.encode("secret")).thenReturn("encoded");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(55L);
            return user;
        });
        when(buildingRepository.findById(10L)).thenReturn(Optional.of(building));
        when(buildingRepository.findByHeadUserId(55L)).thenReturn(Optional.empty());

        UserDtos.UserResponse response = userService.create(request);

        assertThat(response.getId()).isEqualTo(55L);
        assertThat(response.getRole()).isEqualTo(RoleName.BUILDING_HEAD);
        assertThat(building.getHeadUserId()).isEqualTo(55L);
        verify(buildingRepository).save(building);
    }

    @Test
    void changePasswordRequiresCorrectCurrentPassword() {
        AppUser current = new AppUser();
        current.setId(2L);
        current.setUsername("user");
        current.setRole(RoleName.OPERATOR);
        current.setPassword("encoded-old");
        when(currentUserService.requireCurrentUser()).thenReturn(current);
        when(passwordEncoder.matches("bad-old", "encoded-old")).thenReturn(false);

        UserDtos.ChangePasswordRequest request = new UserDtos.ChangePasswordRequest();
        request.setCurrentPassword("bad-old");
        request.setNewPassword("new-pass");
        request.setConfirmPassword("new-pass");

        assertThatThrownBy(() -> userService.changePassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is invalid");
    }
}
