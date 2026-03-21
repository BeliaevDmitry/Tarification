package org.school.personalLoad.user;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final AppUserRepository userRepository;
    private final SchoolBuildingRepository buildingRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public Page<UserDtos.UserResponse> findAll(RoleName role, Pageable pageable) {
        Page<AppUser> page = role == null ? userRepository.findAll(pageable) : userRepository.findAllByRole(role, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDtos.UserResponse findById(Long id) {
        return toResponse(getUser(id));
    }

    @Override
    public UserDtos.UserResponse create(UserDtos.CreateUserRequest request) {
        validateCreate(request);
        AppUser user = new AppUser();
        user.setUsername(normalize(request.getUsername()));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setFullName(normalizeNullable(request.getFullName()));
        user.setRole(request.getRole());
        user.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        AppUser saved = userRepository.save(user);
        assignBuildingHead(saved, request.getBuildingId());
        auditService.log(ActionType.CREATE, "User", saved.getId(), null, Map.of("username", saved.getUsername(), "role", saved.getRole()), "User created");
        return toResponse(saved);
    }

    @Override
    public UserDtos.UserResponse update(Long id, UserDtos.UpdateUserRequest request) {
        AppUser user = getUser(id);
        RoleName oldRole = user.getRole();
        UserDtos.UserResponse oldValue = toResponse(user);
        user.setUsername(normalize(request.getUsername()));
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setFullName(normalizeNullable(request.getFullName()));
        user.setRole(request.getRole());
        user.setEnabled(request.getEnabled() == null || request.getEnabled());
        AppUser saved = userRepository.save(user);
        assignBuildingHead(saved, request.getBuildingId());
        auditService.log(ActionType.UPDATE, "User", saved.getId(), oldValue, toResponse(saved), "User updated");
        if (oldRole != saved.getRole()) {
            auditService.log(ActionType.ROLE_CHANGE, "User", saved.getId(), Map.of("role", oldRole), Map.of("role", saved.getRole()), "Role changed");
        }
        return toResponse(saved);
    }

    @Override
    public void delete(Long id) {
        AppUser current = currentUserService.requireCurrentUser();
        if (current.getId().equals(id)) {
            throw new IllegalArgumentException("You cannot delete yourself");
        }
        AppUser user = getUser(id);
        clearHeadAssignment(user.getId());
        userRepository.delete(user);
        auditService.log(ActionType.DELETE, "User", id, Map.of("username", user.getUsername()), null, "User deleted");
    }

    @Override
    public UserDtos.PasswordResetResponse resetPassword(Long id, String newPassword) {
        AppUser user = getUser(id);
        String password = (newPassword == null || newPassword.isBlank()) ? generatePassword(8) : newPassword;
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        auditService.log(ActionType.PASSWORD_RESET, "User", id, null, Map.of("username", user.getUsername()), "Password reset by admin");
        UserDtos.PasswordResetResponse response = new UserDtos.PasswordResetResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setTemporaryPassword(password);
        return response;
    }

    @Override
    public UserDtos.UserResponse updateProfile(UserDtos.ProfileUpdateRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        UserDtos.UserResponse oldValue = toResponse(user);
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setFullName(normalizeNullable(request.getFullName()));
        userRepository.save(user);
        auditService.log(ActionType.UPDATE, "Profile", user.getId(), oldValue, toResponse(user), "Profile updated");
        return toResponse(user);
    }

    @Override
    public void changePassword(UserDtos.ChangePasswordRequest request) {
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new IllegalArgumentException("newPassword is required");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }
        AppUser user = currentUserService.requireCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is invalid");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        auditService.log(ActionType.PASSWORD_CHANGE, "User", user.getId(), null, Map.of("username", user.getUsername()), "Password changed by user");
    }

    private void validateCreate(UserDtos.CreateUserRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (userRepository.existsByUsernameIgnoreCase(normalize(request.getUsername()))) {
            throw new IllegalArgumentException("username already exists");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (request.getRole() == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (request.getRole() == RoleName.BUILDING_HEAD && request.getBuildingId() == null) {
            throw new IllegalArgumentException("buildingId is required for BUILDING_HEAD");
        }
    }

    private void assignBuildingHead(AppUser user, Long buildingId) {
        clearHeadAssignment(user.getId());
        if (user.getRole() != RoleName.BUILDING_HEAD) {
            return;
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("buildingId is required for BUILDING_HEAD");
        }
        SchoolBuilding building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));
        if (building.getHeadUserId() != null && !building.getHeadUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Building already has a head assigned");
        }
        building.setHeadUserId(user.getId());
        buildingRepository.save(building);
    }

    private void clearHeadAssignment(Long userId) {
        buildingRepository.findByHeadUserId(userId).ifPresent(building -> {
            building.setHeadUserId(null);
            buildingRepository.save(building);
        });
    }

    private AppUser getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    private UserDtos.UserResponse toResponse(AppUser user) {
        UserDtos.UserResponse response = new UserDtos.UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setRole(user.getRole());
        response.setEnabled(user.isEnabled());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        buildingRepository.findByHeadUserId(user.getId()).ifPresent(building -> {
            response.setBuildingId(building.getId());
            response.setBuildingCode(building.getCode());
            response.setBuildingName(building.getName());
        });
        return response;
    }

    private String generatePassword(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
