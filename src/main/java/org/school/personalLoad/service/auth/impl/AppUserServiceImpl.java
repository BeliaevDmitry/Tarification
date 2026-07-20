package org.school.personalLoad.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.auth.*;
import org.school.personalLoad.auth.AuthExceptions.UnauthorizedException;
import org.school.personalLoad.dto.auth.CreateUserRequest;
import org.school.personalLoad.dto.auth.UpdateUserRequest;
import org.school.personalLoad.dto.auth.UserTabPermissionRequest;
import org.school.personalLoad.repository.BuildingGroupRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.repository.auth.AppUserTabPermissionRepository;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AppUserServiceImpl implements AppUserService {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final AppUserRepository appUserRepository;
    private final BuildingGroupRepository buildingGroupRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final AppUserTabPermissionRepository tabPermissionRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.default-admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${app.security.default-admin.password:admin}")
    private String defaultAdminPassword;

    @Value("${app.security.default-admin.full-name:Главный администратор}")
    private String defaultAdminFullName;

    @Override
    public SessionUser authenticate(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        AppUser user = appUserRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new UnauthorizedException("Неверный логин или пароль"));
        user = syncUserWithTeacherDirectory(user);

        if (!user.isActive() || !user.isCanView()) {
            throw new UnauthorizedException("Доступ пользователя отключён администратором");
        }
        if (!passwordEncoder.matches(String.valueOf(password), user.getPasswordHash())) {
            throw new UnauthorizedException("Неверный логин или пароль");
        }
        return toSessionUser(user);
    }

    @Override
    public SessionUser findSessionUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("Пользователь не авторизован");
        }
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Пользователь не найден"));
        user = syncUserWithTeacherDirectory(user);
        if (!user.isActive() || !user.isCanView()) {
            throw new UnauthorizedException("Доступ пользователя отключён администратором");
        }
        return toSessionUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return appUserRepository.findAll().stream()
                .map(this::syncUserWithTeacherDirectory)
                .sorted(Comparator.comparing(AppUser::getRole).thenComparing(AppUser::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public List<TabPermissionSnapshot> getTabPermissions(Long userId) {
        return loadPermissionSnapshots(appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден")));
    }

    @Override
    public AppUser createUser(CreateUserRequest request) {
        validateCreateRequest(request);
        String username = normalizeUsername(request.getUsername());
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Пользователь с таким логином уже существует");
        }

        Set<String> knownBuildingGroupCodes = loadKnownBuildingGroupCodes();
        Set<String> knownBuildingAccessCodes = loadKnownBuildingAccessCodes();

        String normalizedFio = normalizeTeacherFio(request.getFullName());
        ensureUniqueTeacherFioUser(normalizedFio, null);
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(normalizedFio);
        user.setDocumentPosition(normalizeOptional(request.getDocumentPosition()));
        user.setEmail(normalizeOptional(request.getEmail()));
        user.setPhone(normalizePhone(request.getPhone()));
        user.setManagedBuildingCode(normalizeKnownBuildingScopeCode(request.getManagedBuildingCode(), knownBuildingGroupCodes, knownBuildingAccessCodes));
        user.setLoadEditAllBuildings(Boolean.TRUE.equals(request.getLoadEditAllBuildings()));
        user.setLoadEditableBuildingCodes(normalizeBuildingCodes(request.getLoadEditableBuildingCodes(), knownBuildingGroupCodes, knownBuildingAccessCodes));
        user.setRole(Objects.requireNonNull(request.getRole(), "Роль обязательна"));
        user.setActive(true);
        user.setCanView(request.getCanView() == null || request.getCanView());
        user.setCanEdit(Boolean.TRUE.equals(request.getCanEdit()));
        enforceAdminFlags(user);
        validateBuildingHeadAssignment(user);
        user.setPasswordHash(passwordEncoder.encode(generateTemporaryPassword()));
        AppUser savedUser = appUserRepository.save(user);
        saveTabPermissions(savedUser, request.getTabPermissions());
        recalculateGlobalEditFlag(savedUser);
        return savedUser;
    }

    @Override
    public AppUser updateUser(Long userId, UpdateUserRequest request) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Set<String> knownBuildingGroupCodes = loadKnownBuildingGroupCodes();
        Set<String> knownBuildingAccessCodes = loadKnownBuildingAccessCodes();

        if (request.getFullName() != null) {
            String normalizedFio = normalizeTeacherFio(request.getFullName());
            ensureUniqueTeacherFioUser(normalizedFio, user.getId());
            user.setFullName(normalizedFio);
        }
        if (request.getDocumentPosition() != null) {
            user.setDocumentPosition(normalizeOptional(request.getDocumentPosition()));
        }
        if (request.getEmail() != null) {
            user.setEmail(normalizeOptional(request.getEmail()));
        }
        if (request.getPhone() != null) {
            user.setPhone(normalizePhone(request.getPhone()));
        }
        if (request.getManagedBuildingCode() != null) {
            user.setManagedBuildingCode(normalizeKnownBuildingScopeCode(request.getManagedBuildingCode(), knownBuildingGroupCodes, knownBuildingAccessCodes));
        }
        if (request.getLoadEditAllBuildings() != null) {
            user.setLoadEditAllBuildings(request.getLoadEditAllBuildings());
        }
        if (request.getLoadEditableBuildingCodes() != null) {
            user.setLoadEditableBuildingCodes(normalizeBuildingCodes(request.getLoadEditableBuildingCodes(), knownBuildingGroupCodes, knownBuildingAccessCodes));
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
        enforceAdminFlags(user);
        validateBuildingHeadAssignment(user);
        AppUser savedUser = appUserRepository.save(user);
        if (request.getTabPermissions() != null) {
            saveTabPermissions(savedUser, request.getTabPermissions());
        } else {
            ensureTabPermissions(savedUser);
        }
        recalculateGlobalEditFlag(savedUser);
        return savedUser;
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
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        String verifiedCurrentPassword = requirePassword(currentPassword, "Текущий пароль обязателен");
        String verifiedNewPassword = requirePassword(newPassword, "Новый пароль обязателен");

        if (!passwordEncoder.matches(verifiedCurrentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Текущий пароль введён неверно");
        }
        if (verifiedNewPassword.length() < 8) {
            throw new IllegalArgumentException("Новый пароль должен содержать минимум 8 символов");
        }
        if (verifiedCurrentPassword.equals(verifiedNewPassword)) {
            throw new IllegalArgumentException("Новый пароль должен отличаться от текущего");
        }

        user.setPasswordHash(passwordEncoder.encode(verifiedNewPassword));
        appUserRepository.save(user);
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
        admin.setPhone(null);
        admin.setManagedBuildingCode(null);
        admin.setActive(true);
        admin.setCanView(true);
        admin.setCanEdit(true);
        admin.setLoadEditAllBuildings(true);
        admin.setLoadEditableBuildingCodes(new LinkedHashSet<>());
        admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
        AppUser savedAdmin = appUserRepository.save(admin);
        saveDefaultPermissions(savedAdmin, true, true);
        log.warn("Создан пользователь-администратор по умолчанию: login='{}' password='{}'. Обязательно смените пароль после первого входа.", savedAdmin.getUsername(), defaultAdminPassword);
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

    private void saveTabPermissions(AppUser user, List<UserTabPermissionRequest> requestedPermissions) {
        tabPermissionRepository.deleteAllByUserId(user.getId());
        tabPermissionRepository.flush();
        if (user.getRole() == UserRole.ADMIN) {
            saveDefaultPermissions(user, true, true);
            return;
        }

        Map<AppTab, UserTabPermissionRequest> requestedByTab = Optional.ofNullable(requestedPermissions)
                .orElseGet(List::of)
                .stream()
                .filter(permission -> permission.getTab() != null)
                .collect(Collectors.toMap(UserTabPermissionRequest::getTab, Function.identity(), (a, b) -> b, () -> new EnumMap<>(AppTab.class)));

        List<AppUserTabPermission> permissions = new ArrayList<>();
        for (AppTab tab : AppTab.navigableTabs()) {
            UserTabPermissionRequest requested = requestedByTab.get(tab);
            boolean sensitive = isSensitivePermission(tab);
            boolean defaultCanView = sensitive ? false : user.isCanView();
            boolean defaultCanEdit = sensitive ? false : user.isCanView() && user.isCanEdit();
            boolean defaultCanExport = sensitive ? false : defaultCanView;
            boolean canView = requested != null ? Boolean.TRUE.equals(requested.getCanView()) : defaultCanView;
            boolean canEdit = requested != null ? Boolean.TRUE.equals(requested.getCanEdit()) : defaultCanEdit;
            boolean canImport = requested != null ? Boolean.TRUE.equals(requested.getCanImport()) : defaultCanEdit;
            boolean canExport = requested != null ? Boolean.TRUE.equals(requested.getCanExport()) : defaultCanExport;
            if (tab == AppTab.USERS) {
                canView = false;
                canEdit = false;
                canImport = false;
                canExport = false;
            }
            if (!canView) {
                canEdit = false;
                canImport = false;
                canExport = false;
            }
            permissions.add(buildPermission(user, tab, canView, canEdit, canImport, canExport));
        }
        tabPermissionRepository.saveAll(permissions);
    }

    private void saveDefaultPermissions(AppUser user, boolean canView, boolean canEdit) {
        List<AppUserTabPermission> permissions = AppTab.navigableTabs().stream()
                .map(tab -> {
                    if (isSensitivePermission(tab) && user.getRole() != UserRole.ADMIN) {
                        return buildPermission(user, tab, false, false, false, false);
                    }
                    return buildPermission(user, tab, canView, canEdit, canEdit, canView);
                })
                .toList();
        tabPermissionRepository.saveAll(permissions);
    }

    private boolean isSensitivePermission(AppTab tab) {
        return tab == AppTab.LOAD_SALARY || tab == AppTab.OGE_MISMATCH_VIEW;
    }

    private AppUserTabPermission buildPermission(AppUser user, AppTab tab, boolean canView, boolean canEdit, boolean canImport, boolean canExport) {
        if (user.getRole() != UserRole.ADMIN && tab == AppTab.USERS) {
            canView = false;
            canEdit = false;
            canImport = false;
            canExport = false;
        }
        AppUserTabPermission permission = new AppUserTabPermission();
        permission.setUser(user);
        permission.setTab(tab);
        permission.setCanView(canView);
        permission.setCanEdit(canView && canEdit);
        permission.setCanImport(canView && canImport);
        permission.setCanExport(canView && canExport);
        return permission;
    }

    private void ensureTabPermissions(AppUser user) {
        List<AppUserTabPermission> existing = tabPermissionRepository.findAllByUserIdOrderByTabAsc(user.getId());
        if (existing.isEmpty()) {
            saveDefaultPermissions(user, user.isCanView(), user.isCanView() && user.isCanEdit());
            return;
        }

        EnumSet<AppTab> existingTabs = existing.stream()
                .map(AppUserTabPermission::getTab)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(AppTab.class)));
        Map<AppTab, AppUserTabPermission> existingByTab = existing.stream()
                .collect(Collectors.toMap(AppUserTabPermission::getTab, Function.identity(), (left, right) -> left,
                        () -> new EnumMap<>(AppTab.class)));
        mergeLegacyNotificationPermission(existingByTab);

        List<AppUserTabPermission> missing = new ArrayList<>();
        for (AppTab tab : AppTab.navigableTabs()) {
            if (existingTabs.contains(tab)) {
                continue;
            }
            AppUserTabPermission legacy = existingByTab.get(legacySourceTab(tab));
            boolean canView = user.getRole() == UserRole.ADMIN || (legacy != null ? legacy.isCanView() : user.isCanView());
            boolean canEdit = user.getRole() == UserRole.ADMIN || (legacy != null
                    ? legacy.isCanEdit()
                    : user.isCanView() && user.isCanEdit());
            boolean canImport = user.getRole() == UserRole.ADMIN || (legacy != null ? legacy.isCanImport() : canEdit);
            boolean canExport = user.getRole() == UserRole.ADMIN || (legacy != null ? legacy.isCanExport() : canView);
            if (isSensitivePermission(tab) && user.getRole() != UserRole.ADMIN) {
                missing.add(buildPermission(user, tab, false, false, false, false));
            } else {
                missing.add(buildPermission(user, tab, canView, canEdit, canImport, canExport));
            }
        }
        if (!missing.isEmpty()) {
            tabPermissionRepository.saveAll(missing);
        }
    }

    private void mergeLegacyNotificationPermission(Map<AppTab, AppUserTabPermission> existingByTab) {
        AppUserTabPermission view = existingByTab.get(AppTab.HR_NOTIFICATIONS_VIEW);
        AppUserTabPermission legacyEdit = existingByTab.get(AppTab.HR_NOTIFICATIONS_EDIT);
        if (view == null || legacyEdit == null || !legacyEdit.isCanEdit() || view.isCanEdit()) {
            return;
        }
        view.setCanView(true);
        view.setCanEdit(true);
        view.setCanImport(view.isCanImport() || legacyEdit.isCanImport());
        view.setCanExport(view.isCanExport() || legacyEdit.isCanExport());
        tabPermissionRepository.save(view);
    }

    private AppTab legacySourceTab(AppTab tab) {
        if (tab == AppTab.PEOPLE_LOAD || tab == AppTab.LOAD_ISSUES || tab == AppTab.LOAD_STATS) {
            return AppTab.LOAD;
        }
        if (tab == AppTab.TEACHERS_ARCHIVE || tab == AppTab.TEACHERS_DISMISSALS || tab == AppTab.TEACHERS_SETTINGS || tab == AppTab.TEACHERS_MCKO) {
            return AppTab.TEACHERS;
        }
        return tab;
    }

    private List<TabPermissionSnapshot> loadPermissionSnapshots(AppUser user) {
        ensureTabPermissions(user);
        List<AppUserTabPermission> permissions = tabPermissionRepository.findAllByUserIdOrderByTabAsc(user.getId());
        return permissions.stream()
                .map(permission -> new TabPermissionSnapshot(
                        permission.getTab(),
                        permission.isCanView(),
                        permission.isCanEdit(),
                        permission.isCanImport(),
                        permission.isCanExport()
                ))
                .toList();
    }

    private void recalculateGlobalEditFlag(AppUser user) {
        if (user.getRole() == UserRole.ADMIN) {
            user.setCanView(true);
            user.setCanEdit(true);
            appUserRepository.save(user);
            return;
        }
        boolean hasVisibleTab = loadPermissionSnapshots(user).stream().anyMatch(TabPermissionSnapshot::isCanView);
        boolean hasEditableTab = loadPermissionSnapshots(user).stream().anyMatch(TabPermissionSnapshot::isCanEdit);
        user.setCanView(user.isCanView() && hasVisibleTab);
        user.setCanEdit(user.isCanView() && hasEditableTab);
        appUserRepository.save(user);
    }

    private void validateBuildingHeadAssignment(AppUser user) {
        if (user.getRole() != UserRole.BUILDING_HEAD || user.getManagedBuildingCode() == null) {
            return;
        }
        String normalizedManagedBuildingCode = normalizeBuildingGroupCode(user.getManagedBuildingCode());
        if (normalizedManagedBuildingCode == null) {
            return;
        }
        appUserRepository.findAll().stream()
                .filter(existing -> existing.getRole() == UserRole.BUILDING_HEAD)
                .filter(existing -> !Objects.equals(existing.getId(), user.getId()))
                .filter(existing -> Objects.equals(normalizeBuildingGroupCode(existing.getManagedBuildingCode()), normalizedManagedBuildingCode))
                .findFirst()
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Для корпуса " + user.getManagedBuildingCode() + " уже назначен руководитель: " + existing.getFullName()
                    );
                });
    }

    private void enforceAdminFlags(AppUser user) {
        if (user.getRole() == UserRole.ADMIN) {
            user.setCanView(true);
            user.setCanEdit(true);
            user.setActive(true);
            user.setManagedBuildingCode(null);
            user.setLoadEditAllBuildings(true);
            user.setLoadEditableBuildingCodes(new LinkedHashSet<>());
            return;
        }
        if (user.getRole() != UserRole.BUILDING_HEAD) {
            user.setManagedBuildingCode(null);
        }
        if (user.isLoadEditAllBuildings()) {
            user.setLoadEditableBuildingCodes(new LinkedHashSet<>());
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

    private String normalizePhone(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) return null;
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("8")) digits = "7" + digits.substring(1);
        if (digits.length() != 11 || !digits.startsWith("7")) throw new IllegalArgumentException("Телефон должен быть в формате +7...");
        return "+" + digits;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String requirePassword(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }


    private LinkedHashSet<String> normalizeBuildingCodes(Collection<String> values, Set<String> knownBuildingGroupCodes, Set<String> knownBuildingAccessCodes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            String normalizedCode = normalizeKnownBuildingScopeCode(value, knownBuildingGroupCodes, knownBuildingAccessCodes);
            if (normalizedCode != null) {
                normalized.add(normalizedCode);
            }
        }
        return normalized;
    }

    private Set<String> loadKnownBuildingGroupCodes() {
        return buildingGroupRepository.findAll().stream()
                .map(group -> normalizeOptionalBuildingCode(group.getCode()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> loadKnownBuildingAccessCodes() {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        schoolBuildingRepository.findAll().forEach(building -> {
            String rawCode = normalizeOptionalBuildingCode(building.getCode());
            if (rawCode == null) {
                return;
            }
            codes.add(rawCode);
            String groupCode = normalizeBuildingGroupCode(rawCode);
            if (groupCode != null) {
                codes.add(groupCode);
                if (building.getId() != null) {
                    codes.add(groupCode + "::" + building.getId());
                }
                String address = normalizeOptionalBuildingCode(building.getAddress());
                if (address != null) {
                    codes.add(groupCode + "|" + address);
                }
            }
        });
        return codes;
    }

    private String normalizeKnownBuildingScopeCode(String value, Set<String> knownBuildingGroupCodes, Set<String> knownBuildingAccessCodes) {
        String normalized = normalizeOptionalBuildingCode(value);
        if (normalized == null) {
            return null;
        }
        if (knownBuildingGroupCodes.contains(normalized)) {
            return normalized;
        }
        if (knownBuildingAccessCodes.contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Корпус для редактирования нагрузки не найден: " + normalized);
    }

    private String normalizeOptionalBuildingCode(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized
                .toUpperCase(Locale.ROOT)
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("[CС][ПPР]", "СП")
                .replaceAll("\\s*\\|\\s*", "|")
                .replaceAll("\\s+", "");
        int idx = normalized.indexOf("|");
        if (idx >= 0) {
            return normalizeBuildingGroupAlias(normalized.substring(0, idx)) + normalized.substring(idx);
        }
        return normalizeBuildingGroupAlias(normalized);
    }

    private String normalizeBuildingGroupAlias(String value) {
        return String.valueOf(value == null ? "" : value).replaceFirst("^СП-(\\d+)$", "СП$1");
    }

    private String normalizeBuildingGroupCode(String value) {
        String normalized = normalizeOptionalBuildingCode(value);
        if (normalized == null) return null;
        int siteIdx = normalized.indexOf("::");
        if (siteIdx >= 0) return normalized.substring(0, siteIdx);
        int idx = normalized.indexOf("|");
        return idx >= 0 ? normalized.substring(0, idx) : normalized;
    }

    private String normalizeTeacherFio(String fio) {
        String normalized = normalizeText(fio, "ФИО пользователя обязательно");
        return teacherDirectoryRepository.findByFioTeacherIgnoreCase(normalized)
                .map(entry -> entry.getFioTeacher().trim())
                .orElseThrow(() -> new IllegalArgumentException("ФИО должно быть выбрано из справочника «Кадры»"));
    }

    private void ensureUniqueTeacherFioUser(String fio, Long selfId) {
        appUserRepository.findAll().stream()
                .filter(user -> !Objects.equals(user.getId(), selfId))
                .filter(user -> normalizeOptional(user.getFullName()) != null)
                .filter(user -> normalizeOptional(user.getFullName()).equalsIgnoreCase(fio))
                .findFirst()
                .ifPresent(user -> {
                    throw new IllegalStateException("Пользователь с этим ФИО уже существует: " + user.getUsername());
                });
    }

    private AppUser syncUserWithTeacherDirectory(AppUser user) {
        if (user == null || user.getRole() == UserRole.ADMIN) return user;
        String fio = normalizeOptional(user.getFullName());
        if (fio == null) return user;
        org.school.personalLoad.model.TeacherDirectoryEntry teacher = teacherDirectoryRepository.findByFioTeacherIgnoreCase(fio).orElse(null);
        boolean shouldDisable = teacher == null || teacher.getDismissalDate() != null;
        if (shouldDisable && user.isActive()) {
            user.setActive(false);
            user.setCanView(false);
            user.setCanEdit(false);
            return appUserRepository.save(user);
        }
        return user;
    }

    private SessionUser toSessionUser(AppUser user) {
        return new SessionUser(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                user.isCanView(),
                user.isCanEdit() || user.getRole() == UserRole.ADMIN,
                user.getManagedBuildingCode(),
                user.isLoadEditAllBuildings() || user.getRole() == UserRole.ADMIN,
                new LinkedHashSet<>(user.getLoadEditableBuildingCodes()),
                loadPermissionSnapshots(user)
        );
    }
}
