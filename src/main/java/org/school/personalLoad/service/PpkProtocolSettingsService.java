package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.model.PpkProtocolSettings;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.PpkProtocolSettingsRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PpkProtocolSettingsService {
    private final PpkProtocolSettingsRepository repository;
    private final TeacherDirectoryRepository teacherRepository;

    @Transactional
    public OvzDtos.PpkProtocolSettingsView get() {
        PpkProtocolSettings settings = current();
        List<TeacherDirectoryEntry> employees = teacherRepository.findAll();
        migrateLegacyLinks(settings, employees);
        return toView(settings, employees);
    }

    @Transactional(readOnly = true)
    public List<OvzDtos.PpkEmployeeOption> employees() {
        return teacherRepository.findAll().stream()
                .filter(this::selectable)
                .sorted(java.util.Comparator.comparing(
                        TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(this::toEmployeeOption)
                .toList();
    }

    @Transactional
    public OvzDtos.PpkProtocolSettingsView update(OvzDtos.PpkProtocolSettingsRequest request) {
        if (request == null) throw new IllegalArgumentException("Настройки комиссии не переданы");
        List<Long> attendeeIds = request.getAttendeeEmployeeIds() == null
                ? List.of() : request.getAttendeeEmployeeIds().stream().filter(Objects::nonNull).toList();
        if (attendeeIds.isEmpty()) throw new IllegalArgumentException("Добавьте постоянных членов комиссии");
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        unique.add(request.getChairEmployeeId());
        unique.add(request.getSecretaryEmployeeId());
        unique.addAll(attendeeIds);
        if (unique.contains(null)) throw new IllegalArgumentException("Выберите председателя и секретаря из списка сотрудников");
        if (unique.size() != attendeeIds.size() + 2) {
            throw new IllegalArgumentException("Один сотрудник не может занимать в комиссии несколько мест");
        }

        Map<Long, TeacherDirectoryEntry> employees = teacherRepository.findAll().stream()
                .filter(this::selectable)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, Function.identity()));
        TeacherDirectoryEntry chair = requiredEmployee(employees, request.getChairEmployeeId());
        TeacherDirectoryEntry secretary = requiredEmployee(employees, request.getSecretaryEmployeeId());
        List<TeacherDirectoryEntry> attendees = attendeeIds.stream()
                .map(id -> requiredEmployee(employees, id)).toList();
        PpkProtocolSettings settings = current();
        applyChair(settings, chair);
        applySecretary(settings, secretary);
        settings.setAttendeeEmployeeIds(joinIds(attendeeIds));
        settings.setAttendees(attendees.stream().map(this::commissionLine).collect(Collectors.joining("\n")));
        return toView(repository.save(settings), new ArrayList<>(employees.values()));
    }

    @Transactional
    public PpkProtocolSettings current() {
        return repository.findById(PpkProtocolSettings.DEFAULT_ID)
                .orElseGet(() -> repository.save(new PpkProtocolSettings()));
    }

    private OvzDtos.PpkProtocolSettingsView toView(PpkProtocolSettings settings,
                                                   List<TeacherDirectoryEntry> employees) {
        Map<Long, TeacherDirectoryEntry> byId = employees.stream()
                .filter(employee -> employee.getId() != null)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, Function.identity(), (left, right) -> left));
        TeacherDirectoryEntry chair = byId.get(settings.getChairEmployeeId());
        TeacherDirectoryEntry secretary = byId.get(settings.getSecretaryEmployeeId());
        List<TeacherDirectoryEntry> attendees = parseIds(settings.getAttendeeEmployeeIds()).stream()
                .map(byId::get).filter(Objects::nonNull).toList();
        OvzDtos.PpkProtocolSettingsView view = new OvzDtos.PpkProtocolSettingsView();
        view.setChairEmployeeId(settings.getChairEmployeeId());
        view.setChairName(chair == null ? settings.getChairName() : chair.getFioTeacher());
        view.setChairPosition(chair == null ? settings.getChairPosition() : trim(chair.getPrimaryPosition()));
        view.setSecretaryEmployeeId(settings.getSecretaryEmployeeId());
        view.setSecretaryName(secretary == null ? settings.getSecretaryName() : secretary.getFioTeacher());
        view.setSecretaryPosition(secretary == null ? settings.getSecretaryPosition() : trim(secretary.getPrimaryPosition()));
        view.setAttendeeEmployeeIds(attendees.stream().map(TeacherDirectoryEntry::getId).toList());
        view.setAttendeeMembers(attendees.stream().map(this::toMemberView).toList());
        view.setAttendees(attendees.isEmpty() ? settings.getAttendees()
                : attendees.stream().map(this::commissionLine).collect(Collectors.joining("\n")));
        return view;
    }

    private void migrateLegacyLinks(PpkProtocolSettings settings, List<TeacherDirectoryEntry> employees) {
        boolean changed = false;
        if (settings.getChairEmployeeId() == null) {
            TeacherDirectoryEntry employee = findByStoredName(settings.getChairName(), employees);
            if (employee != null) { applyChair(settings, employee); changed = true; }
        }
        if (settings.getSecretaryEmployeeId() == null) {
            TeacherDirectoryEntry employee = findByStoredName(settings.getSecretaryName(), employees);
            if (employee != null) { applySecretary(settings, employee); changed = true; }
        }
        if (parseIds(settings.getAttendeeEmployeeIds()).isEmpty()) {
            List<Long> ids = String.valueOf(settings.getAttendees() == null ? "" : settings.getAttendees()).lines()
                    .map(line -> findByStoredName(line, employees)).filter(Objects::nonNull)
                    .map(TeacherDirectoryEntry::getId).distinct().toList();
            if (!ids.isEmpty()) { settings.setAttendeeEmployeeIds(joinIds(ids)); changed = true; }
        }
        if (changed) repository.save(settings);
    }

    private TeacherDirectoryEntry findByStoredName(String stored, List<TeacherDirectoryEntry> employees) {
        String expected = normalizedName(stored);
        if (expected.isBlank()) return null;
        return employees.stream().filter(this::selectable)
                .filter(employee -> expected.equals(normalizedName(employee.getFioTeacher()))
                        || expected.equals(normalizedName(RussianNameCases.derive(employee.getFioTeacher()).initials())))
                .findFirst().orElse(null);
    }

    private String normalizedName(String value) {
        String clean = String.valueOf(value == null ? "" : value).split("\\s+[—–-]\\s+", 2)[0];
        return clean.toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replaceAll("[^а-яa-z0-9]", "");
    }

    private boolean selectable(TeacherDirectoryEntry employee) {
        if (employee == null || employee.getId() == null || employee.isArchived()) return false;
        String name = trim(employee.getFioTeacher());
        if (name == null || name.toLowerCase(Locale.ROOT).startsWith("вакансия")) return false;
        return employee.getDismissalDate() == null || employee.getDismissalDate().isAfter(LocalDate.now());
    }

    private TeacherDirectoryEntry requiredEmployee(Map<Long, TeacherDirectoryEntry> employees, Long id) {
        TeacherDirectoryEntry employee = employees.get(id);
        if (employee == null) throw new IllegalArgumentException("Выбранный сотрудник не найден в действующих кадровых карточках");
        return employee;
    }

    private void applyChair(PpkProtocolSettings settings, TeacherDirectoryEntry employee) {
        settings.setChairEmployeeId(employee.getId());
        settings.setChairName(employee.getFioTeacher().trim());
        settings.setChairPosition(trim(employee.getPrimaryPosition()));
    }

    private void applySecretary(PpkProtocolSettings settings, TeacherDirectoryEntry employee) {
        settings.setSecretaryEmployeeId(employee.getId());
        settings.setSecretaryName(employee.getFioTeacher().trim());
        settings.setSecretaryPosition(trim(employee.getPrimaryPosition()));
    }

    private String commissionLine(TeacherDirectoryEntry employee) {
        String position = trim(employee.getPrimaryPosition());
        return employee.getFioTeacher().trim() + (position == null ? "" : " — " + position);
    }

    private OvzDtos.PpkCommissionMemberView toMemberView(TeacherDirectoryEntry employee) {
        OvzDtos.PpkCommissionMemberView view = new OvzDtos.PpkCommissionMemberView();
        view.setEmployeeId(employee.getId());
        view.setFullName(employee.getFioTeacher());
        view.setPosition(trim(employee.getPrimaryPosition()));
        return view;
    }

    private OvzDtos.PpkEmployeeOption toEmployeeOption(TeacherDirectoryEntry employee) {
        OvzDtos.PpkEmployeeOption option = new OvzDtos.PpkEmployeeOption();
        option.setEmployeeId(employee.getId());
        option.setFullName(employee.getFioTeacher());
        option.setPosition(trim(employee.getPrimaryPosition()));
        return option;
    }

    private List<Long> parseIds(String source) {
        if (source == null || source.isBlank()) return List.of();
        return java.util.Arrays.stream(source.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> { try { return Long.valueOf(value); } catch (NumberFormatException ignored) { return null; } })
                .filter(Objects::nonNull).distinct().toList();
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
