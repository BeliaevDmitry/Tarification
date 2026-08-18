package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.CalendarAudienceDtos;
import org.school.personalLoad.model.CalendarAudienceGroup;
import org.school.personalLoad.model.CalendarAudienceMembership;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.CalendarAudienceMembershipRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.CalendarAudienceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarAudienceServiceImpl implements CalendarAudienceService {

    private final CalendarAudienceMembershipRepository membershipRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final SchoolBuildingRepository buildingRepository;

    @Override
    @Transactional(readOnly = true)
    public CalendarAudienceDtos.SettingsView settings(SessionUser user) {
        List<TeacherDirectoryEntry> people = activePeople();
        Set<Long> activeIds = people.stream().map(TeacherDirectoryEntry::getId).collect(Collectors.toSet());
        Map<CalendarAudienceGroup, List<Long>> members = new EnumMap<>(CalendarAudienceGroup.class);
        for (CalendarAudienceGroup group : CalendarAudienceGroup.values()) {
            members.put(group, new ArrayList<>());
        }
        membershipRepository.findAll().stream()
                .filter(row -> row.getTeacher() != null && activeIds.contains(row.getTeacher().getId()))
                .forEach(row -> members.get(row.getGroupCode()).add(row.getTeacher().getId()));
        members.values().forEach(ids -> ids.sort(Long::compareTo));
        return view(people, members, user);
    }

    @Override
    @Transactional
    public CalendarAudienceDtos.SettingsView update(CalendarAudienceDtos.UpdateRequest request, SessionUser user) {
        ensureCanEdit(user);
        Map<CalendarAudienceGroup, Set<Long>> desired = parseRequest(request);
        Set<Long> requestedIds = desired.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        Map<Long, TeacherDirectoryEntry> teachersById = teacherRepository.findAllById(requestedIds).stream()
                .filter(this::isActive)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, Function.identity()));
        Set<Long> missing = requestedIds.stream()
                .filter(id -> !teachersById.containsKey(id))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Не найдены действующие сотрудники: " + missing);
        }

        List<CalendarAudienceMembership> existing = membershipRepository.findAll();
        Map<MemberKey, CalendarAudienceMembership> existingByKey = existing.stream()
                .filter(row -> row.getTeacher() != null && row.getTeacher().getId() != null && row.getGroupCode() != null)
                .collect(Collectors.toMap(
                        row -> new MemberKey(row.getGroupCode(), row.getTeacher().getId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<MemberKey> desiredKeys = desired.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(id -> new MemberKey(entry.getKey(), id)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<CalendarAudienceMembership> toDelete = existingByKey.entrySet().stream()
                .filter(entry -> !desiredKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            membershipRepository.deleteAll(toDelete);
        }

        List<CalendarAudienceMembership> toCreate = desiredKeys.stream()
                .filter(key -> !existingByKey.containsKey(key))
                .map(key -> membership(key, teachersById.get(key.teacherId()), user))
                .toList();
        if (!toCreate.isEmpty()) {
            membershipRepository.saveAll(toCreate);
        }

        List<TeacherDirectoryEntry> people = activePeople();
        return view(people, desired.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new ArrayList<>(entry.getValue()),
                (left, right) -> left,
                () -> new EnumMap<>(CalendarAudienceGroup.class)
        )), user);
    }

    private CalendarAudienceDtos.SettingsView view(List<TeacherDirectoryEntry> people,
                                                    Map<CalendarAudienceGroup, List<Long>> members,
                                                    SessionUser user) {
        List<CalendarAudienceDtos.PersonOption> personOptions = people.stream()
                .map(person -> new CalendarAudienceDtos.PersonOption(person.getId(), person.getFioTeacher(),
                        text(person.getPrimaryPosition()), text(person.getNumberSchoolBuilding())))
                .toList();
        List<CalendarAudienceDtos.BuildingOption> buildings = buildingRepository.findAll().stream()
                .sorted(Comparator.comparing((SchoolBuilding building) -> text(building.getAddress()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(building -> text(building.getCode()), String.CASE_INSENSITIVE_ORDER))
                .map(building -> new CalendarAudienceDtos.BuildingOption(building.getId(), building.getCode(),
                        building.getName(), building.getAddress()))
                .toList();
        List<CalendarAudienceDtos.GroupOption> groups = Arrays.stream(CalendarAudienceGroup.values())
                .map(group -> new CalendarAudienceDtos.GroupOption(group.name(), group.getDisplayName(),
                        List.copyOf(members.getOrDefault(group, List.of()))))
                .toList();
        return new CalendarAudienceDtos.SettingsView(personOptions, buildings, groups, canEdit(user));
    }

    private Map<CalendarAudienceGroup, Set<Long>> parseRequest(CalendarAudienceDtos.UpdateRequest request) {
        Map<CalendarAudienceGroup, Set<Long>> result = new EnumMap<>(CalendarAudienceGroup.class);
        for (CalendarAudienceGroup group : CalendarAudienceGroup.values()) {
            result.put(group, new LinkedHashSet<>());
        }
        List<CalendarAudienceDtos.GroupSelection> groups = request == null || request.groups() == null
                ? List.of() : request.groups();
        for (CalendarAudienceDtos.GroupSelection selection : groups) {
            CalendarAudienceGroup group;
            try {
                group = CalendarAudienceGroup.valueOf(text(selection.code()).toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Неизвестная группа календаря: " + text(selection.code()));
            }
            if (selection.personIds() != null) {
                selection.personIds().stream().filter(Objects::nonNull).forEach(result.get(group)::add);
            }
        }
        return result;
    }

    private CalendarAudienceMembership membership(MemberKey key,
                                                  TeacherDirectoryEntry teacher,
                                                  SessionUser user) {
        CalendarAudienceMembership result = new CalendarAudienceMembership();
        result.setGroupCode(key.group());
        result.setTeacher(teacher);
        result.setUpdatedAt(LocalDateTime.now());
        result.setUpdatedBy(text(user.getFullName()).isBlank() ? user.getUsername() : user.getFullName());
        return result;
    }

    private List<TeacherDirectoryEntry> activePeople() {
        return teacherRepository.findAll().stream()
                .filter(this::isActive)
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean isActive(TeacherDirectoryEntry person) {
        return person != null && person.getId() != null && person.getDismissalDate() == null && !person.isArchived()
                && !text(person.getFioTeacher()).isBlank()
                && !text(person.getFioTeacher()).toLowerCase(Locale.ROOT).startsWith("вакансия");
    }

    private void ensureCanEdit(SessionUser user) {
        if (!canEdit(user)) {
            throw new AuthExceptions.ForbiddenException(
                    "Состав групп календаря может менять только директор или администратор");
        }
    }

    private boolean canEdit(SessionUser user) {
        return user != null && (user.isAdmin() || user.getRole() == UserRole.DIRECTOR);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record MemberKey(CalendarAudienceGroup group, Long teacherId) {
    }
}
