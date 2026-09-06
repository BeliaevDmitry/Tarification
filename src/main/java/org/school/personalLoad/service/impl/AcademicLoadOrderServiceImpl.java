package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.school.personalLoad.dto.AcademicLoadOrderDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.AcademicLoadOrderRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.AcademicLoadOrderService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademicLoadOrderServiceImpl implements AcademicLoadOrderService {

    private final AcademicLoadOrderRepository orderRepository;
    private final CurriculumPlanEntryRepository curriculumRepository;
    private final ManualLoadEntryRepository loadRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final AcademicYearService academicYearService;
    private final AcademicLoadOrderDocumentService documentService;

    @Override
    @Transactional(readOnly = true)
    public List<AcademicLoadOrderDtos.OrderView> list(String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return orderRepository.findAllByAcademicYearOrderByOrderDateDescCreatedAtDesc(year).stream()
                .map(this::view)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicLoadOrderDtos.ReadinessView readiness(String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        List<CurriculumPlanEntry> curriculum = activeCurriculum(year);
        List<ManualLoadEntry> load = activeLoad(year);
        int planCount = (int) curriculum.stream()
                .map(row -> normalized(row.getNumberSchoolBuilding()) + "|" + row.getStage() + "|" + normalized(row.getClassName()))
                .distinct()
                .count();
        int teacherCount = (int) load.stream()
                .map(ManualLoadEntry::getFioTeacher)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        return new AcademicLoadOrderDtos.ReadinessView(year, curriculum.size(), planCount, load.size(), teacherCount);
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicLoadOrderDtos.ReferencesView references() {
        List<TeacherDirectoryEntry> teachers = teacherRepository.findAll().stream()
                .filter(teacher -> !teacher.isArchived())
                .filter(teacher -> teacher.getFioTeacher() != null && !teacher.getFioTeacher().isBlank())
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, naturalTextComparator()))
                .toList();
        Long suggested = teachers.stream()
                .filter(teacher -> normalized(teacher.getPrimaryPosition()).contains("директор"))
                .filter(teacher -> !normalized(teacher.getPrimaryPosition()).contains("замест"))
                .map(TeacherDirectoryEntry::getId)
                .findFirst()
                .orElse(null);
        List<AcademicLoadOrderDtos.StaffView> staff = teachers.stream()
                .map(teacher -> new AcademicLoadOrderDtos.StaffView(
                        teacher.getId(), teacher.getFioTeacher(), trim(teacher.getPrimaryPosition())))
                .toList();
        return new AcademicLoadOrderDtos.ReferencesView(staff, suggested);
    }

    @Override
    public AcademicLoadOrderDtos.OrderView create(AcademicLoadOrderDtos.CreateRequest request,
                                                   String schoolCode,
                                                   SessionUser user) {
        if (request == null) throw new IllegalArgumentException("Данные приказа не переданы");
        AcademicLoadOrderType type = Objects.requireNonNull(request.type(), "Выберите вид приказа");
        String year = academicYearService.resolveRequestedOrDefault(request.academicYear());
        String number = required(request.orderNumber(), "Укажите номер приказа");
        if (request.orderDate() == null) throw new IllegalArgumentException("Укажите дату приказа");
        String signerName = required(request.signerName(), "Укажите ФИО подписанта");
        String signerPosition = required(request.signerPosition(), "Укажите должность подписанта");
        String code = normalizeSchoolCode(schoolCode);
        String schoolName = schoolName(code);

        List<AcademicLoadOrderDocumentService.CurriculumPlanRow> curriculumRows = type == AcademicLoadOrderType.CURRICULUM_APPROVAL
                ? curriculumRows(year) : List.of();
        List<AcademicLoadOrderDocumentService.LoadRow> loadRows = type == AcademicLoadOrderType.LOAD_APPROVAL
                ? loadRows(year) : List.of();
        int sourceItemCount = type == AcademicLoadOrderType.CURRICULUM_APPROVAL
                ? curriculumRows.size() : loadRows.size();
        if (sourceItemCount == 0) {
            throw new IllegalStateException(type == AcademicLoadOrderType.CURRICULUM_APPROVAL
                    ? "Нельзя сформировать приказ: учебные планы за " + year + " год не заполнены"
                    : "Нельзя сформировать приказ: учебная нагрузка за " + year + " год не распределена");
        }

        AcademicLoadOrderDocumentService.DocumentData data = new AcademicLoadOrderDocumentService.DocumentData(
                type,
                code,
                schoolName,
                year,
                number,
                request.orderDate(),
                trim(request.protocolNumber()),
                request.protocolDate(),
                request.effectiveDate(),
                signerName,
                signerPosition,
                trim(request.controlOfficerName()),
                trim(request.basisText()),
                curriculumRows,
                loadRows
        );
        byte[] content = documentService.generate(data);

        AcademicLoadOrder order = new AcademicLoadOrder();
        order.setAcademicYear(year);
        order.setType(type);
        order.setOrderNumber(number);
        order.setOrderDate(request.orderDate());
        order.setProtocolNumber(trim(request.protocolNumber()));
        order.setProtocolDate(request.protocolDate());
        order.setEffectiveDate(request.effectiveDate());
        order.setSignerName(signerName);
        order.setSignerPosition(signerPosition);
        order.setControlOfficerName(trim(request.controlOfficerName()));
        order.setSchoolCodeSnapshot(code);
        order.setSchoolNameSnapshot(schoolName);
        order.setSourceItemCount(sourceItemCount);
        order.setCreatedByUsername(required(user == null ? null : user.getUsername(), "Не определён пользователь"));
        order.setCreatedByFio(trim(user == null ? null : user.getFullName()));
        order.setCreatedAt(LocalDateTime.now());
        order.setDocumentFilename(filename(type, number, request.orderDate()));
        order.setDocumentContent(content);
        return view(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicLoadOrder document(Long id) {
        AcademicLoadOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Приказ не найден"));
        if (order.getDocumentContent() == null || order.getDocumentContent().length == 0) {
            throw new IllegalStateException("Word-файл приказа не сохранён");
        }
        return order;
    }

    private List<AcademicLoadOrderDocumentService.CurriculumPlanRow> curriculumRows(String year) {
        Map<CurriculumGroupKey, SortedSet<String>> classes = new TreeMap<>(Comparator
                .comparing(CurriculumGroupKey::building, naturalTextComparator())
                .thenComparing(CurriculumGroupKey::stage));
        for (CurriculumPlanEntry entry : activeCurriculum(year)) {
            CurriculumGroupKey key = new CurriculumGroupKey(
                    display(entry.getNumberSchoolBuilding(), "Не указан"),
                    stageLabel(entry.getStage()));
            classes.computeIfAbsent(key, ignored -> new TreeSet<>(naturalTextComparator()))
                    .add(display(entry.getClassName(), "—"));
        }
        return classes.entrySet().stream()
                .map(entry -> new AcademicLoadOrderDocumentService.CurriculumPlanRow(
                        entry.getKey().building(), entry.getKey().stage(), String.join(", ", entry.getValue())))
                .toList();
    }

    private List<AcademicLoadOrderDocumentService.LoadRow> loadRows(String year) {
        Map<LoadGroupKey, SortedSet<String>> classes = new TreeMap<>(Comparator
                .comparing(LoadGroupKey::teacher, naturalTextComparator())
                .thenComparing(LoadGroupKey::subject, naturalTextComparator())
                .thenComparing(LoadGroupKey::period, naturalTextComparator())
                .thenComparing(LoadGroupKey::hours, naturalTextComparator()));
        for (ManualLoadEntry entry : activeLoad(year)) {
            String teacher = display(entry.getFioTeacher(), "Не назначен");
            String subject = display(entry.getSubjectName(), "—");
            String period = periodLabel(entry);
            String hours = formatHours(entry.getEffectiveLoadHours());
            String className = display(entry.getClassName(), display(entry.getGroupNameEducationalPlan(), "—"));
            classes.computeIfAbsent(new LoadGroupKey(teacher, subject, hours, period), ignored -> new TreeSet<>(naturalTextComparator()))
                    .add(className);
        }
        return classes.entrySet().stream()
                .map(entry -> new AcademicLoadOrderDocumentService.LoadRow(
                        entry.getKey().teacher(),
                        entry.getKey().subject(),
                        String.join(", ", entry.getValue()),
                        entry.getKey().hours(),
                        entry.getKey().period()))
                .toList();
    }

    private List<CurriculumPlanEntry> activeCurriculum(String year) {
        return curriculumRepository.findAllByAcademicYear(year).stream()
                .filter(entry -> !entry.isDeprecated())
                .toList();
    }

    private List<ManualLoadEntry> activeLoad(String year) {
        return loadRepository.findAllByAcademicYear(year).stream()
                .filter(entry -> !entry.isOrphaned())
                .filter(entry -> entry.getEffectiveLoadHours().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private AcademicLoadOrderDtos.OrderView view(AcademicLoadOrder order) {
        String author = trim(order.getCreatedByFio());
        if (author.isBlank()) author = trim(order.getCreatedByUsername());
        return new AcademicLoadOrderDtos.OrderView(
                order.getId(),
                order.getAcademicYear(),
                order.getType(),
                order.getType().getDisplayName(),
                order.getOrderNumber(),
                order.getOrderDate(),
                order.getSchoolCodeSnapshot(),
                Optional.ofNullable(order.getSourceItemCount()).orElse(0),
                author,
                order.getCreatedAt(),
                order.getDocumentFilename());
    }

    private String stageLabel(CurriculumStage stage) {
        if (stage == CurriculumStage.NOO) return "Начальное общее образование";
        if (stage == CurriculumStage.SOO) return "Среднее общее образование";
        return "Основное общее образование";
    }

    private String periodLabel(ManualLoadEntry entry) {
        if (entry.getLoadFromDate() != null || entry.getLoadToDate() != null) {
            java.time.format.DateTimeFormatter date = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
            String from = entry.getLoadFromDate() == null ? "начала года" : date.format(entry.getLoadFromDate());
            String to = entry.getLoadToDate() == null ? "конца года" : date.format(entry.getLoadToDate());
            return from + " — " + to;
        }
        if (entry.getStudyPeriod() == StudyPeriod.H1) return "1 полугодие";
        if (entry.getStudyPeriod() == StudyPeriod.H2) return "2 полугодие";
        return "Учебный год";
    }

    private String formatHours(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO).stripTrailingZeros().toPlainString();
    }

    private String normalizeSchoolCode(String value) {
        String normalized = trim(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) normalized = SchoolCodeResolver.resolve();
        return normalized.isBlank() ? "demo" : normalized;
    }

    private String schoolName(String code) {
        return "demo".equalsIgnoreCase(code) ? "ГБОУ Школа" : "ГБОУ Школа № " + code;
    }

    private String filename(AcademicLoadOrderType type, String number, java.time.LocalDate date) {
        String prefix = type == AcademicLoadOrderType.CURRICULUM_APPROVAL
                ? "Приказ_об_утверждении_учебных_планов_" : "Приказ_об_утверждении_нагрузки_";
        String safeNumber = number.replaceAll("[^0-9A-Za-zА-Яа-яЁё._-]+", "_");
        return prefix + safeNumber + "_от_" + date + ".docx";
    }

    private String required(String value, String message) {
        String normalized = trim(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String display(String value, String fallback) {
        String normalized = trim(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String trim(String value) {
        return Optional.ofNullable(value).orElse("").trim();
    }

    private String normalized(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private Comparator<String> naturalTextComparator() {
        return Comparator.comparing((String value) -> value == null ? "" : value, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(value -> value == null ? "" : value);
    }

    private record CurriculumGroupKey(String building, String stage) {
    }

    private record LoadGroupKey(String teacher, String subject, String hours, String period) {
    }
}
