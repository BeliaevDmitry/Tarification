package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.TeacherTimeOffDtos;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.TeacherTimeOffEntry;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.TeacherTimeOffEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherTimeOffService {
    static final int MINUTES_PER_DAY = 8 * 60;

    private final TeacherTimeOffEntryRepository entryRepository;
    private final TeacherDirectoryRepository teacherRepository;

    @Transactional(readOnly = true)
    public List<TeacherTimeOffDtos.TeacherOption> teachers() {
        return teacherRepository.findAll().stream()
                .filter(this::isActiveEmployee)
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(teacher -> new TeacherTimeOffDtos.TeacherOption(teacher.getId(), teacher.getFioTeacher()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherTimeOffDtos.WorkspaceView workspace() {
        List<TeacherTimeOffEntry> entries = entryRepository.findAllByOrderByEventDateDescCreatedAtDesc();
        Map<Long, String> currentNames = teacherRepository.findAllById(entries.stream()
                        .map(TeacherTimeOffEntry::getTeacherId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, TeacherDirectoryEntry::getFioTeacher));

        Map<Long, Balance> balances = new LinkedHashMap<>();
        for (TeacherTimeOffEntry entry : entries) {
            Balance balance = balances.computeIfAbsent(entry.getTeacherId(), ignored -> new Balance());
            balance.teacherFio = currentNames.getOrDefault(entry.getTeacherId(), entry.getTeacherFioSnapshot());
            if (entry.getOperationType() == TeacherTimeOffEntry.OperationType.EARNED) {
                balance.earnedMinutes = Math.addExact(balance.earnedMinutes, entry.getAmountMinutes());
            } else {
                balance.usedMinutes = Math.addExact(balance.usedMinutes, entry.getAmountMinutes());
            }
        }

        List<TeacherTimeOffDtos.SummaryView> summary = balances.entrySet().stream()
                .map(item -> new TeacherTimeOffDtos.SummaryView(
                        item.getKey(),
                        item.getValue().teacherFio,
                        item.getValue().earnedMinutes,
                        item.getValue().usedMinutes,
                        item.getValue().earnedMinutes - item.getValue().usedMinutes
                ))
                .sorted(Comparator.comparing(TeacherTimeOffDtos.SummaryView::teacherFio,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<TeacherTimeOffDtos.EntryView> views = entries.stream()
                .map(entry -> entryView(entry, currentNames.get(entry.getTeacherId())))
                .toList();
        return new TeacherTimeOffDtos.WorkspaceView(summary, views);
    }

    @Transactional
    public TeacherTimeOffDtos.EntryView addAccrual(TeacherTimeOffDtos.AccrualRequest request, SessionUser actor) {
        if (request == null) {
            throw new IllegalArgumentException("Данные о начислении не переданы");
        }
        TeacherDirectoryEntry teacher = requireActiveTeacher(request.teacherId());
        int minutes = requireFiveMinuteDuration(request.durationMinutes());
        String reason = requireText(request.reason(), "Укажите, за что начисляется отгул", 2000);
        if (request.lessonRemoval() == null) {
            throw new IllegalArgumentException("Укажите, было ли снятие с уроков");
        }

        TeacherTimeOffEntry entry = baseEntry(teacher, actor, requireDate(request.eventDate()));
        entry.setOperationType(TeacherTimeOffEntry.OperationType.EARNED);
        entry.setAmountMinutes(minutes);
        entry.setReason(reason);
        entry.setLessonRemoval(request.lessonRemoval());
        return entryView(entryRepository.save(entry), teacher.getFioTeacher());
    }

    @Transactional
    public TeacherTimeOffDtos.EntryView useTimeOff(TeacherTimeOffDtos.UsageRequest request, SessionUser actor) {
        if (request == null) {
            throw new IllegalArgumentException("Данные об использовании отгула не переданы");
        }
        TeacherDirectoryEntry teacher = requireActiveTeacher(request.teacherId());
        int minutes = daysToMinutes(request.days());
        int available = balanceMinutes(teacher.getId());
        if (minutes > available) {
            throw new IllegalArgumentException("Недостаточно часов отгула. Доступно: " + formatMinutes(available));
        }

        TeacherTimeOffEntry entry = baseEntry(teacher, actor, requireDate(request.eventDate()));
        entry.setOperationType(TeacherTimeOffEntry.OperationType.USED);
        entry.setAmountMinutes(minutes);
        entry.setReason("Использование отгула");
        entry.setLessonRemoval(null);
        return entryView(entryRepository.save(entry), teacher.getFioTeacher());
    }

    private int balanceMinutes(Long teacherId) {
        int earned = 0;
        int used = 0;
        for (TeacherTimeOffEntry entry : entryRepository.findAllByTeacherId(teacherId)) {
            if (entry.getOperationType() == TeacherTimeOffEntry.OperationType.EARNED) {
                earned = Math.addExact(earned, entry.getAmountMinutes());
            } else {
                used = Math.addExact(used, entry.getAmountMinutes());
            }
        }
        return earned - used;
    }

    private TeacherTimeOffEntry baseEntry(TeacherDirectoryEntry teacher, SessionUser actor, LocalDate eventDate) {
        if (actor == null) {
            throw new IllegalArgumentException("Не удалось определить пользователя, который вносит данные");
        }
        TeacherTimeOffEntry entry = new TeacherTimeOffEntry();
        entry.setTeacherId(teacher.getId());
        entry.setTeacherFioSnapshot(teacher.getFioTeacher());
        entry.setEventDate(eventDate);
        entry.setCreatedByUserId(actor.getId());
        entry.setCreatedByUsername(requireText(actor.getUsername(), "Не указан логин пользователя", 100));
        entry.setCreatedByFio(displayActor(actor));
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    private TeacherTimeOffDtos.EntryView entryView(TeacherTimeOffEntry entry, String currentFio) {
        return new TeacherTimeOffDtos.EntryView(
                entry.getId(),
                entry.getTeacherId(),
                currentFio == null || currentFio.isBlank() ? entry.getTeacherFioSnapshot() : currentFio,
                entry.getOperationType(),
                entry.getAmountMinutes(),
                entry.getReason(),
                entry.getLessonRemoval(),
                entry.getEventDate(),
                entry.getCreatedAt(),
                entry.getCreatedByFio()
        );
    }

    private TeacherDirectoryEntry requireActiveTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new IllegalArgumentException("Выберите сотрудника из раздела «Кадры»");
        }
        TeacherDirectoryEntry teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Сотрудник из раздела «Кадры» не найден"));
        if (!isActiveEmployee(teacher)) {
            throw new IllegalArgumentException("Выбранный сотрудник уволен, находится в архиве или является вакансией");
        }
        return teacher;
    }

    private boolean isActiveEmployee(TeacherDirectoryEntry teacher) {
        return teacher != null
                && teacher.getId() != null
                && !teacher.isArchived()
                && teacher.getDismissalDate() == null
                && teacher.getFioTeacher() != null
                && !teacher.getFioTeacher().isBlank()
                && !"вакансия".equalsIgnoreCase(teacher.getFioTeacher().trim());
    }

    private int requireFiveMinuteDuration(Integer value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Укажите положительные трудозатраты");
        }
        if (value % 5 != 0) {
            throw new IllegalArgumentException("Трудозатраты должны быть кратны 5 минутам");
        }
        return value;
    }

    private int daysToMinutes(BigDecimal days) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Укажите положительное количество дней отгула");
        }
        final int minutes;
        try {
            minutes = days.multiply(BigDecimal.valueOf(MINUTES_PER_DAY)).intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Количество дней должно соответствовать целому числу минут");
        }
        if (minutes % 5 != 0) {
            throw new IllegalArgumentException("Продолжительность отгула должна быть кратна 5 минутам");
        }
        return minutes;
    }

    private LocalDate requireDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("Укажите дату");
        }
        return value;
    }

    private String requireText(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Значение слишком длинное (не более " + maxLength + " символов)");
        }
        return normalized;
    }

    private String displayActor(SessionUser actor) {
        String fullName = actor.getFullName() == null ? "" : actor.getFullName().trim();
        return fullName.isEmpty() ? actor.getUsername() : fullName;
    }

    private String formatMinutes(int minutes) {
        return (minutes / 60) + " ч " + (minutes % 60) + " мин";
    }

    private static final class Balance {
        private String teacherFio;
        private int earnedMinutes;
        private int usedMinutes;
    }
}
