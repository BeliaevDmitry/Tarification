package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.ExitOrderDtos;
import org.school.personalLoad.dto.ProbeOrderDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.ExitOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExitOrderServiceImpl implements ExitOrderService {

    private static final int MAX_SCAN_BYTES = 15 * 1024 * 1024;
    private static final String DEFAULT_DEPUTY_NAME = "Власова Юлия Сергеевна";
    private static final String DEFAULT_DEPUTY_DATIVE = "Власовой Юлии Сергеевне";
    private static final String DEFAULT_DEPUTY_ACCUSATIVE = "Власову Юлию Сергеевну";
    private static final String DEFAULT_DEPUTY_INITIALS = "Власова Ю.С.";

    private final ExitOrderRepository orderRepository;
    private final ExitOrderApprovalRepository approvalRepository;
    private final ExitOrderSettingsRepository settingsRepository;
    private final ExitOrderDictionaryOptionRepository dictionaryRepository;
    private final ExitOrderGeneratedDocumentRepository documentRepository;
    private final ExitOrderScanRepository scanRepository;
    private final ClassroomLeadershipRepository classroomRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository studentRepository;
    private final SchoolBuildingRepository buildingRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final AppUserRepository appUserRepository;
    private final ProbeOrderDocumentService documentService;

    @Override
    @Transactional
    public ExitOrderDtos.ReferenceData references(String academicYear, SessionUser user) {
        ensureView(user);
        ensureDictionaryDefaults(academicYear);
        Long currentTeacherId = teacherId(user);

        Map<Long, LinkedHashMap<Long, StudentProfile>> studentsByClass = new LinkedHashMap<>();
        for (StudentClassEnrollment enrollment : enrollmentRepository.findAllByAcademicYear(academicYear)) {
            if (enrollment.getStatus() != StudentEnrollmentStatus.ACTIVE || enrollment.getClassRef() == null
                    || enrollment.getStudent() == null || !enrollment.getStudent().isActive()) {
                continue;
            }
            studentsByClass.computeIfAbsent(enrollment.getClassRef().getId(), ignored -> new LinkedHashMap<>())
                    .put(enrollment.getStudent().getId(), enrollment.getStudent());
        }

        List<ClassroomLeadershipEntry> directory = classroomRepository.findAllByAcademicYear(academicYear).stream()
                .filter(entry -> entry.getSchoolBuilding() != null)
                .sorted(Comparator.comparing(ClassroomLeadershipEntry::getClassName, classComparator()))
                .toList();
        List<ExitOrderDtos.ClassOption> classes = directory.stream().map(entry -> {
            List<ExitOrderDtos.StudentOption> students = studentsByClass
                    .getOrDefault(entry.getId(), new LinkedHashMap<>()).values().stream()
                    .sorted(Comparator.comparing(StudentProfile::getCurrentFullName, String.CASE_INSENSITIVE_ORDER))
                    .map(student -> new ExitOrderDtos.StudentOption(student.getId(), student.getCurrentFullName(),
                            entry.getClassName())).toList();
            SchoolBuilding building = entry.getSchoolBuilding();
            boolean suggested = currentTeacherId != null && Objects.equals(currentTeacherId, entry.getTeacherId());
            return new ExitOrderDtos.ClassOption(entry.getId(), entry.getClassName(),
                    ClassNameNormalizer.extractParallel(entry.getClassName()), building.getId(),
                    entry.getNumberSchoolBuilding(), building.getName(), building.getAddress(), suggested, students);
        }).toList();

        List<ExitOrderDtos.StaffOption> staff = activeStaff().stream().map(this::staffOption).toList();
        List<Long> suggestedClassIds = classes.stream().filter(ExitOrderDtos.ClassOption::suggested)
                .map(ExitOrderDtos.ClassOption::id).toList();
        String suggestedGatheringPlace = classes.stream().filter(ExitOrderDtos.ClassOption::suggested)
                .map(ExitOrderDtos.ClassOption::buildingAddress).filter(value -> !text(value).isBlank())
                .findFirst().orElse("");
        Map<ExitOrderDictionaryType, List<String>> dictionaries = dictionaryValues();
        dictionaries = new EnumMap<>(dictionaries);
        List<String> gatheringPlaces = new ArrayList<>(dictionaries.getOrDefault(
                ExitOrderDictionaryType.GATHERING_PLACE, List.of()));
        directory.stream().map(ClassroomLeadershipEntry::getCampusAddress).map(this::text)
                .filter(value -> !value.isBlank()).distinct().forEach(gatheringPlaces::add);
        dictionaries.put(ExitOrderDictionaryType.GATHERING_PLACE,
                gatheringPlaces.stream().distinct().toList());

        return new ExitOrderDtos.ReferenceData(classes, staff, staff, dictionaries, suggestedClassIds,
                currentTeacherId, currentTeacherId, suggestedGatheringPlace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExitOrderDtos.OrderView> list(String academicYear, SessionUser user) {
        ensureView(user);
        List<ExitOrder> orders = orderRepository.findAllByAcademicYearOrderByEventDateAscStartTimeAsc(academicYear);
        Map<Long, Boolean> documents = documentAvailability(orders);
        Map<Long, Boolean> scans = scanAvailability(orders);
        Map<Long, List<ExitOrderApproval>> approvals = approvals(orders);
        ProbeOrderApprovalMode mode = currentApprovalMode();
        return orders.stream()
                .sorted(orderComparator())
                .map(order -> toView(order, user, documents.containsKey(order.getId()),
                        scans.containsKey(order.getId()), mode, approvals.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public ExitOrderDtos.OrderView create(String academicYear,
                                           ExitOrderDtos.CreateRequest request,
                                           SessionUser user) {
        ensureEdit(user);
        ExitOrder order = new ExitOrder();
        order.setAcademicYear(academicYear);
        order.setRequestedByUserId(user.getId());
        order.setRequestedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
        order.setRequestedAt(LocalDateTime.now());
        applyRequest(order, request, true);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        ExitOrder saved = orderRepository.save(order);
        return toView(saved, user, false, false);
    }

    @Override
    @Transactional
    public ExitOrderDtos.OrderView update(Long id, ExitOrderDtos.CreateRequest request, SessionUser user) {
        ExitOrder order = requireOrder(id);
        ensureCanEditOrder(user, order);
        resetWorkflow(order);
        applyRequest(order, request, false);
        order.setUpdatedAt(LocalDateTime.now());
        return toView(orderRepository.save(order), user, false, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional
    public ExitOrderDtos.OrderView acknowledge(Long id, SessionUser user) {
        ExitOrder order = requireOrder(id);
        if (order.getStatus() != ProbeOrderStatus.DRAFT) {
            throw new IllegalStateException("Заявка уже согласована или выпущена");
        }
        ProbeOrderApprovalMode mode = currentApprovalMode();
        List<ExitOrderApproval> existing = approvalRepository.findAllByOrder_Id(id);
        List<ApprovalTarget> pending = pendingTargets(user, order, mode, existing);
        if (pending.isEmpty()) {
            throw new AuthExceptions.ForbiddenException("Согласовать заявку может руководитель соответствующего корпуса");
        }
        validateReady(order);
        LocalDateTime now = LocalDateTime.now();
        for (ApprovalTarget target : pending) {
            ExitOrderApproval approval = new ExitOrderApproval();
            approval.setOrder(order);
            approval.setScopeType(target.scopeType());
            approval.setScopeCode(target.scopeCode());
            approval.setScopeLabel(target.scopeLabel());
            approval.setApprovedAt(now);
            approval.setApprovedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
            approvalRepository.save(approval);
        }
        List<ExitOrderApproval> saved = approvalRepository.findAllByOrder_Id(id);
        refreshApprovalSummary(order, mode, saved);
        order.setUpdatedAt(now);
        return toView(orderRepository.save(order), user, false,
                scanRepository.findByOrder_Id(id).isPresent(), mode, saved);
    }

    @Override
    @Transactional
    public ExitOrderDtos.OrderView generate(Long id,
                                             ExitOrderDtos.GenerateRequest request,
                                             SessionUser user) {
        ensureLeadershipEdit(user);
        ExitOrder order = requireOrder(id);
        if (!approvalState(order, currentApprovalMode(), approvalRepository.findAllByOrder_Id(id)).complete()) {
            throw new IllegalStateException("Заявка ещё не согласована руководителем корпуса");
        }
        if (order.getStatus() == ProbeOrderStatus.RELEASED) {
            throw new IllegalStateException("Выпущенный приказ нельзя сформировать заново");
        }
        if (request == null || request.orderDate() == null) {
            throw new IllegalArgumentException("Укажите дату приказа");
        }
        TeacherDirectoryEntry signer = staff(request.signerTeacherId(), "Выберите подписанта приказа");
        order.setOrderNumber(requireText(request.orderNumber(), "Укажите номер приказа"));
        order.setOrderDate(request.orderDate());
        order.setSigner(signer);
        order.setSignerPosition(firstNotBlank(request.signerPosition(), signer.getPrimaryPosition(), "Директор"));

        ExitOrderGeneratedDocument document = documentRepository.findByOrder_Id(id)
                .orElseGet(ExitOrderGeneratedDocument::new);
        document.setOrder(order);
        document.setFileName(documentFilename(order));
        document.setContent(documentService.generate(documentData(order, user)));
        document.setGeneratedAt(LocalDateTime.now());
        document.setGeneratedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
        documentRepository.save(document);
        order.setStatus(ProbeOrderStatus.GENERATED);
        order.setUpdatedAt(LocalDateTime.now());
        return toView(orderRepository.save(order), user, true, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional(readOnly = true)
    public ProbeOrderDtos.FilePayload generatedDocument(Long id, SessionUser user) {
        ensureExport(user);
        requireOrder(id);
        ExitOrderGeneratedDocument document = documentRepository.findByOrder_Id(id)
                .orElseThrow(() -> new IllegalStateException("Word-приказ ещё не сформирован"));
        return new ProbeOrderDtos.FilePayload(document.getFileName(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", document.getContent());
    }

    @Override
    @Transactional
    public ExitOrderDtos.OrderView release(Long id, SessionUser user) {
        ensureLeadershipEdit(user);
        ExitOrder order = requireOrder(id);
        if (order.getStatus() != ProbeOrderStatus.GENERATED || documentRepository.findByOrder_Id(id).isEmpty()) {
            throw new IllegalStateException("Сначала сформируйте Word-приказ");
        }
        if (!approvalState(order, currentApprovalMode(), approvalRepository.findAllByOrder_Id(id)).complete()) {
            throw new IllegalStateException("Заявка ещё не согласована руководителем корпуса");
        }
        order.setStatus(ProbeOrderStatus.RELEASED);
        order.setReleasedAt(LocalDateTime.now());
        order.setReleasedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
        order.setUpdatedAt(LocalDateTime.now());
        return toView(orderRepository.save(order), user, true, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional
    public ExitOrderDtos.OrderView uploadScan(Long id, MultipartFile file, SessionUser user) throws IOException {
        ExitOrder order = requireOrder(id);
        ensureCanManageReleased(user, order);
        if (order.getStatus() != ProbeOrderStatus.RELEASED) {
            throw new IllegalStateException("Скан можно прикрепить после выпуска приказа");
        }
        validateScan(file);
        ExitOrderScan scan = scanRepository.findByOrder_Id(id).orElseGet(ExitOrderScan::new);
        scan.setOrder(order);
        scan.setFileName(safeFilename(file.getOriginalFilename(), "scan.pdf"));
        scan.setContentType(firstNotBlank(file.getContentType(), "application/octet-stream"));
        scan.setFileSize(file.getSize());
        scan.setContent(file.getBytes());
        scan.setUploadedAt(LocalDateTime.now());
        scan.setUploadedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
        scanRepository.save(scan);
        return toView(order, user, true, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ProbeOrderDtos.FilePayload signedScan(Long id, SessionUser user) {
        ensureExport(user);
        requireOrder(id);
        ExitOrderScan scan = scanRepository.findByOrder_Id(id)
                .orElseThrow(() -> new IllegalStateException("Скан подписанного приказа не загружен"));
        return new ProbeOrderDtos.FilePayload(scan.getFileName(), scan.getContentType(), scan.getContent());
    }

    @Override
    @Transactional
    public ExitOrderDtos.OrderView markAttendance(Long id,
                                                   ExitOrderDtos.AttendanceRequest request,
                                                   SessionUser user) {
        ExitOrder order = requireOrder(id);
        ensureCanManageReleased(user, order);
        if (order.getStatus() != ProbeOrderStatus.RELEASED || order.getEventDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("Неявку можно отметить после состоявшегося мероприятия по выпущенному приказу");
        }
        Set<Long> absentIds = request == null || request.absentParticipantIds() == null
                ? Set.of() : request.absentParticipantIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> participantIds = order.getParticipants().stream().map(ExitOrderParticipant::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!participantIds.containsAll(absentIds)) {
            throw new IllegalArgumentException("В списке неявившихся есть ребёнок из другого приказа");
        }
        order.getParticipants().forEach(participant -> participant.setAbsent(absentIds.contains(participant.getId())));
        order.setAttendanceMarkedAt(LocalDateTime.now());
        order.setAttendanceMarkedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
        order.setUpdatedAt(LocalDateTime.now());
        return toView(orderRepository.save(order), user, true, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional
    public ExitOrderDtos.SettingsView settings(String academicYear, SessionUser user) {
        ensureView(user);
        ensureDictionaryDefaults(academicYear);
        ExitOrderSettings settings = settingsRepository.findById(ExitOrderSettings.DEFAULT_ID).orElse(null);
        ProbeOrderApprovalMode mode = settings == null ? ProbeOrderApprovalMode.ORGANIZATIONAL_BUILDING
                : settings.getApprovalMode();
        TeacherDirectoryEntry deputy = effectiveDeputy(settings, activeStaff());
        return settingsView(mode, deputy, dictionaryValues(), canEditSettings(user));
    }

    @Override
    @Transactional
    public ExitOrderDtos.SettingsView updateSettings(String academicYear,
                                                      ExitOrderDtos.SettingsRequest request,
                                                      SessionUser user) {
        ensureLeadershipEdit(user);
        if (request == null || request.approvalMode() == null) {
            throw new IllegalArgumentException("Выберите порядок согласования заявок");
        }
        ExitOrderSettings settings = settingsRepository.findById(ExitOrderSettings.DEFAULT_ID)
                .orElseGet(ExitOrderSettings::new);
        TeacherDirectoryEntry deputy = request.deputyDirectorTeacherId() == null ? null
                : staff(request.deputyDirectorTeacherId(), "Заместитель директора не найден");
        settings.setApprovalMode(request.approvalMode());
        settings.setDeputyDirectorTeacherId(deputy == null ? null : deputy.getId());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
        settingsRepository.save(settings);

        Map<ExitOrderDictionaryType, List<String>> requested = request.dictionaries() == null
                ? Map.of() : request.dictionaries();
        for (ExitOrderDictionaryType type : ExitOrderDictionaryType.values()) {
            replaceDictionary(type, requested.getOrDefault(type, List.of()));
        }
        ensureDictionaryDefaults(academicYear);
        refreshPendingApprovalSummaries(request.approvalMode());
        return settingsView(settings.getApprovalMode(), effectiveDeputy(settings, activeStaff()),
                dictionaryValues(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public ExitOrderDtos.SummaryView summary(String academicYear, SessionUser user) {
        ensureView(user);
        List<ExitOrder> orders = orderRepository.findAllByAcademicYearOrderByEventDateAscStartTimeAsc(academicYear).stream()
                .filter(order -> order.getStatus() == ProbeOrderStatus.RELEASED)
                .filter(order -> !order.getEventDate().isAfter(LocalDate.now()))
                .toList();
        Map<String, MutableClassSummary> classes = new LinkedHashMap<>();
        for (ExitOrder order : orders) {
            Set<String> eventClasses = new HashSet<>();
            for (ExitOrderParticipant participant : order.getParticipants()) {
                String key = classKey(participant.getClassNameSnapshot()) + "|" + scopeCode(participant.getOrganizationalBuildingCode());
                MutableClassSummary row = classes.computeIfAbsent(key, ignored -> new MutableClassSummary(
                        participant.getClassNameSnapshot(), participant.getOrganizationalBuildingCode()));
                if (eventClasses.add(key)) row.events++;
                if (participant.isAbsent()) row.absent++; else row.attended++;
            }
        }

        Map<Long, MutableTeacherSummary> teachers = new LinkedHashMap<>();
        for (ExitOrder order : orders) {
            long attended = order.getParticipants().stream().filter(participant -> !participant.isAbsent()).count();
            for (TeacherDirectoryEntry teacher : companions(order)) {
                MutableTeacherSummary row = teachers.computeIfAbsent(teacher.getId(), ignored ->
                        new MutableTeacherSummary(teacher.getId(), teacher.getFioTeacher(), teacher.getNumberSchoolBuilding()));
                row.events++;
                row.children += attended;
            }
        }
        List<ExitOrderDtos.ClassSummary> classRows = classes.values().stream()
                .map(row -> new ExitOrderDtos.ClassSummary(row.className, row.buildingCode,
                        row.events, row.attended, row.absent))
                .sorted(Comparator.comparingLong(ExitOrderDtos.ClassSummary::attended).reversed()
                        .thenComparing(ExitOrderDtos.ClassSummary::className, classComparator())).toList();
        List<ExitOrderDtos.TeacherSummary> teacherRows = teachers.values().stream()
                .map(row -> new ExitOrderDtos.TeacherSummary(row.id, row.fullName, row.buildingCode,
                        row.events, row.children))
                .sorted(Comparator.comparingLong(ExitOrderDtos.TeacherSummary::events).reversed()
                        .thenComparing(ExitOrderDtos.TeacherSummary::fullName, String.CASE_INSENSITIVE_ORDER)).toList();
        long attended = orders.stream().flatMap(order -> order.getParticipants().stream())
                .filter(participant -> !participant.isAbsent()).count();
        long absent = orders.stream().flatMap(order -> order.getParticipants().stream())
                .filter(ExitOrderParticipant::isAbsent).count();
        return new ExitOrderDtos.SummaryView(classRows, teacherRows, orders.size(), attended, absent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbeOrderDtos.CalendarEvent> calendar(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from == null ? LocalDate.now().minusMonths(1) : from;
        LocalDate effectiveTo = to == null ? LocalDate.now().plusMonths(2) : to;
        if (effectiveTo.isBefore(effectiveFrom) || ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) > 370) {
            throw new IllegalArgumentException("Период календаря должен быть не больше одного года");
        }
        return orderRepository.findAllByStatusInAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        List.of(ProbeOrderStatus.BUILDING_APPROVED, ProbeOrderStatus.GENERATED,
                                ProbeOrderStatus.RELEASED), effectiveFrom, effectiveTo).stream()
                .map(this::calendarEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbeOrderDtos.HistoryEvent> studentHistory(Long studentId) {
        if (studentId == null) return List.of();
        return orderRepository.findAttendedByStudentId(studentId, LocalDate.now()).stream()
                .map(this::historyEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbeOrderDtos.HistoryEvent> teacherHistory(Long teacherId) {
        if (teacherId == null) return List.of();
        return orderRepository.findReleasedByCompanionId(teacherId, LocalDate.now()).stream()
                .map(this::historyEvent).toList();
    }

    private void applyRequest(ExitOrder order, ExitOrderDtos.CreateRequest request, boolean creating) {
        if (request == null) throw new IllegalArgumentException("Данные заявки не переданы");
        if (request.eventDate() == null) throw new IllegalArgumentException("Укажите дату мероприятия");
        validateTimes(request.startTime(), request.endTime(), request.gatheringTime(), request.returnTime());
        order.setPreamble(effectivePreamble(order.getAcademicYear(), request.preamble()));
        order.setEventName(requireText(request.eventName(), "Укажите название мероприятия"));
        order.setEventDate(request.eventDate());
        order.setStartTime(request.startTime());
        order.setEndTime(request.endTime());
        order.setVenue(requireText(request.venue(), "Укажите место проведения"));
        order.setEventAddress(requireText(request.eventAddress(), "Укажите адрес мероприятия"));
        order.setGatheringTime(request.gatheringTime());
        order.setReturnTime(request.returnTime());
        replaceParticipants(order, request.studentIds());
        SchoolBuilding majority = majorityBuilding(order.getParticipants());
        order.setSchoolBuilding(majority);
        order.setGatheringPlace(firstNotBlank(request.gatheringPlace(), majority.getAddress()));
        assignCompanions(order, request.primaryCompanionTeacherId(), request.secondaryCompanionTeacherId(),
                request.additionalCompanionTeacherIds());
        validateReady(order);
        if (creating) order.setStatus(ProbeOrderStatus.DRAFT);
    }

    private void replaceParticipants(ExitOrder order, List<Long> studentIds) {
        LinkedHashSet<Long> uniqueIds = Optional.ofNullable(studentIds).orElseGet(List::of).stream()
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) throw new IllegalArgumentException("Выберите хотя бы одного ребёнка");
        Map<Long, StudentProfile> students = studentRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity()));
        Map<Long, StudentClassEnrollment> enrollments = enrollmentRepository.findAllByAcademicYear(order.getAcademicYear())
                .stream().filter(item -> item.getStudent() != null && uniqueIds.contains(item.getStudent().getId()))
                .filter(item -> item.getStatus() == StudentEnrollmentStatus.ACTIVE && item.getClassRef() != null)
                .collect(Collectors.toMap(item -> item.getStudent().getId(), Function.identity(), (left, right) ->
                        right.getUpdatedAt().isAfter(left.getUpdatedAt()) ? right : left));
        if (students.size() != uniqueIds.size() || enrollments.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("Не для всех выбранных детей найден действующий класс в этом учебном году");
        }
        order.getParticipants().clear();
        for (Long studentId : uniqueIds) {
            StudentProfile student = students.get(studentId);
            StudentClassEnrollment enrollment = enrollments.get(studentId);
            ClassroomLeadershipEntry classroom = enrollment.getClassRef();
            if (classroom.getSchoolBuilding() == null) {
                throw new IllegalArgumentException("Для класса " + enrollment.getClassName() + " не указан корпус");
            }
            ExitOrderParticipant participant = new ExitOrderParticipant();
            participant.setOrder(order);
            participant.setStudent(student);
            participant.setFullNameSnapshot(student.getCurrentFullName());
            participant.setClassNameSnapshot(enrollment.getClassName());
            participant.setOrganizationalBuildingCode(classroom.getNumberSchoolBuilding());
            participant.setSchoolBuildingId(classroom.getSchoolBuilding().getId());
            participant.setAbsent(false);
            order.getParticipants().add(participant);
        }
    }

    private SchoolBuilding majorityBuilding(List<ExitOrderParticipant> participants) {
        Map<Long, Long> counts = participants.stream().collect(Collectors.groupingBy(
                ExitOrderParticipant::getSchoolBuildingId, LinkedHashMap::new, Collectors.counting()));
        Long buildingId = counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Не удалось определить корпус сбора"));
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Корпус сбора не найден"));
    }

    private void assignCompanions(ExitOrder order, Long primaryId, Long secondaryId, List<Long> additionalIds) {
        TeacherDirectoryEntry primary = staff(primaryId, "Выберите основного сопровождающего");
        TeacherDirectoryEntry secondary = secondaryId == null ? null
                : staff(secondaryId, "Второй сопровождающий не найден");
        if (requiredCompanions(order.getParticipants().size()) > 1 && secondary == null) {
            throw new IllegalArgumentException("Для группы больше 10 детей выберите второго сопровождающего");
        }
        LinkedHashSet<Long> used = new LinkedHashSet<>();
        used.add(primary.getId());
        if (secondary != null && !used.add(secondary.getId())) {
            throw new IllegalArgumentException("Один сотрудник не может быть выбран дважды");
        }
        LinkedHashSet<TeacherDirectoryEntry> additional = new LinkedHashSet<>();
        for (Long id : Optional.ofNullable(additionalIds).orElseGet(List::of)) {
            if (id == null) continue;
            if (!used.add(id)) throw new IllegalArgumentException("Один сотрудник не может быть выбран дважды");
            additional.add(staff(id, "Дополнительный сопровождающий не найден"));
        }
        order.setPrimaryCompanion(primary);
        order.setSecondaryCompanion(secondary);
        order.getAdditionalCompanions().clear();
        order.getAdditionalCompanions().addAll(additional);
    }

    private void resetWorkflow(ExitOrder order) {
        if (order.getId() != null) {
            approvalRepository.deleteAllByOrder_Id(order.getId());
            documentRepository.deleteByOrder_Id(order.getId());
        }
        order.setStatus(ProbeOrderStatus.DRAFT);
        order.setBuildingApprovedAt(null);
        order.setBuildingApprovedBy(null);
        order.setOrderNumber(null);
        order.setOrderDate(null);
        order.setSigner(null);
        order.setSignerPosition(null);
        order.setReleasedAt(null);
        order.setReleasedBy(null);
        order.setAttendanceMarkedAt(null);
        order.setAttendanceMarkedBy(null);
    }

    private ExitOrderDtos.OrderView toView(ExitOrder order, SessionUser user, boolean hasDocument, boolean hasScan) {
        return toView(order, user, hasDocument, hasScan, currentApprovalMode(),
                approvalRepository.findAllByOrder_Id(order.getId()));
    }

    private ExitOrderDtos.OrderView toView(ExitOrder order,
                                            SessionUser user,
                                            boolean hasDocument,
                                            boolean hasScan,
                                            ProbeOrderApprovalMode mode,
                                            List<ExitOrderApproval> savedApprovals) {
        ApprovalState approval = approvalState(order, mode, savedApprovals);
        boolean leadership = isLeadership(user);
        boolean requester = user != null && Objects.equals(user.getId(), order.getRequestedByUserId());
        boolean editable = order.getStatus() == ProbeOrderStatus.DRAFT && (leadership || requester)
                && user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS);
        boolean released = order.getStatus() == ProbeOrderStatus.RELEASED;
        List<ExitOrderDtos.ParticipantView> participants = order.getParticipants().stream().map(item ->
                new ExitOrderDtos.ParticipantView(item.getId(), item.getStudent().getId(), item.getFullNameSnapshot(),
                        item.getClassNameSnapshot(), item.getOrganizationalBuildingCode(), item.isAbsent())).toList();
        return new ExitOrderDtos.OrderView(order.getId(), order.getAcademicYear(), order.getPreamble(),
                order.getEventName(), order.getEventDate(), order.getStartTime(), order.getEndTime(), order.getVenue(),
                order.getEventAddress(), order.getGatheringTime(), order.getGatheringPlace(), order.getReturnTime(),
                order.getSchoolBuilding().getId(), order.getSchoolBuilding().getCode(), order.getSchoolBuilding().getName(),
                classNames(order), participants.size(), (int) participants.stream().filter(ExitOrderDtos.ParticipantView::absent).count(),
                requiredCompanions(participants.size()), staffOption(order.getPrimaryCompanion()),
                staffOption(order.getSecondaryCompanion()), additionalCompanions(order).stream().map(this::staffOption).toList(),
                order.getStatus(), mode, approval.views(), approval.complete(), order.getRequestedBy(), order.getRequestedAt(),
                order.getOrderNumber(), order.getOrderDate(), staffOption(order.getSigner()), order.getSignerPosition(),
                hasDocument && user.canExportTab(AppTab.DOCUMENTS_EXIT_ORDERS),
                hasScan && user.canExportTab(AppTab.DOCUMENTS_EXIT_ORDERS), order.getAttendanceMarkedAt(), participants,
                editable, canAcknowledge(user, order, mode, savedApprovals),
                leadership && user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS) && approval.complete() && !released,
                leadership && user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS) && order.getStatus() == ProbeOrderStatus.GENERATED,
                released && (leadership || requester) && user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS),
                released && !order.getEventDate().isAfter(LocalDate.now()) && (leadership || requester)
                        && user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS));
    }

    private List<ApprovalTarget> approvalTargets(ExitOrder order, ProbeOrderApprovalMode mode) {
        LinkedHashMap<String, ApprovalTarget> targets = new LinkedHashMap<>();
        if (mode == ProbeOrderApprovalMode.ORGANIZATIONAL_BUILDING || mode == ProbeOrderApprovalMode.BOTH) {
            order.getParticipants().stream().map(ExitOrderParticipant::getOrganizationalBuildingCode)
                    .map(this::scopeCode).filter(value -> !value.isBlank()).distinct().sorted()
                    .forEach(code -> addTarget(targets, new ApprovalTarget(ProbeOrderApprovalScope.ORGANIZATIONAL_BUILDING,
                            code, "Корпус " + code)));
        }
        if (mode == ProbeOrderApprovalMode.PHYSICAL_SITE || mode == ProbeOrderApprovalMode.BOTH) {
            SchoolBuilding building = order.getSchoolBuilding();
            String code = scopeCode(building.getBuildingGroup() == null
                    ? building.getCode() : building.getBuildingGroup().getCode());
            String label = "Площадка " + code + (text(building.getAddress()).isBlank() ? "" : " — " + building.getAddress());
            addTarget(targets, new ApprovalTarget(ProbeOrderApprovalScope.PHYSICAL_SITE, code, label));
        }
        return List.copyOf(targets.values());
    }

    private void addTarget(Map<String, ApprovalTarget> targets, ApprovalTarget target) {
        targets.putIfAbsent(approvalKey(target.scopeType(), target.scopeCode()), target);
    }

    private ApprovalState approvalState(ExitOrder order,
                                        ProbeOrderApprovalMode mode,
                                        List<ExitOrderApproval> saved) {
        List<ApprovalTarget> targets = approvalTargets(order, mode);
        Map<String, ExitOrderApproval> byKey = Optional.ofNullable(saved).orElseGet(List::of).stream()
                .collect(Collectors.toMap(item -> approvalKey(item.getScopeType(), item.getScopeCode()),
                        Function.identity(), (left, right) -> left));
        List<ExitOrderDtos.ApprovalView> views = targets.stream().map(target -> {
            ExitOrderApproval approval = byKey.get(approvalKey(target.scopeType(), target.scopeCode()));
            return new ExitOrderDtos.ApprovalView(target.scopeType(), target.scopeCode(), target.scopeLabel(),
                    approval == null ? null : approval.getApprovedAt(), approval == null ? null : approval.getApprovedBy());
        }).toList();
        boolean anyScope = targets.stream().map(ApprovalTarget::scopeType).collect(Collectors.toSet()).size() > 1;
        boolean complete = !targets.isEmpty() && (anyScope
                ? views.stream().anyMatch(view -> view.approvedAt() != null)
                : views.stream().allMatch(view -> view.approvedAt() != null));
        return new ApprovalState(targets, views, complete);
    }

    private List<ApprovalTarget> pendingTargets(SessionUser user,
                                                ExitOrder order,
                                                ProbeOrderApprovalMode mode,
                                                List<ExitOrderApproval> saved) {
        Set<String> approved = Optional.ofNullable(saved).orElseGet(List::of).stream()
                .map(item -> approvalKey(item.getScopeType(), item.getScopeCode())).collect(Collectors.toSet());
        return approvalTargets(order, mode).stream().filter(target -> targetMatchesUser(user, target))
                .filter(target -> !approved.contains(approvalKey(target.scopeType(), target.scopeCode()))).toList();
    }

    private boolean canAcknowledge(SessionUser user,
                                   ExitOrder order,
                                   ProbeOrderApprovalMode mode,
                                   List<ExitOrderApproval> approvals) {
        return user != null && user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS)
                && order.getStatus() == ProbeOrderStatus.DRAFT
                && !approvalState(order, mode, approvals).complete()
                && !pendingTargets(user, order, mode, approvals).isEmpty();
    }

    private boolean targetMatchesUser(SessionUser user, ApprovalTarget target) {
        if (isLeadership(user)) return true;
        return user != null && user.getRole() == UserRole.BUILDING_HEAD
                && scopeCode(user.getManagedBuildingCode()).equals(scopeCode(target.scopeCode()));
    }

    private void refreshApprovalSummary(ExitOrder order,
                                        ProbeOrderApprovalMode mode,
                                        List<ExitOrderApproval> approvals) {
        ApprovalState state = approvalState(order, mode, approvals);
        if (state.complete()) {
            order.setStatus(ProbeOrderStatus.BUILDING_APPROVED);
            order.setBuildingApprovedAt(state.views().stream().map(ExitOrderDtos.ApprovalView::approvedAt)
                    .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
            order.setBuildingApprovedBy(state.views().stream().map(ExitOrderDtos.ApprovalView::approvedBy)
                    .filter(Objects::nonNull).distinct().collect(Collectors.joining(", ")));
        }
    }

    private void refreshPendingApprovalSummaries(ProbeOrderApprovalMode mode) {
        for (ExitOrder order : orderRepository.findAll()) {
            if (order.getStatus() != ProbeOrderStatus.DRAFT && order.getStatus() != ProbeOrderStatus.BUILDING_APPROVED) continue;
            ApprovalState state = approvalState(order, mode, approvalRepository.findAllByOrder_Id(order.getId()));
            if (state.complete()) {
                refreshApprovalSummary(order, mode, approvalRepository.findAllByOrder_Id(order.getId()));
            } else {
                order.setStatus(ProbeOrderStatus.DRAFT);
                order.setBuildingApprovedAt(null);
                order.setBuildingApprovedBy(null);
            }
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
    }

    private Map<ExitOrderDictionaryType, List<String>> dictionaryValues() {
        EnumMap<ExitOrderDictionaryType, List<String>> result = new EnumMap<>(ExitOrderDictionaryType.class);
        for (ExitOrderDictionaryType type : ExitOrderDictionaryType.values()) result.put(type, new ArrayList<>());
        dictionaryRepository.findAllByOrderByTypeAscSortOrderAsc().forEach(option -> result.get(option.getType()).add(option.getValue()));
        return result;
    }

    private void ensureDictionaryDefaults(String academicYear) {
        Map<ExitOrderDictionaryType, List<String>> defaults = defaultDictionaries(academicYear);
        for (ExitOrderDictionaryType type : ExitOrderDictionaryType.values()) {
            if (dictionaryRepository.findAllByTypeOrderBySortOrderAsc(type).isEmpty()) {
                replaceDictionary(type, defaults.getOrDefault(type, List.of()));
            }
        }
    }

    private void replaceDictionary(ExitOrderDictionaryType type, List<String> values) {
        dictionaryRepository.deleteAllByType(type);
        LinkedHashSet<String> normalized = Optional.ofNullable(values).orElseGet(List::of).stream()
                .map(this::text).filter(value -> !value.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
        int order = 0;
        for (String value : normalized) {
            ExitOrderDictionaryOption option = new ExitOrderDictionaryOption();
            option.setType(type);
            option.setValue(value);
            option.setSortOrder(order++);
            dictionaryRepository.save(option);
        }
    }

    private Map<ExitOrderDictionaryType, List<String>> defaultDictionaries(String academicYear) {
        EnumMap<ExitOrderDictionaryType, List<String>> result = new EnumMap<>(ExitOrderDictionaryType.class);
        result.put(ExitOrderDictionaryType.PREAMBLE, List.of(defaultPreamble(academicYear)));
        result.put(ExitOrderDictionaryType.EVENT_NAME, List.of("Экскурсия", "Музейное занятие",
                "Театральное мероприятие", "Профориентационное мероприятие"));
        result.put(ExitOrderDictionaryType.VENUE, List.of("Музей", "Театр", "Выставочный зал",
                "Образовательная организация"));
        result.put(ExitOrderDictionaryType.EVENT_ADDRESS, List.of());
        result.put(ExitOrderDictionaryType.GATHERING_PLACE, List.of());
        return result;
    }

    private String effectivePreamble(String academicYear, String requested) {
        String value = firstNotBlank(requested, defaultPreamble(academicYear));
        List<String> allowed = dictionaryRepository.findAllByTypeOrderBySortOrderAsc(ExitOrderDictionaryType.PREAMBLE)
                .stream().map(ExitOrderDictionaryOption::getValue).toList();
        if (!allowed.isEmpty() && allowed.stream().noneMatch(value::equals)) {
            throw new IllegalArgumentException("Выберите преамбулу из справочника приказов");
        }
        return value;
    }

    private String defaultPreamble(String academicYear) {
        return "На основании Плана воспитательной работы ГБОУ Школа № 7 на "
                + firstNotBlank(academicYear, "2026/2027").replace('/', '–') + " учебный год";
    }

    private ExitOrderDtos.SettingsView settingsView(ProbeOrderApprovalMode mode,
                                                     TeacherDirectoryEntry deputy,
                                                     Map<ExitOrderDictionaryType, List<String>> dictionaries,
                                                     boolean canEdit) {
        return new ExitOrderDtos.SettingsView(mode, approvalModeLabel(mode), deputy == null ? null : deputy.getId(),
                deputy == null ? DEFAULT_DEPUTY_NAME : deputy.getFioTeacher(), dictionaries, canEdit);
    }

    private ProbeOrderApprovalMode currentApprovalMode() {
        return settingsRepository.findById(ExitOrderSettings.DEFAULT_ID).map(ExitOrderSettings::getApprovalMode)
                .orElse(ProbeOrderApprovalMode.ORGANIZATIONAL_BUILDING);
    }

    private String approvalModeLabel(ProbeOrderApprovalMode mode) {
        if (mode == ProbeOrderApprovalMode.PHYSICAL_SITE) return "Руководитель фактической площадки";
        if (mode == ProbeOrderApprovalMode.BOTH) return "Руководитель корпуса или руководитель площадки";
        return "Руководитель организационного корпуса";
    }

    private ProbeOrderDocumentService.DocumentData documentData(ExitOrder order, SessionUser user) {
        List<String> classes = classNames(order);
        DocumentPersonnel personnel = documentPersonnel(user);
        List<ProbeOrderDocumentService.ParticipantData> participants = order.getParticipants().stream().map(item -> {
            StudentProfile student = item.getStudent();
            return new ProbeOrderDocumentService.ParticipantData(item.getFullNameSnapshot(), student.getChildPhone(),
                    student.getRepresentativeName(), student.getRepresentativePhone());
        }).toList();
        return new ProbeOrderDocumentService.DocumentData(order.getAcademicYear(), order.getOrderNumber(),
                order.getOrderDate(), order.getEventDate(), order.getStartTime(),
                classes.stream().map(this::displayClass).collect(Collectors.joining(", ")),
                classes.size() == 1 ? "класса" : "классов", order.getVenue(), order.getEventAddress(),
                order.getGatheringTime(), order.getGatheringPlace(), order.getReturnTime(),
                order.getSchoolBuilding().getManagerFio(), person(order.getPrimaryCompanion()),
                person(order.getSecondaryCompanion()), additionalCompanions(order).stream().map(this::person).toList(),
                person(order.getSigner()), order.getSignerPosition(), person(personnel.director()), personnel.deputy(),
                personnel.executor(), participants, order.getPreamble(), "на мероприятие «" + order.getEventName() + "»");
    }

    private DocumentPersonnel documentPersonnel(SessionUser user) {
        List<TeacherDirectoryEntry> active = activeStaff();
        TeacherDirectoryEntry director = linkedRoleTeacher(UserRole.DIRECTOR, active);
        if (director == null) director = active.stream().filter(item -> position(item).equals("директор")).findFirst().orElse(null);
        ExitOrderSettings settings = settingsRepository.findById(ExitOrderSettings.DEFAULT_ID).orElse(null);
        TeacherDirectoryEntry deputyTeacher = effectiveDeputy(settings, active);
        ProbeOrderDocumentService.PersonData deputy = deputyTeacher == null ? defaultDeputy() : person(deputyTeacher);
        TeacherDirectoryEntry executorTeacher = teacherId(user) == null ? null
                : teacherRepository.findById(teacherId(user)).orElse(null);
        ProbeOrderDocumentService.PersonData executor = executorTeacher == null
                ? new ProbeOrderDocumentService.PersonData(null, user.getFullName(), user.getFullName(), user.getFullName(),
                initials(user.getFullName()), user.getPhone()) : person(executorTeacher);
        return new DocumentPersonnel(director, deputy, executor);
    }

    private TeacherDirectoryEntry effectiveDeputy(ExitOrderSettings settings, List<TeacherDirectoryEntry> active) {
        Long id = settings == null ? null : settings.getDeputyDirectorTeacherId();
        if (id != null) {
            TeacherDirectoryEntry configured = active.stream().filter(item -> Objects.equals(item.getId(), id)).findFirst().orElse(null);
            if (configured != null) return configured;
        }
        return active.stream().filter(item -> normalizeName(item.getFioTeacher()).startsWith("власова ")).findFirst().orElse(null);
    }

    private TeacherDirectoryEntry linkedRoleTeacher(UserRole role, List<TeacherDirectoryEntry> active) {
        Set<Long> ids = active.stream().map(TeacherDirectoryEntry::getId).collect(Collectors.toSet());
        return appUserRepository.findAll().stream()
                .filter(account -> account.isActive() && account.getRole() == role && account.getTeacherId() != null)
                .filter(account -> ids.contains(account.getTeacherId()))
                .map(account -> teacherRepository.findById(account.getTeacherId()).orElse(null))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private ProbeOrderDocumentService.PersonData defaultDeputy() {
        return new ProbeOrderDocumentService.PersonData(null, DEFAULT_DEPUTY_NAME, DEFAULT_DEPUTY_DATIVE,
                DEFAULT_DEPUTY_ACCUSATIVE, DEFAULT_DEPUTY_INITIALS, null);
    }

    private ProbeOrderDocumentService.PersonData person(TeacherDirectoryEntry teacher) {
        if (teacher == null) return null;
        return new ProbeOrderDocumentService.PersonData(teacher.getId(), teacher.getFioTeacher(),
                teacher.getFioTeacherDative(), teacher.getFioTeacherAccusative(),
                firstNotBlank(teacher.getInitials(), initials(teacher.getFioTeacher())), teacher.getPhone());
    }

    private ProbeOrderDtos.CalendarEvent calendarEvent(ExitOrder order) {
        List<ProbeOrderDtos.CalendarParticipant> participants = new ArrayList<>();
        participants.add(new ProbeOrderDtos.CalendarParticipant("BUILDING", order.getSchoolBuilding().getId(),
                order.getSchoolBuilding().getCode(), order.getSchoolBuilding().getName(), order.getSchoolBuilding().getAddress()));
        companions(order).forEach(teacher -> participants.add(new ProbeOrderDtos.CalendarParticipant("PERSON", teacher.getId(),
                text(teacher.getNumberSchoolBuilding()), teacher.getFioTeacher(), text(teacher.getPrimaryPosition()))));
        return new ProbeOrderDtos.CalendarEvent(order.getId(), order.getEventName(), order.getEventDate(),
                order.getStartTime(), order.getEndTime(), order.getSchoolBuilding().getCode(),
                order.getSchoolBuilding().getName(), classNames(order), companionNames(order), order.getVenue(),
                order.getEventAddress(), participants);
    }

    private ProbeOrderDtos.HistoryEvent historyEvent(ExitOrder order) {
        return new ProbeOrderDtos.HistoryEvent(order.getId(), order.getAcademicYear(), order.getEventName(),
                order.getEventDate(), order.getStartTime(), order.getEndTime(), order.getVenue(), order.getEventAddress(),
                order.getSchoolBuilding().getCode(), classNames(order), companionNames(order), order.getOrderNumber(),
                order.getOrderDate());
    }

    private ExitOrder requireOrder(Long id) {
        if (id == null) throw new IllegalArgumentException("Приказ не выбран");
        return orderRepository.findOneById(id).orElseThrow(() -> new NoSuchElementException("Приказ не найден"));
    }

    private TeacherDirectoryEntry staff(Long id, String message) {
        if (id == null) throw new IllegalArgumentException(message);
        TeacherDirectoryEntry teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(message));
        if (!isActiveStaff(teacher)) throw new IllegalArgumentException("Сотрудник уволен или находится в архиве");
        return teacher;
    }

    private List<TeacherDirectoryEntry> activeStaff() {
        return teacherRepository.findAll().stream().filter(this::isActiveStaff)
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private boolean isActiveStaff(TeacherDirectoryEntry teacher) {
        return teacher != null && !teacher.isArchived() && teacher.getDismissalDate() == null
                && !text(teacher.getFioTeacher()).toLowerCase(Locale.ROOT).startsWith("вакансия");
    }

    private Long teacherId(SessionUser user) {
        if (user == null) return null;
        Long id = appUserRepository.findById(user.getId()).map(account -> account.getTeacherId()).orElse(null);
        if (id != null) return id;
        return teacherRepository.findByFioTeacherIgnoreCase(user.getFullName()).map(TeacherDirectoryEntry::getId).orElse(null);
    }

    private ExitOrderDtos.StaffOption staffOption(TeacherDirectoryEntry teacher) {
        if (teacher == null) return null;
        return new ExitOrderDtos.StaffOption(teacher.getId(), teacher.getFioTeacher(), teacher.getPrimaryPosition(),
                teacher.getNumberSchoolBuilding(), teacher.getPhone());
    }

    private List<TeacherDirectoryEntry> additionalCompanions(ExitOrder order) {
        return order.getAdditionalCompanions() == null ? List.of() : order.getAdditionalCompanions().stream().toList();
    }

    private List<TeacherDirectoryEntry> companions(ExitOrder order) {
        LinkedHashMap<Long, TeacherDirectoryEntry> result = new LinkedHashMap<>();
        if (order.getPrimaryCompanion() != null) result.put(order.getPrimaryCompanion().getId(), order.getPrimaryCompanion());
        if (order.getSecondaryCompanion() != null) result.put(order.getSecondaryCompanion().getId(), order.getSecondaryCompanion());
        additionalCompanions(order).forEach(item -> result.put(item.getId(), item));
        return List.copyOf(result.values());
    }

    private List<String> companionNames(ExitOrder order) {
        return companions(order).stream().map(TeacherDirectoryEntry::getFioTeacher).toList();
    }

    private List<String> classNames(ExitOrder order) {
        return order.getParticipants().stream().map(ExitOrderParticipant::getClassNameSnapshot)
                .filter(Objects::nonNull).distinct().sorted(classComparator()).toList();
    }

    private Comparator<String> classComparator() {
        return Comparator.comparing((String value) -> Optional.ofNullable(ClassNameNormalizer.extractParallel(value)).orElse(99))
                .thenComparing(Function.identity(), String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<ExitOrder> orderComparator() {
        LocalDate today = LocalDate.now();
        return Comparator.comparingInt((ExitOrder order) -> order.getEventDate().isBefore(today) ? 1 : 0)
                .thenComparing(order -> order.getEventDate().isBefore(today)
                        ? -order.getEventDate().toEpochDay() : order.getEventDate().toEpochDay())
                .thenComparing(ExitOrder::getStartTime);
    }

    private int requiredCompanions(int children) {
        return children > 10 ? 2 : 1;
    }

    private void validateReady(ExitOrder order) {
        if (order.getParticipants().isEmpty()) throw new IllegalArgumentException("Выберите хотя бы одного ребёнка");
        if (order.getPrimaryCompanion() == null) throw new IllegalArgumentException("Выберите сопровождающего");
        if (requiredCompanions(order.getParticipants().size()) > 1 && order.getSecondaryCompanion() == null) {
            throw new IllegalArgumentException("Для группы больше 10 детей нужен второй сопровождающий");
        }
    }

    private void validateTimes(LocalTime start, LocalTime end, LocalTime gathering, LocalTime returning) {
        if (start == null || end == null || gathering == null || returning == null) {
            throw new IllegalArgumentException("Укажите время начала, окончания, сбора и возвращения");
        }
        if (!end.isAfter(start)) throw new IllegalArgumentException("Окончание должно быть позже начала");
        if (gathering.isAfter(start)) throw new IllegalArgumentException("Сбор не может быть позже начала мероприятия");
        if (returning.isBefore(end)) throw new IllegalArgumentException("Возвращение не может быть раньше окончания мероприятия");
    }

    private void validateScan(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Выберите файл скана");
        if (file.getSize() > MAX_SCAN_BYTES) throw new IllegalArgumentException("Скан должен быть не больше 15 МБ");
        String name = safeFilename(file.getOriginalFilename(), "scan").toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".pdf") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
            throw new IllegalArgumentException("Разрешены сканы PDF, JPG и PNG");
        }
    }

    private void ensureCanEditOrder(SessionUser user, ExitOrder order) {
        ensureEdit(user);
        if (order.getStatus() != ProbeOrderStatus.DRAFT
                || (!isLeadership(user) && !Objects.equals(user.getId(), order.getRequestedByUserId()))) {
            throw new AuthExceptions.ForbiddenException("Редактировать можно только свою несогласованную заявку");
        }
    }

    private void ensureCanManageReleased(SessionUser user, ExitOrder order) {
        ensureEdit(user);
        if (!isLeadership(user) && !Objects.equals(user.getId(), order.getRequestedByUserId())) {
            throw new AuthExceptions.ForbiddenException("Отмечать посещение и загружать скан может автор заявки или администрация");
        }
    }

    private void ensureView(SessionUser user) {
        if (user == null || !user.canViewTab(AppTab.DOCUMENTS_EXIT_ORDERS)) {
            throw new AuthExceptions.ForbiddenException("Нет права просматривать приказы на выход");
        }
    }

    private void ensureEdit(SessionUser user) {
        ensureView(user);
        if (!user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS)) {
            throw new AuthExceptions.ForbiddenException("Нет права создавать и изменять приказы на выход");
        }
    }

    private void ensureLeadershipEdit(SessionUser user) {
        ensureEdit(user);
        if (!isLeadership(user)) {
            throw new AuthExceptions.ForbiddenException("Формировать и выпускать приказ может только администрация");
        }
    }

    private void ensureExport(SessionUser user) {
        ensureView(user);
        if (!user.canExportTab(AppTab.DOCUMENTS_EXIT_ORDERS)) {
            throw new AuthExceptions.ForbiddenException("Нет права скачивать приказы на выход");
        }
    }

    private boolean canEditSettings(SessionUser user) {
        return isLeadership(user) && user.canEditTab(AppTab.DOCUMENTS_EXIT_ORDERS);
    }

    private boolean isLeadership(SessionUser user) {
        return user != null && (user.isAdmin() || user.getRole() == UserRole.DIRECTOR
                || user.getRole() == UserRole.DEPUTY_DIRECTOR);
    }

    private Map<Long, Boolean> documentAvailability(List<ExitOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        return documentRepository.findAllByOrder_IdIn(orders.stream().map(ExitOrder::getId).toList()).stream()
                .collect(Collectors.toMap(item -> item.getOrder().getId(), ignored -> true));
    }

    private Map<Long, Boolean> scanAvailability(List<ExitOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        return scanRepository.findAllByOrder_IdIn(orders.stream().map(ExitOrder::getId).toList()).stream()
                .collect(Collectors.toMap(item -> item.getOrder().getId(), ignored -> true));
    }

    private Map<Long, List<ExitOrderApproval>> approvals(List<ExitOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        return approvalRepository.findAllByOrder_IdIn(orders.stream().map(ExitOrder::getId).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
    }

    private String approvalKey(ProbeOrderApprovalScope type, String code) {
        return type + "|" + scopeCode(code);
    }

    private String scopeCode(String value) {
        String normalized = text(value).toUpperCase(Locale.ROOT).replace('–', '-').replace('—', '-')
                .replaceAll("[CС][ПPР]", "СП").replaceAll("\\s+", "");
        int details = normalized.indexOf("::");
        if (details >= 0) normalized = normalized.substring(0, details);
        int address = normalized.indexOf('|');
        if (address >= 0) normalized = normalized.substring(0, address);
        return normalized.replaceFirst("^СП-(\\d+)$", "СП$1");
    }

    private String classKey(String value) {
        return text(value).toUpperCase(Locale.ROOT).replace(" ", "").replace('-', '–');
    }

    private String displayClass(String value) {
        return text(value).replaceAll("^(\\d+)\\s*[-–—]?\\s*([А-ЯA-Z])$", "$1 «$2»");
    }

    private String documentFilename(ExitOrder order) {
        return safeFilename("Приказ на выход " + order.getEventDate() + " "
                + String.join("_", classNames(order)) + ".docx", "Приказ на выход.docx");
    }

    private String safeFilename(String value, String fallback) {
        String normalized = text(value).replaceAll("[\\\\/:*?\"<>|]", "_");
        return normalized.isBlank() ? fallback : normalized;
    }

    private String requireText(String value, String message) {
        String result = text(value);
        if (result.isBlank()) throw new IllegalArgumentException(message);
        return result;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) if (!text(value).isBlank()) return text(value);
        return "";
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeName(String value) {
        return text(value).toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ");
    }

    private String position(TeacherDirectoryEntry teacher) {
        return normalizeName(teacher == null ? null : teacher.getPrimaryPosition());
    }

    private String initials(String fullName) {
        String[] parts = text(fullName).split("\\s+");
        if (parts.length == 0) return "";
        StringBuilder value = new StringBuilder(parts[0]);
        for (int i = 1; i < Math.min(parts.length, 3); i++) {
            if (!parts[i].isBlank()) value.append(' ').append(parts[i].charAt(0)).append('.');
        }
        return value.toString();
    }

    private record ApprovalTarget(ProbeOrderApprovalScope scopeType, String scopeCode, String scopeLabel) {
    }

    private record ApprovalState(List<ApprovalTarget> targets, List<ExitOrderDtos.ApprovalView> views, boolean complete) {
    }

    private record DocumentPersonnel(TeacherDirectoryEntry director,
                                     ProbeOrderDocumentService.PersonData deputy,
                                     ProbeOrderDocumentService.PersonData executor) {
    }

    private static final class MutableClassSummary {
        private final String className;
        private final String buildingCode;
        private long events;
        private long attended;
        private long absent;

        private MutableClassSummary(String className, String buildingCode) {
            this.className = className;
            this.buildingCode = buildingCode;
        }
    }

    private static final class MutableTeacherSummary {
        private final Long id;
        private final String fullName;
        private final String buildingCode;
        private long events;
        private long children;

        private MutableTeacherSummary(Long id, String fullName, String buildingCode) {
            this.id = id;
            this.fullName = fullName;
            this.buildingCode = buildingCode;
        }
    }
}
