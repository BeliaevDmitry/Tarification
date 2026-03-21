package org.school.personalLoad.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.AuthExceptions.UnauthorizedException;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.auth.CreateUserRequest;
import org.school.personalLoad.dto.auth.UpdateUserRequest;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AppUserServiceImpl implements AppUserService {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final AppUserRepository appUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.default-admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${app.security.default-admin.password:admin12345}")
    private String defaultAdminPassword;

    @Value("${app.security.default-admin.full-name:Главный администратор}")
    private String defaultAdminFullName;

    @Override
    @Transactional(readOnly = true)
    public SessionUser authenticate(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        AppUser user = appUserRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new UnauthorizedException("Неверный логин или пароль"));

        if (!user.isActive() || !user.isCanView()) {
            throw new UnauthorizedException("Доступ пользователя отключён администратором");
        }
        if (!passwordEncoder.matches(String.valueOf(password), user.getPasswordHash())) {
            throw new UnauthorizedException("Неверный логин или пароль");
        }
        return toSessionUser(user);
    }


    @Override
    @Transactional(readOnly = true)
    public SessionUser findSessionUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("Пользователь не авторизован");
        }
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Пользователь не найден"));
        if (!user.isActive() || !user.isCanView()) {
            throw new UnauthorizedException("Доступ пользователя отключён администратором");
        }
        return toSessionUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return appUserRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getRole).thenComparing(AppUser::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public AppUser createUser(CreateUserRequest request) {
        validateCreateRequest(request);
        String username = normalizeUsername(request.getUsername());
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Пользователь с таким логином уже существует");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(normalizeText(request.getFullName(), "ФИО пользователя обязательно"));
        user.setEmail(normalizeOptional(request.getEmail()));
        user.setRole(Objects.requireNonNull(request.getRole(), "Роль обязательна"));
        user.setActive(true);
        user.setCanView(request.getCanView() == null || request.getCanView());
        user.setCanEdit(Boolean.TRUE.equals(request.getCanEdit()));
        if (user.getRole() == UserRole.ADMIN) {
            user.setCanView(true);
            user.setCanEdit(true);
        }
        user.setPasswordHash(passwordEncoder.encode(generateTemporaryPassword()));
        return appUserRepository.save(user);
    }

    @Override
    public AppUser updateUser(Long userId, UpdateUserRequest request) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (request.getFullName() != null) {
            user.setFullName(normalizeText(request.getFullName(), "ФИО пользователя обязательно"));
        }
        if (request.getEmail() != null) {
            user.setEmail(normalizeOptional(request.getEmail()));
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        if (request.getCanView() != null) {
            user.setCanView(request.getCanView());
        }
        if (request.getCanEdit() != null) {
            user.setCanEdit(request.getCanEdit());
        }
        if (user.getRole() == UserRole.ADMIN) {
            user.setCanView(true);
            user.setCanEdit(true);
            user.setActive(true);
        }
        return appUserRepository.save(user);
    }

    @Override
    public String resetPassword(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        String password = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        appUserRepository.save(user);
        return password;
    }

    @Override
    public void ensureDefaultAdmin() {
        if (appUserRepository.count() > 0) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(normalizeUsername(defaultAdminUsername));
        admin.setFullName(defaultAdminFullName);
        admin.setRole(UserRole.ADMIN);
        admin.setEmail(null);
        admin.setActive(true);
        admin.setCanView(true);
        admin.setCanEdit(true);
        admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
        appUserRepository.save(admin);
        log.warn("Создан пользователь-администратор по умолчанию: login='{}' password='{}'. Обязательно смените пароль после первого входа.", admin.getUsername(), defaultAdminPassword);
    }

    private void validateCreateRequest(CreateUserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Тело запроса не передано");
        }
        normalizeText(request.getUsername(), "Логин пользователя обязателен");
        normalizeText(request.getFullName(), "ФИО пользователя обязательно");
        if (request.getRole() == null) {
            throw new IllegalArgumentException("Роль обязательна");
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i += 1) {
            password.append(PASSWORD_ALPHABET.charAt(secureRandom.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private String normalizeUsername(String username) {
        String value = normalizeText(username, "Логин пользователя обязателен");
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value, String errorMessage) {
        String normalized = String.valueOf(value == null ? "" : value).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private SessionUser toSessionUser(AppUser user) {
        return new SessionUser(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.isCanView(),
                user.isCanEdit() || user.getRole() == UserRole.ADMIN
        );
    }
}
