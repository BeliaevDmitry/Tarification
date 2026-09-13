package org.school.personalLoad.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.auth.*;
import org.school.personalLoad.auth.AuthExceptions.UnauthorizedException;
import org.school.personalLoad.dto.auth.CreateUserRequest;
import org.school.personalLoad.dto.auth.UpdateUserRequest;
import org.school.personalLoad.dto.auth.UserTabPermissionRequest;
import org.school.personalLoad.model.TeacherDirectoryEntry;
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
        TeacherDirectoryEntry teacher = requestedTeacher(request);
        String usernameSource = normalizeOptional(request.getUsername()) == null
                ? teacher.getEmail() : request.getUsername();
        String username = normalizeUsername(usernameSource);
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Пользователь с таким логином уже существует");
        }

        Set<String> knownBuildingGroupCodes = loadKnownBuildingGroupCodes();
        Set<String> knownBuildingAccessCodes = loadKnownBuildingAccessCodes();

        String normalizedFio = teacher.getFioTeacher().trim();
        ensureUniqueTeacherFioUser(normalizedFio, null);
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(normalizedFio);
        user.setTeacherId(teacher.getId());
        user.setDocumentPosition(request.getDocumentPosition() == null
                ? defaultDocumentPosition(teacher.getPrimaryPosition())
                : normalizeOptional(request.getDocumentPosition()));
        user.setEmail(normalizeOptional(request.getEmail() == null ? teacher.getEmail() : request.getEmail()));
        user.setPhone(normalizePhone(request.getPhone() == null ? teacher.getPhone() : request.getPhone()));
        String managedBuildingCode = request.getManagedBuildingCode();
        LinkedHashSet<UserRole> roles = normalizeRoles(request.getRoles(), request.getRole(), null);
        UserRole primaryRole = primaryRole(roles, request.getRole());
        if (managedBuildingCode == null && !roles.contains(UserRole.ADMIN)) {
            managedBuildingCode = teacher.getNumberSchoolBuilding();
        }
        user.setManagedBuildingCode(normalizeKnownBuildingScopeCode(managedBuildingCode,
                knownBuildingGroupCodes, knownBuildingAccessCodes));
        user.setLoadEditAllBuildings(Boolean.TRUE.equals(request.getLoadEditAllBuildings()));
        user.setLoadEditableBuildingCodes(normalizeBuildingCodes(request.getLoadEditableBuildingCodes(), knownBuildingGroupCodes, knownBuildingAccessCodes));
        user.setRole(primaryRole);
        user.setRoles(roles);
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
            user.setTeacherId(teacherDirectoryRepository.findByFioTeacherIgnoreCase(normalizedFio)
                    .map(org.school.personalLoad.model.TeacherDirectoryEntry::getId)
                    .orElseThrow(() -> new IllegalArgumentException("ФИО должно быть выбрано из справочника «Кадры»")));
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
        if (request.getRoles() != null || request.getRole() != null) {
            LinkedHashSet<UserRole> roles = normalizeRoles(request.getRoles(), request.getRole(), user.getRole());
            user.setRole(primaryRole(roles, request.getRole() == null ? user.getRole() : request.getRole()));
            user.setRoles(roles);
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
        admin.setRoles(new LinkedHashSet<>(Set.of(UserRole.ADMIN)));
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
        if (request.getTeacherId() == null) {
            normalizeText(request.getUsername(), "Логин пользователя обязателен");
            normalizeText(request.getFullName(), "ФИО пользователя обязательно");
        }
        if ((request.getRoles() == null || request.getRoles().isEmpty()) && request.getRole() == null) {
            throw new IllegalArgumentException("Роль обязательна");
        }
    }

    private TeacherDirectoryEntry requestedTeacher(CreateUserRequest request) {
        TeacherDirectoryEntry teacher = request.getTeacherId() == null
                ? teacherDirectoryRepository.findByFioTeacherIgnoreCase(
                        normalizeText(request.getFullName(), "ФИО пользователя обязательно"))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ФИО должно быть выбрано из справочника «Кадры»"))
                : teacherDirectoryRepository.findById(request.getTeacherId())
                    .orElseThrow(() -> new IllegalArgumentException("Сотрудник из раздела «Кадры» не найден"));
        String fio = normalizeOptional(teacher.getFioTeacher());
        if (fio == null || teacher.isArchived() || teacher.getDismissalDate() != null
                || fio.toLowerCase(Locale.ROOT).startsWith("вакансия")) {
            throw new IllegalArgumentException("Выбранный сотрудник уволен, находится в архиве или является вакансией");
        }
        return teacher;
    }

    private String defaultDocumentPosition(String primaryPosition) {
        String value = normalizeOptional(primaryPosition);
        if (value == null) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("заместитель директора")) return lower.replaceFirst("заместитель", "заместителя");
        if (lower.startsWith("директор")) return lower.replaceFirst("директор", "директора");
        if (lower.startsWith("руководитель")) return lower.replaceFirst("руководитель", "руководителя");
        if (lower.startsWith("учитель")) return lower.replaceFirst("учитель", "учителя");
        if (lower.startsWith("преподаватель")) return lower.replaceFirst("преподаватель", "преподавателя");
        if (lower.startsWith("педагог-психолог")) return lower.replaceFirst("педагог-психолог", "педагога-психолога");
        if (lower.startsWith("социальный педагог")) return lower.replaceFirst("социальный педагог", "социального педагога");
        if (lower.startsWith("педагог")) return lower.replaceFirst("педагог", "педагога");
        if (lower.startsWith("методист")) return lower.replaceFirst("методист", "методиста");
        if (lower.startsWith("специалист")) return lower.replaceFirst("специалист", "специалиста");
        if (lower.startsWith("воспитатель")) return lower.replaceFirst("воспитатель", "воспитателя");
        if (lower.startsWith("тьютор")) return lower.replaceFirst("тьютор", "тьютора");
        if (lower.startsWith("советник")) return lower.replaceFirst("советник", "советника");
        if (lower.startsWith("секретарь")) return lower.replaceFirst("секретарь", "секретаря");
        if (lower.startsWith("инженер")) return lower.replaceFirst("инженер", "инженера");
        return value;
    }

    private void saveTabPermissions(AppUser user, List<UserTabPermissionRequest> requestedPermissions) {
        tabPermissionRepository.deleteAllByUserId(user.getId());
        tabPermissionRepository.flush();
        if (user.hasRole(UserRole.ADMIN)) {
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
            boolean defaultCanView = defaultCanView(user, tab, sensitive);
            boolean defaultCanEdit = defaultCanEdit(user, tab, sensitive);
            boolean defaultCanExport = defaultCanExport(user, tab, sensitive);
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
                    if (isSensitivePermission(tab) && !user.hasRole(UserRole.ADMIN)) {
                        return buildPermission(user, tab, false, false, false, false);
                    }
                    if (usesRestrictedRoleProfile(user)) {
                        boolean roleView = defaultCanView(user, tab, false);
                        boolean roleEdit = defaultCanEdit(user, tab, false);
                        return buildPermission(user, tab, roleView, roleEdit, roleEdit,
                                defaultCanExport(user, tab, false));
                    }
                    return buildPermission(user, tab, canView, canEdit, canEdit, canView);
                })
                .toList();
        tabPermissionRepository.saveAll(permissions);
    }

    private boolean isSensitivePermission(AppTab tab) {
        return tab == AppTab.LOAD_SALARY
                || tab == AppTab.LOAD_MASTER_FOT
                || tab == AppTab.OGE_MISMATCH_VIEW
                || tab == AppTab.CONTINGENT_ADMISSION_ROLES;
    }

    private AppUserTabPermission buildPermission(AppUser user, AppTab tab, boolean canView, boolean canEdit, boolean canImport, boolean canExport) {
        if (!user.hasRole(UserRole.ADMIN) && tab == AppTab.USERS) {
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
        ensureClassTeacherRolePermissions(user, existingByTab);

        List<AppUserTabPermission> missing = new ArrayList<>();
        for (AppTab tab : AppTab.navigableTabs()) {
            if (existingTabs.contains(tab)) {
                continue;
            }
            AppUserTabPermission legacy = existingByTab.get(legacySourceTab(tab));
            boolean canView = user.hasRole(UserRole.ADMIN) || (legacy != null ? legacy.isCanView() : defaultCanView(user, tab, isSensitivePermission(tab)));
            boolean canEdit = user.hasRole(UserRole.ADMIN) || (legacy != null
                    ? legacy.isCanEdit()
                    : defaultCanEdit(user, tab, isSensitivePermission(tab)));
            boolean canImport = user.hasRole(UserRole.ADMIN) || (legacy != null ? legacy.isCanImport() : canEdit);
            boolean canExport = user.hasRole(UserRole.ADMIN) || (legacy != null ? legacy.isCanExport() : defaultCanExport(user, tab, isSensitivePermission(tab)));
            if (user.hasRole(UserRole.CLASS_TEACHER) && user.isCanView()
                    && (tab == AppTab.CLASS_TEACHER_EXIT_ORDER_CREATE
                    || tab == AppTab.CLASS_TEACHER_EXIT_ORDER_SUMMARY
                    || tab == AppTab.EDUCATIONAL_WORK)) {
                canView = true;
                canEdit = user.isCanEdit() && tab != AppTab.CLASS_TEACHER_EXIT_ORDER_SUMMARY;
                canImport = canEdit && tab == AppTab.EDUCATIONAL_WORK;
                canExport = false;
            }
            if (isSensitivePermission(tab) && !user.hasRole(UserRole.ADMIN)) {
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

    private void ensureClassTeacherRolePermissions(AppUser user, Map<AppTab, AppUserTabPermission> existingByTab) {
        if (!user.hasRole(UserRole.CLASS_TEACHER) || !user.isCanView()) return;
        grantRolePermission(existingByTab.get(AppTab.CLASS_TEACHER_EXIT_ORDER_CREATE), true, false);
        grantRolePermission(existingByTab.get(AppTab.CLASS_TEACHER_EXIT_ORDER_SUMMARY), false, false);
        grantRolePermission(existingByTab.get(AppTab.EDUCATIONAL_WORK), true, true);
    }

    private void grantRolePermission(AppUserTabPermission permission, boolean canEdit, boolean canImport) {
        if (permission == null) return;
        boolean changed = !permission.isCanView()
                || (canEdit && !permission.isCanEdit())
                || (canImport && !permission.isCanImport());
        if (!changed) return;
        permission.setCanView(true);
        if (canEdit) permission.setCanEdit(true);
        if (canImport) permission.setCanImport(true);
        tabPermissionRepository.save(permission);
    }

    private AppTab legacySourceTab(AppTab tab) {
        if (tab == AppTab.PEOPLE_LOAD || tab == AppTab.LOAD_ISSUES || tab == AppTab.LOAD_STATS) {
            return AppTab.LOAD;
        }
        if (tab == AppTab.TEACHERS_ARCHIVE || tab == AppTab.TEACHERS_DISMISSALS || tab == AppTab.TEACHERS_SETTINGS
                || tab == AppTab.TEACHERS_MCKO || tab == AppTab.TEACHERS_TIME_OFF) {
            return AppTab.TEACHERS;
        }
        if (tab == AppTab.OVZ) {
            return AppTab.CONTINGENT_STATS;
        }
        if (tab == AppTab.CONTINGENT_ADMISSION) {
            return AppTab.CONTINGENT_STATS;
        }
        if (tab == AppTab.CLASS_TEACHER_EXIT_ORDER_CREATE
                || tab == AppTab.CLASS_TEACHER_EXIT_ORDER_SUMMARY) {
            return AppTab.DOCUMENTS_EXIT_ORDERS;
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
        if (user.hasRole(UserRole.ADMIN)) {
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
        if (!user.hasRole(UserRole.BUILDING_HEAD) || user.getManagedBuildingCode() == null) {
            return;
        }
        String normalizedManagedBuildingCode = normalizeBuildingGroupCode(user.getManagedBuildingCode());
        if (normalizedManagedBuildingCode == null) {
            return;
        }
        appUserRepository.findAll().stream()
                .filter(existing -> existing.hasRole(UserRole.BUILDING_HEAD))
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
        if (user.hasRole(UserRole.ADMIN)) {
            user.setCanView(true);
            user.setCanEdit(true);
            user.setActive(true);
            user.setManagedBuildingCode(null);
            user.setLoadEditAllBuildings(true);
            user.setLoadEditableBuildingCodes(new LinkedHashSet<>());
            return;
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
        if (user == null) return null;
        boolean rolesChanged = user.getRole() != null && (user.getRoles() == null || !user.getRoles().contains(user.getRole()));
        if (rolesChanged) {
            user.setRoles(new LinkedHashSet<>(user.getEffectiveRoles()));
            user = appUserRepository.save(user);
        }
        if (user.hasRole(UserRole.ADMIN)) return user;
        String fio = normalizeOptional(user.getFullName());
        if (fio == null) return user;
        org.school.personalLoad.model.TeacherDirectoryEntry teacher = user.getTeacherId() == null
                ? teacherDirectoryRepository.findByFioTeacherIgnoreCase(fio).orElse(null)
                : teacherDirectoryRepository.findById(user.getTeacherId()).orElse(null);
        if (teacher != null && !Objects.equals(user.getTeacherId(), teacher.getId())) {
            user.setTeacherId(teacher.getId());
            user = appUserRepository.save(user);
        }
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
                user.isCanEdit() || user.hasRole(UserRole.ADMIN),
                user.getManagedBuildingCode(),
                user.isLoadEditAllBuildings() || user.hasRole(UserRole.ADMIN),
                new LinkedHashSet<>(user.getLoadEditableBuildingCodes()),
                loadPermissionSnapshots(user),
                new LinkedHashSet<>(user.getEffectiveRoles())
        );
    }

    private LinkedHashSet<UserRole> normalizeRoles(Collection<UserRole> requestedRoles,
                                                    UserRole requestedPrimaryRole,
                                                    UserRole fallbackRole) {
        LinkedHashSet<UserRole> roles = new LinkedHashSet<>();
        if (requestedRoles != null) {
            requestedRoles.stream().filter(Objects::nonNull).forEach(roles::add);
        }
        if (roles.size() > 1) roles.remove(UserRole.EMPLOYEE);
        if (roles.isEmpty() && requestedPrimaryRole != null) roles.add(requestedPrimaryRole);
        if (roles.isEmpty() && fallbackRole != null) roles.add(fallbackRole);
        if (roles.isEmpty()) throw new IllegalArgumentException("Выберите хотя бы одну роль");
        return roles;
    }

    private UserRole primaryRole(LinkedHashSet<UserRole> roles, UserRole preferredRole) {
        if (roles.contains(UserRole.ADMIN)) return UserRole.ADMIN;
        if (preferredRole != null && roles.contains(preferredRole)) return preferredRole;
        return roles.iterator().next();
    }

    private boolean usesRestrictedRoleProfile(AppUser user) {
        Set<UserRole> roles = user.getEffectiveRoles();
        return !roles.isEmpty() && roles.stream().allMatch(role -> role == UserRole.EMPLOYEE
                || role == UserRole.CLASS_TEACHER || role == UserRole.SECRETARY);
    }

    private boolean defaultCanView(AppUser user, AppTab tab, boolean sensitive) {
        if (user.hasRole(UserRole.ADMIN)) return true;
        if (sensitive || !user.isCanView()) return false;
        if (!usesRestrictedRoleProfile(user)) return true;
        boolean classTeacher = user.hasRole(UserRole.CLASS_TEACHER)
                && (tab == AppTab.CLASS_TEACHER_EXIT_ORDER_CREATE
                || tab == AppTab.CLASS_TEACHER_EXIT_ORDER_SUMMARY
                || tab == AppTab.EDUCATIONAL_WORK);
        boolean secretary = user.hasRole(UserRole.SECRETARY)
                && (tab == AppTab.CONTINGENT_STATS || tab == AppTab.CONTINGENT_ADMISSION);
        return classTeacher || secretary;
    }

    private boolean defaultCanEdit(AppUser user, AppTab tab, boolean sensitive) {
        if (user.hasRole(UserRole.ADMIN)) return true;
        if (sensitive || !user.isCanView() || !user.isCanEdit()) return false;
        if (!usesRestrictedRoleProfile(user)) return true;
        boolean classTeacher = user.hasRole(UserRole.CLASS_TEACHER)
                && (tab == AppTab.CLASS_TEACHER_EXIT_ORDER_CREATE || tab == AppTab.EDUCATIONAL_WORK);
        boolean secretary = user.hasRole(UserRole.SECRETARY) && tab == AppTab.CONTINGENT_ADMISSION;
        return classTeacher || secretary;
    }

    private boolean defaultCanExport(AppUser user, AppTab tab, boolean sensitive) {
        if (!defaultCanView(user, tab, sensitive)) return false;
        return !user.hasRole(UserRole.CLASS_TEACHER) || user.getEffectiveRoles().stream()
                .anyMatch(role -> role != UserRole.CLASS_TEACHER && role != UserRole.EMPLOYEE);
    }
}
