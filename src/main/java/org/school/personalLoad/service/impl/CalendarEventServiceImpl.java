package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.CalendarDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.CalendarEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarEventServiceImpl implements CalendarEventService {

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final List<String> DEFAULT_COLORS = List.of(
            "#2563eb", "#7c3aed", "#db2777", "#dc2626", "#ea580c",
            "#ca8a04", "#16a34a", "#0d9488", "#0891b2", "#4f46e5");

    private final CalendarEventRepository eventRepository;
    private final CalendarUserSettingsRepository settingsRepository;
    private final CalendarCustomListRepository customListRepository;
    private final CalendarAudienceMembershipRepository membershipRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final SchoolBuildingRepository buildingRepository;
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CalendarDtos.EventView> list(LocalDate from, LocalDate to, SessionUser user) {
        AppUser viewer = currentUser(user);
        LocalDate start = from == null ? LocalDate.now().minusMonths(1) : from;
        LocalDate end = to == null ? start.plusMonths(2) : to;
        if (end.isBefore(start)) throw new IllegalArgumentException("Дата окончания раньше даты начала");
        if (Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays() > 370) {
            throw new IllegalArgumentException("За один раз можно открыть календарь максимум за год");
        }
        List<CalendarEvent> events = eventRepository
                .findDistinctByStartsAtLessThanAndEndsAtGreaterThanEqualOrderByStartsAtAsc(
                        end.plusDays(1).atStartOfDay(), start.atStartOfDay());
        Set<Long> ownerIds = events.stream().map(CalendarEvent::getOwner).filter(Objects::nonNull)
                .map(AppUser::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CalendarUserSettings> settings = settingsRepository.findAllByUser_IdIn(ownerIds).stream()
                .filter(row -> row.getUser() != null)
                .collect(Collectors.toMap(row -> row.getUser().getId(), Function.identity(), (left, right) -> left));
        Set<CalendarAudienceGroup> viewerGroups = groupsForTeacher(viewer.getTeacherId());
        return events.stream()
                .filter(event -> canView(event, viewer, user, settings.get(ownerId(event)), viewerGroups))
                .map(event -> toView(event, settings.get(ownerId(event)), viewer, user))
                .toList();
    }

    @Override
    @Transactional
    public CalendarDtos.EventView create(CalendarDtos.EventRequest request, SessionUser user) {
        AppUser owner = currentUser(user);
        CalendarEvent event = new CalendarEvent();
        event.setOwner(owner);
        event.setCreatedAt(LocalDateTime.now());
        apply(event, request, owner);
        CalendarEvent saved = eventRepository.save(event);
        return toView(saved, settingsRepository.findByUser_Id(owner.getId()).orElse(null), owner, user);
    }

    @Override
    @Transactional
    public CalendarDtos.EventView update(Long id, CalendarDtos.EventRequest request, SessionUser user) {
        AppUser editor = currentUser(user);
        CalendarEvent event = requireEvent(id);
        ensureCanEdit(event, editor, user);
        apply(event, request, event.getOwner());
        event.setUpdatedAt(LocalDateTime.now());
        CalendarEvent saved = eventRepository.save(event);
        return toView(saved, settingsRepository.findByUser_Id(ownerId(saved)).orElse(null), editor, user);
    }

    @Override
    @Transactional
    public void delete(Long id, SessionUser user) {
        AppUser editor = currentUser(user);
        CalendarEvent event = requireEvent(id);
        ensureCanEdit(event, editor, user);
        eventRepository.delete(event);
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarDtos.BootstrapView bootstrap(SessionUser user) {
        AppUser owner = currentUser(user);
        CalendarUserSettings settings = settingsRepository.findByUser_Id(owner.getId()).orElse(null);
        List<CalendarDtos.VisibilityOption> visibility = Arrays.stream(CalendarEventVisibility.values())
                .map(value -> new CalendarDtos.VisibilityOption(value.name(), value.getDisplayName()))
                .toList();
        return new CalendarDtos.BootstrapView(preferences(owner, settings), visibility, customLists(owner));
    }

    @Override
    @Transactional
    public CalendarDtos.PreferencesView updatePreferences(CalendarDtos.PreferencesRequest request, SessionUser user) {
        AppUser owner = currentUser(user);
        if (request == null) throw new IllegalArgumentException("Настройки календаря не заполнены");
        String color = text(request.color());
        if (!COLOR.matcher(color).matches()) throw new IllegalArgumentException("Выберите корректный цвет календаря");
        CalendarEventVisibility visibility = request.defaultVisibility() == null
                ? CalendarEventVisibility.PARTICIPANTS : request.defaultVisibility();
        Set<Long> viewerIds = ids(request.sharedWithPersonIds());
        Map<Long, TeacherDirectoryEntry> viewers = activeTeachers(viewerIds, "Не найдены сотрудники для доступа к календарю");
        CalendarUserSettings settings = settingsRepository.findByUser_Id(owner.getId()).orElseGet(() -> {
            CalendarUserSettings created = new CalendarUserSettings();
            created.setUser(owner);
            return created;
        });
        settings.setColor(color.toLowerCase(Locale.ROOT));
        settings.setDefaultVisibility(visibility);
        settings.getSharedWith().clear();
        viewerIds.stream().map(viewers::get).filter(Objects::nonNull).forEach(settings.getSharedWith()::add);
        settings.setUpdatedAt(LocalDateTime.now());
        return preferences(owner, settingsRepository.save(settings));
    }

    @Override
    @Transactional
    public CalendarDtos.CustomListView createCustomList(CalendarDtos.CustomListRequest request, SessionUser user) {
        AppUser owner = currentUser(user);
        String name = customListName(request);
        if (customListRepository.existsByOwner_IdAndNameIgnoreCase(owner.getId(), name)) {
            throw new IllegalArgumentException("Список с таким названием уже существует");
        }
        CalendarCustomList list = new CalendarCustomList();
        list.setOwner(owner);
        updateCustomListEntity(list, request, name);
        return customListView(customListRepository.save(list));
    }

    @Override
    @Transactional
    public CalendarDtos.CustomListView updateCustomList(Long id,
                                                        CalendarDtos.CustomListRequest request,
                                                        SessionUser user) {
        AppUser owner = currentUser(user);
        CalendarCustomList list = customListRepository.findByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(() -> new IllegalArgumentException("Личный список не найден"));
        String name = customListName(request);
        if (customListRepository.existsByOwner_IdAndNameIgnoreCaseAndIdNot(owner.getId(), name, id)) {
            throw new IllegalArgumentException("Список с таким названием уже существует");
        }
        updateCustomListEntity(list, request, name);
        return customListView(customListRepository.save(list));
    }

    @Override
    @Transactional
    public void deleteCustomList(Long id, SessionUser user) {
        AppUser owner = currentUser(user);
        CalendarCustomList list = customListRepository.findByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(() -> new IllegalArgumentException("Личный список не найден"));
        customListRepository.delete(list);
    }

    private void apply(CalendarEvent event, CalendarDtos.EventRequest request, AppUser owner) {
        if (request == null) throw new IllegalArgumentException("Заполните встречу");
        String title = text(request.title());
        if (title.isBlank()) throw new IllegalArgumentException("Укажите название встречи");
        if (title.length() > 500) throw new IllegalArgumentException("Название встречи слишком длинное");
        if (request.date() == null || request.startTime() == null) {
            throw new IllegalArgumentException("Укажите дату и время начала");
        }
        int duration = request.durationMinutes() == null ? 60 : request.durationMinutes();
        if (duration < 5 || duration > 1440) {
            throw new IllegalArgumentException("Продолжительность должна быть от 5 минут до 24 часов");
        }
        String place = text(request.place());
        if (place.length() > 1000) throw new IllegalArgumentException("Место проведения слишком длинное");

        Set<Long> selectedPeople = ids(request.personIds());
        Set<Long> selectedBuildings = ids(request.buildingIds());
        Set<Long> selectedLists = ids(request.customListIds());
        Set<CalendarAudienceGroup> selectedGroups = parseGroups(request.groupCodes());
        Map<Long, TeacherDirectoryEntry> activePeople = activePeopleById();
        requireExisting(selectedPeople, activePeople.keySet(), "Не найдены выбранные участники");

        Map<Long, SchoolBuilding> buildings = buildingRepository.findAllById(selectedBuildings).stream()
                .collect(Collectors.toMap(SchoolBuilding::getId, Function.identity()));
        requireExisting(selectedBuildings, buildings.keySet(), "Не найдены выбранные корпуса");
        List<CalendarCustomList> customLists = selectedLists.isEmpty() ? List.of()
                : customListRepository.findAllByOwner_IdAndIdIn(owner.getId(), selectedLists);
        requireExisting(selectedLists, customLists.stream().map(CalendarCustomList::getId).collect(Collectors.toSet()),
                "Не найдены выбранные личные списки");

        LinkedHashSet<TeacherDirectoryEntry> participants = new LinkedHashSet<>();
        selectedPeople.stream().map(activePeople::get).filter(Objects::nonNull).forEach(participants::add);
        Map<CalendarAudienceGroup, Set<Long>> groupMembers = allGroupMembers();
        selectedGroups.stream().flatMap(group -> groupMembers.getOrDefault(group, Set.of()).stream())
                .map(activePeople::get).filter(Objects::nonNull).forEach(participants::add);
        for (SchoolBuilding building : buildings.values()) {
            activePeople.values().stream().filter(person -> sameBuilding(person.getNumberSchoolBuilding(), building.getCode()))
                    .forEach(participants::add);
        }
        customLists.stream().flatMap(list -> list.getMembers().stream()).filter(this::isActive).forEach(participants::add);

        CalendarUserSettings ownerSettings = settingsRepository.findByUser_Id(owner.getId()).orElse(null);
        event.setTitle(title);
        event.setStartsAt(LocalDateTime.of(request.date(), request.startTime()));
        event.setDurationMinutes(duration);
        event.setEndsAt(event.getStartsAt().plusMinutes(duration));
        event.setPlace(place);
        event.setVisibility(request.visibility() == null
                ? defaultVisibility(ownerSettings) : request.visibility());
        event.getSelectedPersonIds().clear();
        event.getSelectedPersonIds().addAll(selectedPeople);
        event.getSelectedGroups().clear();
        event.getSelectedGroups().addAll(selectedGroups);
        event.getSelectedCustomListIds().clear();
        event.getSelectedCustomListIds().addAll(selectedLists);
        event.getBuildings().clear();
        selectedBuildings.stream().map(buildings::get).filter(Objects::nonNull).forEach(event.getBuildings()::add);
        event.getParticipants().clear();
        event.getParticipants().addAll(participants);
        event.setAudienceSummary(audienceSummary(selectedPeople, selectedGroups, selectedBuildings, customLists,
                activePeople, buildings));
        event.setUpdatedAt(LocalDateTime.now());
    }

    private boolean canView(CalendarEvent event,
                            AppUser viewer,
                            SessionUser session,
                            CalendarUserSettings ownerSettings,
                            Set<CalendarAudienceGroup> viewerGroups) {
        if (event.getOwner() == null) return false;
        if (Objects.equals(event.getOwner().getId(), viewer.getId())) return true;
        if (session.isAdmin() || session.getRole() == UserRole.DIRECTOR) return true;
        Long teacherId = viewer.getTeacherId();
        if (teacherId != null && ownerSettings != null && ownerSettings.getSharedWith().stream()
                .anyMatch(person -> Objects.equals(person.getId(), teacherId))) return true;
        if (event.getVisibility() != CalendarEventVisibility.PRIVATE && teacherId != null
                && event.getParticipants().stream()
                .anyMatch(person -> Objects.equals(person.getId(), teacherId))) return true;
        return switch (event.getVisibility()) {
            case PRIVATE, PARTICIPANTS -> false;
            case DEPUTIES -> session.getRole() == UserRole.DEPUTY_DIRECTOR
                    || viewerGroups.contains(CalendarAudienceGroup.DEPUTIES);
            case ADMINISTRATION -> session.getRole() == UserRole.DEPUTY_DIRECTOR
                    || viewerGroups.contains(CalendarAudienceGroup.ADMINISTRATION)
                    || viewerGroups.contains(CalendarAudienceGroup.FULL_ADMINISTRATION);
            case EVERYONE -> true;
        };
    }

    private CalendarDtos.EventView toView(CalendarEvent event,
                                          CalendarUserSettings ownerSettings,
                                          AppUser viewer,
                                          SessionUser session) {
        AppUser owner = event.getOwner();
        List<CalendarDtos.PersonRef> participants = event.getParticipants().stream()
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(this::personRef).toList();
        List<CalendarDtos.BuildingRef> buildings = event.getBuildings().stream()
                .sorted(Comparator.comparing(building -> text(building.getAddress()), String.CASE_INSENSITIVE_ORDER))
                .map(this::buildingRef).toList();
        return new CalendarDtos.EventView(event.getId(), "MANUAL", event.getTitle(),
                event.getStartsAt().toLocalDate(), event.getStartsAt().toLocalTime(), event.getEndsAt().toLocalTime(),
                event.getDurationMinutes(), event.getPlace(), event.getVisibility(), event.getVisibility().getDisplayName(),
                owner.getId(), owner.getTeacherId(), owner.getFullName(), color(owner, ownerSettings),
                event.getAudienceSummary(), participants, buildings,
                event.getSelectedPersonIds().stream().sorted().toList(),
                event.getSelectedGroups().stream().map(Enum::name).sorted().toList(),
                event.getBuildings().stream().map(SchoolBuilding::getId).sorted().toList(),
                event.getSelectedCustomListIds().stream().sorted().toList(),
                canEdit(event, viewer, session));
    }

    private CalendarDtos.PreferencesView preferences(AppUser owner, CalendarUserSettings settings) {
        List<Long> sharedWith = settings == null ? List.of() : settings.getSharedWith().stream()
                .map(TeacherDirectoryEntry::getId).filter(Objects::nonNull).sorted().toList();
        return new CalendarDtos.PreferencesView(owner.getId(), owner.getTeacherId(), color(owner, settings),
                defaultVisibility(settings), sharedWith);
    }

    private List<CalendarDtos.CustomListView> customLists(AppUser owner) {
        return customListRepository.findAllByOwner_IdOrderByNameAsc(owner.getId()).stream()
                .map(this::customListView).toList();
    }

    private CalendarDtos.CustomListView customListView(CalendarCustomList list) {
        return new CalendarDtos.CustomListView(list.getId(), list.getName(), list.getMembers().stream()
                .map(TeacherDirectoryEntry::getId).filter(Objects::nonNull).sorted().toList());
    }

    private void updateCustomListEntity(CalendarCustomList list,
                                        CalendarDtos.CustomListRequest request,
                                        String name) {
        Set<Long> memberIds = ids(request == null ? null : request.personIds());
        Map<Long, TeacherDirectoryEntry> members = activeTeachers(memberIds, "Не найдены участники личного списка");
        list.setName(name);
        list.getMembers().clear();
        memberIds.stream().map(members::get).filter(Objects::nonNull).forEach(list.getMembers()::add);
        list.setUpdatedAt(LocalDateTime.now());
    }

    private String customListName(CalendarDtos.CustomListRequest request) {
        String name = text(request == null ? null : request.name());
        if (name.isBlank()) throw new IllegalArgumentException("Укажите название списка");
        if (name.length() > 255) throw new IllegalArgumentException("Название списка слишком длинное");
        return name;
    }

    private String audienceSummary(Set<Long> selectedPeople,
                                   Set<CalendarAudienceGroup> selectedGroups,
                                   Set<Long> selectedBuildings,
                                   List<CalendarCustomList> customLists,
                                   Map<Long, TeacherDirectoryEntry> people,
                                   Map<Long, SchoolBuilding> buildings) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        selectedGroups.stream().sorted(Comparator.comparing(Enum::ordinal))
                .map(CalendarAudienceGroup::getDisplayName).forEach(labels::add);
        selectedBuildings.stream().map(buildings::get).filter(Objects::nonNull)
                .map(building -> firstNotBlank(building.getAddress(), building.getName(), building.getCode()))
                .forEach(labels::add);
        customLists.stream().map(CalendarCustomList::getName).forEach(labels::add);
        selectedPeople.stream().map(people::get).filter(Objects::nonNull)
                .map(TeacherDirectoryEntry::getFioTeacher).forEach(labels::add);
        return String.join("; ", labels);
    }

    private Map<CalendarAudienceGroup, Set<Long>> allGroupMembers() {
        Map<CalendarAudienceGroup, Set<Long>> result = new EnumMap<>(CalendarAudienceGroup.class);
        Arrays.stream(CalendarAudienceGroup.values()).forEach(group -> result.put(group, new LinkedHashSet<>()));
        membershipRepository.findAll().stream()
                .filter(row -> row.getGroupCode() != null && row.getTeacher() != null && isActive(row.getTeacher()))
                .forEach(row -> result.get(row.getGroupCode()).add(row.getTeacher().getId()));
        return result;
    }

    private Set<CalendarAudienceGroup> groupsForTeacher(Long teacherId) {
        if (teacherId == null) return Set.of();
        return allGroupMembers().entrySet().stream().filter(entry -> entry.getValue().contains(teacherId))
                .map(Map.Entry::getKey).collect(Collectors.toCollection(() -> EnumSet.noneOf(CalendarAudienceGroup.class)));
    }

    private Map<Long, TeacherDirectoryEntry> activePeopleById() {
        return teacherRepository.findAll().stream().filter(this::isActive)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, TeacherDirectoryEntry> activeTeachers(Set<Long> ids, String message) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, TeacherDirectoryEntry> result = teacherRepository.findAllById(ids).stream().filter(this::isActive)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, Function.identity()));
        requireExisting(ids, result.keySet(), message);
        return result;
    }

    private void requireExisting(Set<Long> requested, Set<Long> found, String message) {
        Set<Long> missing = requested.stream().filter(id -> !found.contains(id))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) throw new IllegalArgumentException(message + ": " + missing);
    }

    private Set<CalendarAudienceGroup> parseGroups(List<String> codes) {
        Set<CalendarAudienceGroup> result = EnumSet.noneOf(CalendarAudienceGroup.class);
        if (codes == null) return result;
        for (String code : codes) {
            try {
                result.add(CalendarAudienceGroup.valueOf(text(code).toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Неизвестная группа участников: " + text(code));
            }
        }
        return result;
    }

    private Set<Long> ids(List<Long> values) {
        if (values == null) return new LinkedHashSet<>();
        return values.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean sameBuilding(String personCode, String buildingCode) {
        String person = buildingKey(personCode);
        String building = buildingKey(buildingCode);
        return !person.isBlank() && person.equals(building);
    }

    private String buildingKey(String value) {
        String normalized = text(value).toUpperCase(Locale.ROOT).replace('–', '-').replace('—', '-')
                .replaceAll("[CС][ПPР]", "СП").replaceAll("\\s+", "");
        int separator = normalized.indexOf('|');
        if (separator >= 0) normalized = normalized.substring(0, separator);
        separator = normalized.indexOf("::");
        if (separator >= 0) normalized = normalized.substring(0, separator);
        return normalized.replaceFirst("^СП-(\\d+)$", "СП$1");
    }

    private boolean isActive(TeacherDirectoryEntry person) {
        return person != null && person.getId() != null && person.getDismissalDate() == null && !person.isArchived()
                && !text(person.getFioTeacher()).isBlank()
                && !text(person.getFioTeacher()).toLowerCase(Locale.ROOT).startsWith("вакансия");
    }

    private CalendarEventVisibility defaultVisibility(CalendarUserSettings settings) {
        return settings == null || settings.getDefaultVisibility() == null
                ? CalendarEventVisibility.PARTICIPANTS : settings.getDefaultVisibility();
    }

    private String color(AppUser owner, CalendarUserSettings settings) {
        String configured = settings == null ? "" : text(settings.getColor());
        if (COLOR.matcher(configured).matches()) return configured.toLowerCase(Locale.ROOT);
        long seed = owner == null || owner.getId() == null ? 0 : owner.getId();
        return DEFAULT_COLORS.get((int) Math.floorMod(seed, DEFAULT_COLORS.size()));
    }

    private boolean canEdit(CalendarEvent event, AppUser user, SessionUser session) {
        return event.getOwner() != null && (Objects.equals(event.getOwner().getId(), user.getId())
                || session.isAdmin() || session.getRole() == UserRole.DIRECTOR);
    }

    private void ensureCanEdit(CalendarEvent event, AppUser user, SessionUser session) {
        if (!canEdit(event, user, session)) {
            throw new AuthExceptions.ForbiddenException("Изменить встречу может её создатель или администрация");
        }
    }

    private CalendarEvent requireEvent(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Встреча не найдена"));
    }

    private AppUser currentUser(SessionUser user) {
        if (user == null || user.getId() == null) throw new AuthExceptions.UnauthorizedException("Требуется вход в систему");
        AppUser result = appUserRepository.findById(user.getId())
                .orElseThrow(() -> new AuthExceptions.UnauthorizedException("Пользователь не найден"));
        if (!result.isActive()) throw new AuthExceptions.ForbiddenException("Пользователь отключён");
        return result;
    }

    private Long ownerId(CalendarEvent event) {
        return event.getOwner() == null ? null : event.getOwner().getId();
    }

    private CalendarDtos.PersonRef personRef(TeacherDirectoryEntry person) {
        return new CalendarDtos.PersonRef(person.getId(), person.getFioTeacher(),
                text(person.getPrimaryPosition()), text(person.getNumberSchoolBuilding()));
    }

    private CalendarDtos.BuildingRef buildingRef(SchoolBuilding building) {
        return new CalendarDtos.BuildingRef(building.getId(), building.getCode(), building.getName(), building.getAddress());
    }

    private String firstNotBlank(String... values) {
        for (String value : values) if (!text(value).isBlank()) return text(value);
        return "";
    }

    private String text(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
