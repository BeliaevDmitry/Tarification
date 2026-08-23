package org.school.personalLoad.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.UserRole;
import org.school.personalLoad.dto.ProbeOrderDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.service.ProbeOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProbeOrderServiceImpl implements ProbeOrderService {

    private static final int MAX_SCAN_BYTES = 15 * 1024 * 1024;
    private static final DateTimeFormatter DATE_RU = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<DateTimeFormatter> SOURCE_DATE_FORMATS = List.of(
            DATE_RU,
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])[:.]([0-5]\\d).*?([01]?\\d|2[0-3])[:.]([0-5]\\d)(?!\\d)");

    private final ProbeOrderRepository orderRepository;
    private final ProbeOrderApprovalRepository approvalRepository;
    private final ProbeOrderSettingsRepository settingsRepository;
    private final ProbeOrderGeneratedDocumentRepository generatedDocumentRepository;
    private final ProbeOrderScanRepository scanRepository;
    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final AppUserRepository appUserRepository;
    private final ProbeOrderDocumentService documentService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProbeOrderDtos.ImportResponse importRegistration(String academicYear,
                                                            MultipartFile file,
                                                            SessionUser user) throws IOException {
        ensureImport(user);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите свежую выгрузку регистрации в формате Excel");
        }
        String filename = safeFilename(file.getOriginalFilename(), "registrations.xlsx");
        if (!filename.toLowerCase(Locale.ROOT).matches(".*\\.xls(x|m)?$")) {
            throw new IllegalArgumentException("Выгрузка регистрации должна быть файлом XLS или XLSX");
        }

        ParsedRegistration parsed;
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            parsed = parseRegistration(workbook);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Не удалось прочитать выгрузку регистрации: " + exception.getMessage(), exception);
        }

        StudentContext context = studentContext(academicYear);
        Map<OrderKey, List<ResolvedApplication>> grouped = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>(parsed.warnings());
        int linked = 0;
        int unresolved = 0;

        for (SourceApplication application : parsed.applications()) {
            SourceEvent event = parsed.eventsById().get(application.eventId());
            if (event == null) {
                unresolved++;
                addWarning(warnings, "Заявка " + application.fullName()
                        + ": мероприятие с ID " + application.eventId() + " не найдено");
                continue;
            }
            Resolution resolution = resolveApplication(application, context);
            if (resolution.leadership() == null || resolution.leadership().getSchoolBuilding() == null) {
                unresolved++;
                addWarning(warnings, "Заявка " + application.fullName() + ": не удалось определить корпус для класса "
                        + display(application.className()));
                continue;
            }
            if (resolution.student() != null) {
                linked++;
            } else {
                unresolved++;
                addWarning(warnings, "Заявка " + application.fullName()
                        + ": карточка ребёнка не найдена, участие сохранено только по ФИО");
            }
            ResolvedApplication resolved = new ResolvedApplication(
                    event,
                    application,
                    resolution.student(),
                    resolution.className(),
                    resolution.leadership(),
                    resolution.childPhone(),
                    firstNotBlank(resolution.representativeName(), application.representativeName()),
                    firstNotBlank(resolution.representativePhone(), application.representativePhone())
            );
            OrderKey key = new OrderKey(event.id(), resolution.leadership().getSchoolBuilding().getId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(resolved);
        }

        int created = 0;
        int updated = 0;
        int skippedReleased = 0;
        LocalDateTime uploadedAt = LocalDateTime.now();
        for (Map.Entry<OrderKey, List<ResolvedApplication>> entry : grouped.entrySet()) {
            List<ResolvedApplication> rows = deduplicate(entry.getValue());
            ResolvedApplication first = rows.get(0);
            SchoolBuilding building = first.leadership().getSchoolBuilding();
            Optional<ProbeOrder> existing = orderRepository
                    .findByAcademicYearAndExternalEventIdAndSchoolBuilding_Id(
                            academicYear, first.event().id(), building.getId());
            if (existing.isPresent() && existing.get().getStatus() == ProbeOrderStatus.RELEASED) {
                skippedReleased++;
                addWarning(warnings, "Выпущенный приказ по мероприятию «" + first.event().name()
                        + "» и корпусу " + building.getCode() + " не изменён повторной загрузкой");
                continue;
            }

            ProbeOrder order = existing.orElseGet(ProbeOrder::new);
            boolean isNew = order.getId() == null;
            boolean changed = isNew || sourceChanged(order, first.event())
                    || !participantFingerprint(order.getParticipants()).equals(resolvedFingerprint(rows));
            applySource(order, academicYear, first.event(), building, filename, uploadedAt, user.getFullName(), isNew);
            if (changed) {
                replaceParticipants(order, rows);
                resetWorkflow(order);
            }
            suggestCompanions(order, rows);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            if (isNew) created++; else updated++;
        }

        return new ProbeOrderDtos.ImportResponse(
                parsed.eventsById().size(),
                parsed.applications().size(),
                created,
                updated,
                skippedReleased,
                linked,
                unresolved,
                List.copyOf(warnings)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbeOrderDtos.OrderView> list(String academicYear, SessionUser user) {
        ensureView(user);
        List<ProbeOrder> orders = orderRepository.findAllByAcademicYearOrderByEventDateAscStartTimeAsc(academicYear)
                .stream()
                .filter(order -> canSeeOrder(user, order))
                .sorted(orderComparator())
                .toList();
        Map<Long, ProbeOrderGeneratedDocument> documents = generatedDocuments(orders);
        Map<Long, ProbeOrderScan> scans = scans(orders);
        Map<Long, List<ProbeOrderApproval>> approvals = approvals(orders);
        ProbeOrderApprovalMode approvalMode = currentApprovalMode();
        return orders.stream().map(order -> toView(order, user,
                documents.containsKey(order.getId()), scans.containsKey(order.getId()), approvalMode,
                approvals.getOrDefault(order.getId(), List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProbeOrderDtos.ReferenceData references(String academicYear, SessionUser user) {
        ensureView(user);
        List<TeacherDirectoryEntry> activeStaff = teacherRepository.findAll().stream()
                .filter(this::isActiveStaff)
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ProbeOrderDtos.StaffOption> staff = activeStaff.stream().map(this::staffOption).toList();

        List<ProbeOrderDtos.StudentOption> students = latestContingentRows(academicYear).stream()
                .filter(row -> row.getStudentId() != null)
                .filter(row -> isSchoolClass(row.getClassName()))
                .collect(Collectors.toMap(
                        ContingentStudent::getStudentId,
                        row -> new ProbeOrderDtos.StudentOption(row.getStudentId(), row.getFullName(), row.getClassName()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(ProbeOrderDtos.StudentOption::className, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProbeOrderDtos.StudentOption::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Long defaultSigner = appUserRepository.findById(user.getId())
                .map(appUser -> appUser.getTeacherId())
                .orElse(null);
        if (defaultSigner == null) {
            defaultSigner = teacherRepository.findByFioTeacherIgnoreCase(user.getFullName())
                    .map(TeacherDirectoryEntry::getId).orElse(null);
        }
        return new ProbeOrderDtos.ReferenceData(staff, staff, students, defaultSigner);
    }

    @Override
    @Transactional(readOnly = true)
    public ProbeOrderDtos.SettingsView settings(SessionUser user) {
        ensureView(user);
        ProbeOrderApprovalMode mode = currentApprovalMode();
        return new ProbeOrderDtos.SettingsView(mode, approvalModeLabel(mode), canEditSettings(user));
    }

    @Override
    @Transactional
    public ProbeOrderDtos.SettingsView updateSettings(ProbeOrderDtos.SettingsRequest request, SessionUser user) {
        ensureLeadershipEdit(user);
        if (request == null || request.approvalMode() == null) {
            throw new IllegalArgumentException("Выберите, кто должен согласовывать приказы");
        }
        ProbeOrderSettings settings = settingsRepository.findById(ProbeOrderSettings.DEFAULT_ID)
                .orElseGet(ProbeOrderSettings::new);
        settings.setApprovalMode(request.approvalMode());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
        settingsRepository.save(settings);
        refreshPendingApprovalSummaries(request.approvalMode());
        return new ProbeOrderDtos.SettingsView(request.approvalMode(),
                approvalModeLabel(request.approvalMode()), true);
    }

    @Override
    @Transactional
    public ProbeOrderDtos.OrderView update(Long id, ProbeOrderDtos.EditRequest request, SessionUser user) {
        ProbeOrder order = editableOrder(id, user);
        if (request == null) throw new IllegalArgumentException("Данные изменения не переданы");
        if (request.eventDate() == null) throw new IllegalArgumentException("Укажите дату мероприятия");
        String eventName = requireText(request.eventName(), "Укажите название мероприятия");
        String venue = requireText(request.venue(), "Укажите место проведения");
        String address = requireText(request.eventAddress(), "Укажите адрес мероприятия");
        String gatheringPlace = requireText(request.gatheringPlace(), "Укажите место сбора");
        validateTimes(request.startTime(), request.endTime(), request.gatheringTime(), request.returnTime());

        order.setEventName(eventName);
        order.setEventDate(request.eventDate());
        order.setStartTime(request.startTime());
        order.setEndTime(request.endTime());
        order.setVenue(venue);
        order.setEventAddress(address);
        order.setGatheringTime(request.gatheringTime());
        order.setGatheringPlace(gatheringPlace);
        order.setReturnTime(request.returnTime());
        if (request.participants() != null) {
            replaceParticipantsFromRequest(order, request.participants());
        }
        if (order.getParticipants().isEmpty()) {
            throw new IllegalArgumentException("В приказе должен остаться хотя бы один ребёнок");
        }
        resetWorkflow(order);
        order.setUpdatedAt(LocalDateTime.now());
        ProbeOrder saved = orderRepository.save(order);
        return toView(saved, user, false, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional
    public ProbeOrderDtos.OrderView assignCompanions(Long id,
                                                     ProbeOrderDtos.CompanionRequest request,
                                                     SessionUser user) {
        ProbeOrder order = editableOrder(id, user);
        if (request == null) throw new IllegalArgumentException("Сопровождающие не выбраны");
        TeacherDirectoryEntry primary = staff(request.primaryTeacherId(), "Выберите основного сопровождающего");
        TeacherDirectoryEntry secondary = request.secondaryTeacherId() == null
                ? null : staff(request.secondaryTeacherId(), "Второй сопровождающий не найден");
        if (secondary != null && Objects.equals(primary.getId(), secondary.getId())) {
            throw new IllegalArgumentException("Основной и второй сопровождающий должны быть разными сотрудниками");
        }
        order.setPrimaryCompanion(primary);
        order.setSecondaryCompanion(secondary);
        resetWorkflow(order);
        order.setUpdatedAt(LocalDateTime.now());
        return toView(orderRepository.save(order), user, false, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional
    public ProbeOrderDtos.ContactRefreshResponse refreshContacts(Long id, SessionUser user) {
        ProbeOrder order = requireOrder(id);
        ensureManageOrder(user, order);
        if (order.getStatus() == ProbeOrderStatus.RELEASED) {
            throw new IllegalStateException("Контакты выпущенного приказа изменять нельзя");
        }

        StudentContext context = studentContext(order.getAcademicYear());
        int updated = 0;
        int linked = 0;
        for (ProbeOrderParticipant participant : order.getParticipants()) {
            ParticipantSource source = participantSource(participant, context);
            boolean changed = false;
            if (participant.getStudent() == null && source.student() != null) {
                participant.setStudent(source.student());
                linked++;
                changed = true;
            }
            changed |= replaceWhenPresent(participant.getChildPhone(), source.contacts().childPhone(),
                    participant::setChildPhone);
            changed |= replaceWhenPresent(participant.getRepresentativeName(), source.contacts().representativeName(),
                    participant::setRepresentativeName);
            changed |= replaceWhenPresent(participant.getRepresentativePhone(), source.contacts().representativePhone(),
                    participant::setRepresentativePhone);
            if (changed) updated++;
        }

        if (updated > 0) {
            if (order.getStatus() == ProbeOrderStatus.GENERATED) {
                generatedDocumentRepository.deleteByOrder_Id(order.getId());
                ApprovalState approval = approvalState(order, currentApprovalMode(),
                        approvalRepository.findAllByOrder_Id(order.getId()));
                order.setStatus(approval.complete() ? ProbeOrderStatus.BUILDING_APPROVED : ProbeOrderStatus.DRAFT);
            }
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
        int stillMissing = (int) order.getParticipants().stream()
                .filter(participant -> display(participant.getChildPhone()).isBlank()
                        || display(participant.getRepresentativeName()).isBlank()
                        || display(participant.getRepresentativePhone()).isBlank())
                .count();
        ProbeOrderDtos.OrderView view = toView(order, user,
                generatedDocumentRepository.findByOrder_Id(id).isPresent(),
                scanRepository.findByOrder_Id(id).isPresent());
        return new ProbeOrderDtos.ContactRefreshResponse(
                order.getParticipants().size(), updated, linked, stillMissing, view);
    }

    @Override
    @Transactional
    public ProbeOrderDtos.OrderView acknowledge(Long id, SessionUser user) {
        ProbeOrder order = requireOrder(id);
        if (order.getStatus() == ProbeOrderStatus.RELEASED) {
            throw new IllegalStateException("Выпущенный приказ уже нельзя согласовать");
        }
        ProbeOrderApprovalMode mode = currentApprovalMode();
        List<ProbeOrderApproval> existing = approvalRepository.findAllByOrder_Id(id);
        if (approvalState(order, mode, existing).complete()) {
            throw new AuthExceptions.ForbiddenException("Приказ уже согласован");
        }
        List<ApprovalTarget> pendingForUser = pendingApprovalTargets(user, order, mode, existing);
        if (pendingForUser.isEmpty()) {
            throw new AuthExceptions.ForbiddenException(
                    "Согласовать приказ может назначенный руководитель корпуса или фактической площадки");
        }
        validateReadyForApproval(order);
        LocalDateTime approvedAt = LocalDateTime.now();
        List<ProbeOrderApproval> savedApprovals = new ArrayList<>(existing);
        for (ApprovalTarget target : pendingForUser) {
            ProbeOrderApproval approval = new ProbeOrderApproval();
            approval.setOrder(order);
            approval.setScopeType(target.scopeType());
            approval.setScopeCode(target.scopeCode());
            approval.setScopeLabel(target.scopeLabel());
            approval.setApprovedAt(approvedAt);
            approval.setApprovedBy(firstNotBlank(user.getFullName(), user.getUsername(), "SYSTEM"));
            savedApprovals.add(approvalRepository.save(approval));
        }
        refreshApprovalSummary(order, mode, savedApprovals);
        order.setUpdatedAt(LocalDateTime.now());
        ProbeOrder saved = orderRepository.save(order);
        return toView(saved, user, false, scanRepository.findByOrder_Id(id).isPresent(), mode,
                approvalRepository.findAllByOrder_Id(id));
    }

    @Override
    @Transactional
    public ProbeOrderDtos.OrderView generate(Long id,
                                             ProbeOrderDtos.GenerateRequest request,
                                             SessionUser user) {
        ensureLeadershipEdit(user);
        ProbeOrder order = requireOrder(id);
        if (order.getStatus() == ProbeOrderStatus.RELEASED) {
            throw new IllegalStateException("Выпущенный приказ нельзя сформировать заново");
        }
        if (!approvalState(order, currentApprovalMode(), approvalRepository.findAllByOrder_Id(id)).complete()) {
            throw new IllegalStateException("Приказ ещё не получил все обязательные согласования");
        }
        validateReadyForApproval(order);
        if (request == null || request.orderDate() == null) {
            throw new IllegalArgumentException("Укажите дату приказа");
        }
        String number = requireText(request.orderNumber(), "Укажите номер приказа");
        TeacherDirectoryEntry signer = staff(request.signerTeacherId(), "Выберите подписанта приказа");
        String signerPosition = firstNotBlank(request.signerPosition(), signer.getPrimaryPosition(), "Директор");

        order.setOrderNumber(number);
        order.setOrderDate(request.orderDate());
        order.setSigner(signer);
        order.setSignerPosition(signerPosition);
        byte[] content = documentService.generate(documentData(order, user));
        ProbeOrderGeneratedDocument generated = generatedDocumentRepository.findByOrder_Id(id)
                .orElseGet(ProbeOrderGeneratedDocument::new);
        generated.setOrder(order);
        generated.setFileName(documentFilename(order));
        generated.setContent(content);
        generated.setGeneratedAt(LocalDateTime.now());
        generated.setGeneratedBy(user.getFullName());
        generatedDocumentRepository.save(generated);
        order.setStatus(ProbeOrderStatus.GENERATED);
        order.setUpdatedAt(LocalDateTime.now());
        ProbeOrder saved = orderRepository.save(order);
        return toView(saved, user, true, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional(readOnly = true)
    public ProbeOrderDtos.FilePayload generatedDocument(Long id, SessionUser user) {
        ensureExport(user);
        ProbeOrder order = requireOrder(id);
        ensureVisibleOrder(user, order);
        ensureOrderDetails(user, order);
        ProbeOrderGeneratedDocument document = generatedDocumentRepository.findByOrder_Id(id)
                .orElseThrow(() -> new IllegalStateException("Word-приказ ещё не сформирован"));
        return new ProbeOrderDtos.FilePayload(document.getFileName(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", document.getContent());
    }

    @Override
    @Transactional
    public ProbeOrderDtos.OrderView release(Long id, SessionUser user) {
        ensureLeadershipEdit(user);
        ProbeOrder order = requireOrder(id);
        if (order.getStatus() != ProbeOrderStatus.GENERATED
                || generatedDocumentRepository.findByOrder_Id(id).isEmpty()) {
            throw new IllegalStateException("Сначала сформируйте Word-приказ");
        }
        validateReadyForApproval(order);
        if (!approvalState(order, currentApprovalMode(), approvalRepository.findAllByOrder_Id(id)).complete()) {
            throw new IllegalStateException("Приказ ещё не получил все обязательные согласования");
        }
        order.setStatus(ProbeOrderStatus.RELEASED);
        order.setReleasedAt(LocalDateTime.now());
        order.setReleasedBy(user.getFullName());
        order.setUpdatedAt(LocalDateTime.now());
        return toView(orderRepository.save(order), user, true, scanRepository.findByOrder_Id(id).isPresent());
    }

    @Override
    @Transactional
    public ProbeOrderDtos.OrderView uploadScan(Long id, MultipartFile file, SessionUser user) throws IOException {
        ProbeOrder order = requireOrder(id);
        ensureManageOrder(user, order);
        if (order.getStatus() != ProbeOrderStatus.RELEASED) {
            throw new IllegalStateException("Скан можно прикрепить после выпуска приказа");
        }
        validateScan(file);
        ProbeOrderScan scan = scanRepository.findByOrder_Id(id).orElseGet(ProbeOrderScan::new);
        scan.setOrder(order);
        scan.setFileName(safeFilename(file.getOriginalFilename(), "scan.pdf"));
        scan.setContentType(firstNotBlank(file.getContentType(), "application/octet-stream"));
        scan.setFileSize(file.getSize());
        scan.setContent(file.getBytes());
        scan.setUploadedAt(LocalDateTime.now());
        scan.setUploadedBy(user.getFullName());
        scanRepository.save(scan);
        return toView(order, user, true, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ProbeOrderDtos.FilePayload signedScan(Long id, SessionUser user) {
        ensureExport(user);
        ProbeOrder order = requireOrder(id);
        ensureVisibleOrder(user, order);
        ensureOrderDetails(user, order);
        ProbeOrderScan scan = scanRepository.findByOrder_Id(id)
                .orElseThrow(() -> new IllegalStateException("Скан подписанного приказа не загружен"));
        return new ProbeOrderDtos.FilePayload(scan.getFileName(), scan.getContentType(), scan.getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbeOrderDtos.CalendarEvent> calendar(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from == null ? LocalDate.now().minusMonths(1) : from;
        LocalDate effectiveTo = to == null ? LocalDate.now().plusMonths(2) : to;
        if (effectiveTo.isBefore(effectiveFrom) || ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) > 370) {
            throw new IllegalArgumentException("Период календаря должен быть не больше одного года");
        }
        return orderRepository.findAllByStatusAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        ProbeOrderStatus.RELEASED, effectiveFrom, effectiveTo).stream()
                .map(this::calendarEvent)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbeOrderDtos.HistoryEvent> studentHistory(Long studentId) {
        if (studentId == null) return List.of();
        return orderRepository.findReleasedByStudentId(studentId).stream().map(this::historyEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbeOrderDtos.HistoryEvent> teacherHistory(Long teacherId) {
        if (teacherId == null) return List.of();
        return orderRepository.findReleasedByCompanionId(teacherId).stream().map(this::historyEvent).toList();
    }

    private ParsedRegistration parseRegistration(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("В файле нет листов");
        }
        Sheet eventsSheet = sheet(workbook, "мероприятия");
        Sheet applicationsSheet = sheet(workbook, "заявки");
        if (eventsSheet == null || applicationsSheet == null) {
            throw new IllegalArgumentException("В выгрузке нужны листы «Мероприятия» и «Заявки»");
        }
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("ru"));
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        List<String> warnings = new ArrayList<>();
        Map<String, SourceEvent> events = readEvents(eventsSheet, formatter, evaluator, warnings);
        List<SourceApplication> applications = readApplications(applicationsSheet, formatter, evaluator, warnings);
        if (events.isEmpty()) throw new IllegalArgumentException("На листе «Мероприятия» нет распознанных мероприятий");
        if (applications.isEmpty()) throw new IllegalArgumentException("На листе «Заявки» нет регистраций детей");
        return new ParsedRegistration(events, applications, warnings);
    }

    private Map<String, SourceEvent> readEvents(Sheet sheet,
                                                DataFormatter formatter,
                                                FormulaEvaluator evaluator,
                                                List<String> warnings) {
        Row header = firstNonEmptyRow(sheet, formatter, evaluator);
        if (header == null) return Map.of();
        Map<String, Integer> columns = columns(header, formatter, evaluator);
        int idCol = column(columns, 0, "id события", "ид события", "id");
        int nameCol = column(columns, 2, "название мероприятия", "мероприятие", "название");
        int dateCol = column(columns, 4, "дата мероприятия", "дата");
        int timeCol = column(columns, 5, "время мероприятия", "время");
        int organizerCol = column(columns, 8, "организатор");
        int partnerCol = column(columns, 9, "партнер", "партнёр");
        int addressCol = column(columns, 10, "адрес проведения", "адрес");
        Map<String, SourceEvent> result = new LinkedHashMap<>();
        for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            String id = cell(row, idCol, formatter, evaluator);
            String dateText = cell(row, dateCol, formatter, evaluator);
            if (id.isBlank() && dateText.isBlank()) continue;
            LocalDate date = parseDate(row == null ? null : row.getCell(dateCol), dateText);
            TimePair times = parseTimes(cell(row, timeCol, formatter, evaluator));
            if (id.isBlank() || date == null) {
                addWarning(warnings, "Лист «Мероприятия», строка " + (index + 1) + ": не определены ID или дата");
                continue;
            }
            String name = firstNotBlank(cell(row, nameCol, formatter, evaluator), "Профессиональная проба");
            SourceEvent event = new SourceEvent(
                    id,
                    name,
                    date,
                    times.start(),
                    times.end(),
                    cell(row, organizerCol, formatter, evaluator),
                    cell(row, partnerCol, formatter, evaluator),
                    cell(row, addressCol, formatter, evaluator)
            );
            result.put(id, event);
        }
        return result;
    }

    private List<SourceApplication> readApplications(Sheet sheet,
                                                     DataFormatter formatter,
                                                     FormulaEvaluator evaluator,
                                                     List<String> warnings) {
        Row header = firstNonEmptyRow(sheet, formatter, evaluator);
        if (header == null) return List.of();
        Map<String, Integer> columns = columns(header, formatter, evaluator);
        int eventCol = requiredColumn(columns, "ID события", "id события", "ид события");
        int fioCol = requiredColumn(columns, "ФИО", "фио", "обучающийся", "учащийся");
        int classCol = requiredColumn(columns, "Класс", "класс");
        int letterCol = optionalColumn(columns, "Литтера класса", "литера класса", "буква класса");
        int representativeCol = optionalColumn(columns, "ФИО представителя", "фио законного представителя", "фио родителя");
        int representativePhoneCol = optionalColumn(columns, "Телефон представителя", "телефон законного представителя", "телефон родителя");
        List<SourceApplication> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            String eventId = cell(row, eventCol, formatter, evaluator);
            String fio = cell(row, fioCol, formatter, evaluator);
            if (eventId.isBlank() && fio.isBlank()) continue;
            if (eventId.isBlank() || fio.isBlank()) {
                addWarning(warnings, "Лист «Заявки», строка " + (index + 1) + ": не заполнены ID события или ФИО");
                continue;
            }
            String classValue = cell(row, classCol, formatter, evaluator);
            String letter = letterCol < 0 ? "" : cell(row, letterCol, formatter, evaluator);
            String className = normalizeClass(classValue, letter);
            String key = eventId + "|" + normalizeName(fio);
            if (!unique.add(key)) continue;
            result.add(new SourceApplication(
                    eventId,
                    display(fio),
                    className,
                    representativeCol < 0 ? "" : cell(row, representativeCol, formatter, evaluator),
                    representativePhoneCol < 0 ? "" : cell(row, representativePhoneCol, formatter, evaluator)
            ));
        }
        return result;
    }

    private StudentContext studentContext(String academicYear) {
        List<ContingentStudent> rows = latestContingentRows(academicYear);
        Map<String, List<ContingentStudent>> rowsByName = rows.stream()
                .collect(Collectors.groupingBy(row -> normalizeName(row.getFullName())));
        Map<Long, StudentProfile> profilesById = studentProfileRepository.findAll().stream()
                .filter(profile -> profile.getId() != null)
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity(), (left, right) -> left));
        Map<String, List<StudentProfile>> profilesByName = profilesById.values().stream()
                .collect(Collectors.groupingBy(profile -> normalizeName(profile.getCurrentFullName())));
        Map<String, List<ClassroomLeadershipEntry>> leadershipByClass = classroomLeadershipRepository
                .findAllByAcademicYear(academicYear).stream()
                .collect(Collectors.groupingBy(entry -> classKey(entry.getClassName())));
        return new StudentContext(rowsByName, profilesById, profilesByName, leadershipByClass);
    }

    private List<ContingentStudent> latestContingentRows(String academicYear) {
        return snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .map(snapshot -> contingentStudentRepository.findAllBySnapshotId(snapshot.getId()))
                .orElseGet(List::of);
    }

    private Resolution resolveApplication(SourceApplication application, StudentContext context) {
        String normalizedName = normalizeName(application.fullName());
        List<ContingentStudent> currentRows = context.rowsByName().getOrDefault(normalizedName, List.of());
        ContingentStudent current = currentRows.size() == 1 ? currentRows.get(0) : null;
        StudentProfile student = current != null && current.getStudentId() != null
                ? context.profilesById().get(current.getStudentId()) : null;
        if (student == null) {
            List<StudentProfile> profiles = context.profilesByName().getOrDefault(normalizedName, List.of());
            if (profiles.size() == 1) student = profiles.get(0);
        }
        String className = firstNotBlank(
                current == null ? null : current.getClassName(),
                application.className()
        );
        List<ClassroomLeadershipEntry> classes = context.leadershipByClass()
                .getOrDefault(classKey(className), List.of());
        ClassroomLeadershipEntry leadership = classes.size() == 1 ? classes.get(0) : null;
        ContactData contacts = contactData(current, student);
        return new Resolution(student, className, leadership, contacts.childPhone(),
                contacts.representativeName(), contacts.representativePhone());
    }

    private ContactData contactData(ContingentStudent row, StudentProfile profile) {
        return new ContactData(
                firstNotBlank(row == null ? null : row.getPhone(), profile == null ? null : profile.getChildPhone()),
                joinedContacts(row, true, profile == null ? null : profile.getRepresentativeName()),
                joinedContacts(row, false, profile == null ? null : profile.getRepresentativePhone())
        );
    }

    private String joinedContacts(ContingentStudent row, boolean names, String profileValue) {
        LinkedHashSet<String> contacts = new LinkedHashSet<>();
        rawContactValues(row, names).forEach(value -> addContactLines(contacts, value));
        if (row != null) {
            addContactLines(contacts, names ? row.getRepresentativeName() : row.getRepresentativePhone());
        }
        addContactLines(contacts, profileValue);
        return String.join("\n", contacts);
    }

    private List<String> rawContactValues(ContingentStudent row, boolean names) {
        if (row == null || display(row.getRawPayload()).isBlank()) return List.of();
        try {
            Map<String, Object> values = objectMapper.readValue(row.getRawPayload(), new TypeReference<>() {});
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String key = normalizeHeader(entry.getKey());
                boolean target = names
                        ? key.contains("фио") && (key.contains("представител") || key.contains("родител"))
                        : key.contains("телефон") && (key.contains("представител") || key.contains("родител"));
                String value = display(Objects.toString(entry.getValue(), ""));
                if (target && !value.isBlank()) result.add(value);
            }
            return result;
        } catch (Exception ignored) {
            // Старые снимки могли хранить payload в другом формате.
        }
        return List.of();
    }

    private void addContactLines(Set<String> target, String value) {
        if (value == null) return;
        Arrays.stream(value.split("\\R"))
                .map(this::display).filter(line -> !line.isBlank()).forEach(target::add);
    }

    private void applySource(ProbeOrder order,
                             String academicYear,
                             SourceEvent event,
                             SchoolBuilding building,
                             String filename,
                             LocalDateTime uploadedAt,
                             String uploadedBy,
                             boolean isNew) {
        order.setAcademicYear(academicYear);
        order.setExternalEventId(event.id());
        order.setEventName(event.name());
        order.setEventDate(event.date());
        order.setStartTime(event.start());
        order.setEndTime(event.end());
        order.setOrganizer(display(event.organizer()));
        order.setPartner(display(event.partner()));
        order.setSchoolBuilding(building);
        if (isNew) {
            order.setVenue(firstNotBlank(event.organizer(), event.name()));
            order.setEventAddress(display(event.address()));
            order.setGatheringTime(event.start() == null ? null : event.start().minusHours(1));
            order.setGatheringPlace(display(building.getAddress()));
            order.setReturnTime(event.end() == null ? null : event.end().plusHours(1).plusMinutes(30));
            order.setCreatedAt(LocalDateTime.now());
        } else {
            order.setVenue(firstNotBlank(order.getVenue(), event.organizer(), event.name()));
            order.setEventAddress(firstNotBlank(order.getEventAddress(), event.address()));
            if (order.getGatheringTime() == null && event.start() != null) {
                order.setGatheringTime(event.start().minusHours(1));
            }
            if (order.getReturnTime() == null && event.end() != null) {
                order.setReturnTime(event.end().plusHours(1).plusMinutes(30));
            }
        }
        order.setSourceFileName(filename);
        order.setSourceUploadedAt(uploadedAt);
        order.setSourceUploadedBy(firstNotBlank(uploadedBy, "SYSTEM"));
    }

    private boolean sourceChanged(ProbeOrder order, SourceEvent event) {
        return !Objects.equals(order.getEventName(), event.name())
                || !Objects.equals(order.getEventDate(), event.date())
                || !Objects.equals(order.getStartTime(), event.start())
                || !Objects.equals(order.getEndTime(), event.end());
    }

    private void replaceParticipants(ProbeOrder order, List<ResolvedApplication> rows) {
        order.getParticipants().clear();
        for (ResolvedApplication row : rows) {
            ProbeOrderParticipant participant = new ProbeOrderParticipant();
            participant.setOrder(order);
            participant.setStudent(row.student());
            participant.setFullNameSnapshot(display(row.application().fullName()));
            participant.setNormalizedFullName(normalizeName(row.application().fullName()));
            participant.setClassNameSnapshot(ClassNameNormalizer.normalize(row.className()));
            participant.setChildPhone(displayMultiline(row.childPhone()));
            participant.setRepresentativeName(displayMultiline(row.representativeName()));
            participant.setRepresentativePhone(displayMultiline(row.representativePhone()));
            order.getParticipants().add(participant);
        }
    }

    private void replaceParticipantsFromRequest(ProbeOrder order, List<ProbeOrderDtos.ParticipantRequest> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы одного ребёнка");
        StudentContext context = studentContext(order.getAcademicYear());
        Map<Long, StudentProfile> profiles = studentProfileRepository.findAllById(rows.stream()
                        .map(ProbeOrderDtos.ParticipantRequest::studentId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(StudentProfile::getId, Function.identity()));
        List<ProbeOrderParticipant> participants = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (ProbeOrderDtos.ParticipantRequest row : rows) {
            StudentProfile profile = row.studentId() == null ? null : profiles.get(row.studentId());
            if (row.studentId() != null && profile == null) {
                throw new IllegalArgumentException("Карточка ребёнка не найдена: " + row.studentId());
            }
            String fullName = firstNotBlank(row.fullName(), profile == null ? null : profile.getCurrentFullName());
            fullName = requireText(fullName, "Укажите ФИО ребёнка");
            String className = requireText(row.className(), "Укажите класс ребёнка " + fullName);
            if (!unique.add(normalizeName(fullName))) continue;
            validateParticipantBuilding(order, className);
            ProbeOrderParticipant participant = new ProbeOrderParticipant();
            participant.setOrder(order);
            participant.setStudent(profile);
            participant.setFullNameSnapshot(fullName);
            participant.setNormalizedFullName(normalizeName(fullName));
            participant.setClassNameSnapshot(ClassNameNormalizer.normalize(className));
            ParticipantSource source = participantSource(fullName, profile, context);
            participant.setChildPhone(displayMultiline(firstNotBlank(row.childPhone(), source.contacts().childPhone())));
            participant.setRepresentativeName(displayMultiline(firstNotBlank(
                    row.representativeName(), source.contacts().representativeName())));
            participant.setRepresentativePhone(displayMultiline(firstNotBlank(
                    row.representativePhone(), source.contacts().representativePhone())));
            participants.add(participant);
        }
        order.getParticipants().clear();
        order.getParticipants().addAll(participants);
    }

    private ParticipantSource participantSource(ProbeOrderParticipant participant, StudentContext context) {
        return participantSource(participant.getFullNameSnapshot(), participant.getStudent(), context);
    }

    private ParticipantSource participantSource(String fullName,
                                                StudentProfile linkedProfile,
                                                StudentContext context) {
        String nameKey = normalizeName(fullName);
        Long linkedId = linkedProfile == null ? null : linkedProfile.getId();
        StudentProfile profile = linkedId == null ? null : context.profilesById().get(linkedId);
        if (profile == null) profile = linkedProfile;
        if (profile == null) {
            List<StudentProfile> matches = context.profilesByName().getOrDefault(nameKey, List.of());
            if (matches.size() == 1) profile = matches.get(0);
        }
        StudentProfile resolvedProfile = profile;
        List<ContingentStudent> rows = context.rowsByName().getOrDefault(nameKey, List.of());
        ContingentStudent current = rows.stream()
                .filter(row -> resolvedProfile != null && Objects.equals(row.getStudentId(), resolvedProfile.getId()))
                .findFirst().orElse(rows.size() == 1 ? rows.get(0) : null);
        return new ParticipantSource(profile, contactData(current, profile));
    }

    private boolean replaceWhenPresent(String current, String source, Consumer<String> setter) {
        String value = displayMultiline(source);
        if (value.isBlank() || value.equals(displayMultiline(current))) return false;
        setter.accept(value);
        return true;
    }

    private String displayMultiline(String value) {
        if (value == null) return "";
        return Arrays.stream(value.split("\\R"))
                .map(this::display).filter(line -> !line.isBlank()).collect(Collectors.joining("\n"));
    }

    private void validateParticipantBuilding(ProbeOrder order, String className) {
        List<ClassroomLeadershipEntry> matches = classroomLeadershipRepository.findAllByAcademicYear(order.getAcademicYear())
                .stream().filter(entry -> classKey(entry.getClassName()).equals(classKey(className))).toList();
        if (matches.size() == 1 && matches.get(0).getSchoolBuilding() != null
                && !Objects.equals(matches.get(0).getSchoolBuilding().getId(), order.getSchoolBuilding().getId())) {
            throw new IllegalArgumentException("Класс " + className + " относится к другому корпусу");
        }
    }

    private void suggestCompanions(ProbeOrder order, List<ResolvedApplication> rows) {
        List<TeacherDirectoryEntry> suggestions = rows.stream()
                .map(ResolvedApplication::leadership)
                .map(ClassroomLeadershipEntry::getTeacher)
                .filter(Objects::nonNull)
                .filter(this::isActiveStaff)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new))
                .values().stream().toList();
        if (order.getPrimaryCompanion() == null && !suggestions.isEmpty()) {
            order.setPrimaryCompanion(suggestions.get(0));
        }
        if (requiredCompanions(rows.size()) > 1 && order.getSecondaryCompanion() == null && suggestions.size() > 1) {
            order.setSecondaryCompanion(suggestions.get(1));
        }
    }

    private void resetWorkflow(ProbeOrder order) {
        if (order.getId() != null) {
            generatedDocumentRepository.deleteByOrder_Id(order.getId());
            approvalRepository.deleteAllByOrder_Id(order.getId());
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
    }

    private ProbeOrder editableOrder(Long id, SessionUser user) {
        ProbeOrder order = requireOrder(id);
        ensureManageOrder(user, order);
        if (order.getStatus() == ProbeOrderStatus.RELEASED) {
            throw new IllegalStateException("Выпущенный приказ нельзя редактировать");
        }
        return order;
    }

    private ProbeOrder requireOrder(Long id) {
        return orderRepository.findOneById(id)
                .orElseThrow(() -> new IllegalArgumentException("Приказ не найден: " + id));
    }

    private TeacherDirectoryEntry staff(Long id, String message) {
        if (id == null) throw new IllegalArgumentException(message);
        TeacherDirectoryEntry teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(message));
        if (!isActiveStaff(teacher)) throw new IllegalArgumentException("Сотрудник уволен или находится в архиве");
        return teacher;
    }

    private void validateReadyForApproval(ProbeOrder order) {
        if (order.getParticipants().isEmpty()) throw new IllegalStateException("В приказе нет детей");
        if (order.getEventDate() == null || order.getStartTime() == null || order.getEndTime() == null
                || order.getGatheringTime() == null || order.getReturnTime() == null
                || display(order.getVenue()).isBlank() || display(order.getEventAddress()).isBlank()
                || display(order.getGatheringPlace()).isBlank()) {
            throw new IllegalStateException("Заполните место, адрес и время мероприятия, сбора и возвращения");
        }
        if (!companionsComplete(order)) {
            throw new IllegalStateException(requiredCompanions(order.getParticipants().size()) > 1
                    ? "Для группы больше 10 детей назначьте двух сопровождающих"
                    : "Назначьте сопровождающего");
        }
    }

    private void validateTimes(LocalTime start, LocalTime end, LocalTime gathering, LocalTime returning) {
        if (start == null || end == null) throw new IllegalArgumentException("Укажите время начала и окончания");
        if (!end.isAfter(start)) throw new IllegalArgumentException("Окончание должно быть позже начала");
        if (gathering == null || returning == null) throw new IllegalArgumentException("Укажите время сбора и возвращения");
    }

    private void validateScan(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Выберите файл скана");
        if (file.getSize() > MAX_SCAN_BYTES) throw new IllegalArgumentException("Скан должен быть не больше 15 МБ");
        String name = safeFilename(file.getOriginalFilename(), "scan").toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".pdf") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
            throw new IllegalArgumentException("Разрешены сканы PDF, JPG и PNG");
        }
    }

    private ProbeOrderDtos.OrderView toView(ProbeOrder order,
                                             SessionUser user,
                                             boolean hasDocument,
                                             boolean hasScan) {
        return toView(order, user, hasDocument, hasScan, currentApprovalMode(),
                approvalRepository.findAllByOrder_Id(order.getId()));
    }

    private ProbeOrderDtos.OrderView toView(ProbeOrder order,
                                             SessionUser user,
                                             boolean hasDocument,
                                             boolean hasScan,
                                             ProbeOrderApprovalMode approvalMode,
                                             List<ProbeOrderApproval> savedApprovals) {
        List<ProbeOrderDtos.ParticipantView> participants = order.getParticipants().stream()
                .map(this::participantView).toList();
        List<String> warnings = new ArrayList<>();
        long unlinked = participants.stream().filter(row -> !row.linkedToStudentCard()).count();
        long withoutChildPhone = participants.stream().filter(row -> display(row.childPhone()).isBlank()).count();
        long withoutRepresentative = participants.stream()
                .filter(row -> display(row.representativeName()).isBlank() || display(row.representativePhone()).isBlank())
                .count();
        if (unlinked > 0) warnings.add("Не связаны с карточками детей: " + unlinked);
        if (withoutChildPhone > 0) warnings.add("Нет телефона ребёнка: " + withoutChildPhone);
        if (withoutRepresentative > 0) warnings.add("Нет полных данных законного представителя: " + withoutRepresentative);
        if (!companionsComplete(order)) warnings.add(requiredCompanions(participants.size()) > 1
                ? "Нужно назначить двух сопровождающих" : "Нужно назначить сопровождающего");
        List<ApprovalTarget> approvalTargets = approvalTargets(order, approvalMode);
        ApprovalState approval = approvalState(order, approvalTargets, savedApprovals);
        if (approval.targets().isEmpty()) {
            warnings.add("Не удалось определить корпус класса для согласования");
        }
        boolean canManage = canManageOrder(user, approvalTargets);
        boolean canViewDetails = canViewOrderDetails(user, approvalTargets);
        boolean leadership = isLeadership(user);
        return new ProbeOrderDtos.OrderView(
                order.getId(), order.getAcademicYear(), order.getExternalEventId(), order.getEventName(),
                order.getEventDate(), order.getStartTime(), order.getEndTime(), order.getVenue(),
                order.getEventAddress(), order.getOrganizer(), order.getPartner(), order.getSchoolBuilding().getId(),
                order.getSchoolBuilding().getCode(), order.getSchoolBuilding().getName(), order.getGatheringPlace(),
                order.getGatheringTime(), order.getReturnTime(), classNames(order), participants.size(),
                requiredCompanions(participants.size()), staffOption(order.getPrimaryCompanion()),
                staffOption(order.getSecondaryCompanion()), companionsComplete(order), order.getStatus(),
                approvalMode, approval.views(), approval.complete(),
                order.getBuildingApprovedAt(), order.getBuildingApprovedBy(), order.getOrderNumber(), order.getOrderDate(),
                staffOption(order.getSigner()), order.getSignerPosition(),
                hasDocument && canViewDetails && user.canExportTab(AppTab.DOCUMENTS_PROBE_ORDERS),
                hasScan && canViewDetails && user.canExportTab(AppTab.DOCUMENTS_PROBE_ORDERS), order.getReleasedAt(),
                order.getReleasedBy(), order.getSourceFileName(), order.getSourceUploadedAt(),
                canViewDetails ? participants : List.of(),
                warnings, highlight(order), canManage && order.getStatus() != ProbeOrderStatus.RELEASED,
                canAcknowledge(user, order, approvalTargets, savedApprovals) && order.getStatus() != ProbeOrderStatus.RELEASED,
                leadership && user.canEditTab(AppTab.DOCUMENTS_PROBE_ORDERS)
                        && order.getStatus() != ProbeOrderStatus.RELEASED,
                leadership && user.canEditTab(AppTab.DOCUMENTS_PROBE_ORDERS)
                        && order.getStatus() == ProbeOrderStatus.GENERATED,
                canManage && order.getStatus() == ProbeOrderStatus.RELEASED
        );
    }

    private ProbeOrderDtos.ParticipantView participantView(ProbeOrderParticipant participant) {
        List<String> missing = new ArrayList<>();
        if (display(participant.getChildPhone()).isBlank()) missing.add("телефон ребёнка");
        if (display(participant.getRepresentativeName()).isBlank()) missing.add("ФИО представителя");
        if (display(participant.getRepresentativePhone()).isBlank()) missing.add("телефон представителя");
        if (participant.getStudent() == null) missing.add("связь с карточкой ребёнка");
        return new ProbeOrderDtos.ParticipantView(
                participant.getId(), participant.getStudent() == null ? null : participant.getStudent().getId(),
                participant.getFullNameSnapshot(), participant.getClassNameSnapshot(), participant.getChildPhone(),
                participant.getRepresentativeName(),
                participant.getRepresentativePhone(), participant.getStudent() != null, missing
        );
    }

    private ProbeOrderDtos.CalendarEvent calendarEvent(ProbeOrder order) {
        List<ProbeOrderDtos.CalendarParticipant> participants = new ArrayList<>();
        participants.add(new ProbeOrderDtos.CalendarParticipant(
                "BUILDING", order.getSchoolBuilding().getId(), order.getSchoolBuilding().getCode(),
                order.getSchoolBuilding().getName(), order.getSchoolBuilding().getAddress()));
        if (order.getPrimaryCompanion() != null) {
            participants.add(calendarPerson(order.getPrimaryCompanion()));
        }
        if (order.getSecondaryCompanion() != null
                && (order.getPrimaryCompanion() == null
                || !Objects.equals(order.getSecondaryCompanion().getId(), order.getPrimaryCompanion().getId()))) {
            participants.add(calendarPerson(order.getSecondaryCompanion()));
        }
        return new ProbeOrderDtos.CalendarEvent(order.getId(), order.getEventName(), order.getEventDate(),
                order.getStartTime(), order.getEndTime(), order.getSchoolBuilding().getCode(),
                order.getSchoolBuilding().getName(), classNames(order), companionNames(order), order.getVenue(),
                order.getEventAddress(), participants);
    }

    private ProbeOrderDtos.CalendarParticipant calendarPerson(TeacherDirectoryEntry teacher) {
        return new ProbeOrderDtos.CalendarParticipant(
                "PERSON", teacher.getId(), display(teacher.getNumberSchoolBuilding()), teacher.getFioTeacher(),
                display(teacher.getPrimaryPosition()));
    }

    private ProbeOrderDtos.HistoryEvent historyEvent(ProbeOrder order) {
        return new ProbeOrderDtos.HistoryEvent(order.getId(), order.getAcademicYear(), order.getEventName(),
                order.getEventDate(), order.getStartTime(), order.getEndTime(), order.getVenue(), order.getEventAddress(),
                order.getSchoolBuilding().getCode(), classNames(order), companionNames(order), order.getOrderNumber(),
                order.getOrderDate());
    }

    private ProbeOrderDocumentService.DocumentData documentData(ProbeOrder order, SessionUser user) {
        List<String> classes = classNames(order);
        DocumentPersonnel personnel = documentPersonnel(user);
        return new ProbeOrderDocumentService.DocumentData(
                order.getAcademicYear(), order.getOrderNumber(), order.getOrderDate(), order.getEventDate(),
                order.getStartTime(), classes.stream().map(this::displayClass).collect(Collectors.joining(", ")),
                classes.size() == 1 ? "класса" : "классов", order.getVenue(), order.getEventAddress(),
                order.getGatheringTime(), order.getGatheringPlace(), order.getReturnTime(),
                order.getSchoolBuilding().getManagerFio(), person(order.getPrimaryCompanion()),
                person(order.getSecondaryCompanion()), person(order.getSigner()), order.getSignerPosition(),
                person(personnel.director()), person(personnel.deputyDirector()), personnel.executor(),
                order.getParticipants().stream().map(participant -> new ProbeOrderDocumentService.ParticipantData(
                        participant.getFullNameSnapshot(), participant.getRepresentativeName(), participant.getRepresentativePhone()
                )).toList()
        );
    }

    private DocumentPersonnel documentPersonnel(SessionUser user) {
        List<TeacherDirectoryEntry> active = teacherRepository.findAll().stream()
                .filter(this::isActiveStaff).toList();
        TeacherDirectoryEntry director = linkedRoleTeacher(UserRole.DIRECTOR, active);
        if (director == null) director = active.stream().filter(teacher -> position(teacher).equals("директор")).findFirst().orElse(null);
        TeacherDirectoryEntry deputy = active.stream()
                .filter(teacher -> position(teacher).contains("заместител") && position(teacher).contains("директор"))
                .findFirst().orElse(null);
        TeacherDirectoryEntry executorTeacher = appUserRepository.findById(user.getId())
                .map(appUser -> appUser.getTeacherId()).flatMap(teacherRepository::findById)
                .orElseGet(() -> teacherRepository.findByFioTeacherIgnoreCase(user.getFullName()).orElse(null));
        ProbeOrderDocumentService.PersonData executor = executorTeacher == null
                ? new ProbeOrderDocumentService.PersonData(null, user.getFullName(), user.getFullName(), user.getFullName(),
                initials(user.getFullName()), user.getPhone())
                : person(executorTeacher);
        return new DocumentPersonnel(director, deputy, executor);
    }

    private TeacherDirectoryEntry linkedRoleTeacher(UserRole role, List<TeacherDirectoryEntry> active) {
        Set<Long> activeIds = active.stream().map(TeacherDirectoryEntry::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        return appUserRepository.findAll().stream()
                .filter(account -> account.isActive() && account.getRole() == role && account.getTeacherId() != null)
                .filter(account -> activeIds.contains(account.getTeacherId()))
                .map(account -> teacherRepository.findById(account.getTeacherId()).orElse(null))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String position(TeacherDirectoryEntry teacher) {
        return display(teacher == null ? null : teacher.getPrimaryPosition()).toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private ProbeOrderDocumentService.PersonData person(TeacherDirectoryEntry teacher) {
        if (teacher == null) return null;
        return new ProbeOrderDocumentService.PersonData(teacher.getId(), teacher.getFioTeacher(),
                teacher.getFioTeacherDative(), teacher.getFioTeacherAccusative(),
                firstNotBlank(teacher.getInitials(), initials(teacher.getFioTeacher())), teacher.getPhone());
    }

    private ProbeOrderDtos.StaffOption staffOption(TeacherDirectoryEntry teacher) {
        if (teacher == null) return null;
        return new ProbeOrderDtos.StaffOption(teacher.getId(), teacher.getFioTeacher(), teacher.getPhone(),
                teacher.getPrimaryPosition(), teacher.getNumberSchoolBuilding());
    }

    private List<String> classNames(ProbeOrder order) {
        return order.getParticipants().stream().map(ProbeOrderParticipant::getClassNameSnapshot)
                .filter(value -> value != null && !value.isBlank()).distinct()
                .sorted(classComparator()).toList();
    }

    private List<String> companionNames(ProbeOrder order) {
        List<String> result = new ArrayList<>();
        if (order.getPrimaryCompanion() != null) result.add(order.getPrimaryCompanion().getFioTeacher());
        if (order.getSecondaryCompanion() != null) result.add(order.getSecondaryCompanion().getFioTeacher());
        return result;
    }

    private Comparator<String> classComparator() {
        return Comparator.comparing((String value) -> Optional.ofNullable(ClassNameNormalizer.extractParallel(value)).orElse(99))
                .thenComparing(String::toString, String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<ProbeOrder> orderComparator() {
        LocalDate today = LocalDate.now();
        return Comparator.comparingInt((ProbeOrder order) -> order.getEventDate().isBefore(today) ? 1 : 0)
                .thenComparing(order -> order.getEventDate().isBefore(today)
                        ? order.getEventDate().toEpochDay() * -1 : order.getEventDate().toEpochDay())
                .thenComparing(order -> Optional.ofNullable(order.getStartTime()).orElse(LocalTime.MAX));
    }

    private String highlight(ProbeOrder order) {
        LocalDate today = LocalDate.now();
        if (order.getEventDate().isBefore(today)) return "PAST";
        if (order.getStatus() == ProbeOrderStatus.RELEASED) return "ACTIVE";
        if (order.getBuildingApprovedAt() != null || order.getStatus() == ProbeOrderStatus.GENERATED) {
            return "PENDING_SIGNATURE";
        }
        if (!order.getEventDate().isAfter(today.plusDays(7))) return "URGENT";
        return "DRAFT";
    }

    private boolean companionsComplete(ProbeOrder order) {
        if (order.getPrimaryCompanion() == null) return false;
        return requiredCompanions(order.getParticipants().size()) == 1 || order.getSecondaryCompanion() != null;
    }

    private int requiredCompanions(int children) {
        return children > 10 ? 2 : 1;
    }

    private boolean canSeeOrder(SessionUser user, ProbeOrder order) {
        return user != null;
    }

    private boolean canManageOrder(SessionUser user, ProbeOrder order) {
        return canManageOrder(user, order, currentApprovalMode());
    }

    private boolean canManageOrder(SessionUser user,
                                   ProbeOrder order,
                                   ProbeOrderApprovalMode approvalMode) {
        return canManageOrder(user, approvalTargets(order, approvalMode));
    }

    private boolean canManageOrder(SessionUser user, List<ApprovalTarget> approvalTargets) {
        return user.canEditTab(AppTab.DOCUMENTS_PROBE_ORDERS)
                && canViewOrderDetails(user, approvalTargets);
    }

    private boolean canViewOrderDetails(SessionUser user, List<ApprovalTarget> approvalTargets) {
        return isLeadership(user) || (user != null && user.getRole() == UserRole.BUILDING_HEAD
                && approvalTargets.stream().anyMatch(target -> targetMatchesUser(user, target)));
    }

    private boolean canViewOrderDetails(SessionUser user, ProbeOrder order) {
        return canViewOrderDetails(user, approvalTargets(order, currentApprovalMode()));
    }

    private boolean canAcknowledge(SessionUser user,
                                   ProbeOrder order,
                                   ProbeOrderApprovalMode approvalMode,
                                   List<ProbeOrderApproval> approvals) {
        return canAcknowledge(user, order, approvalTargets(order, approvalMode), approvals);
    }

    private boolean canAcknowledge(SessionUser user,
                                   ProbeOrder order,
                                   List<ApprovalTarget> approvalTargets,
                                   List<ProbeOrderApproval> approvals) {
        return user != null
                && user.canEditTab(AppTab.DOCUMENTS_PROBE_ORDERS)
                && (isLeadership(user) || user.getRole() == UserRole.BUILDING_HEAD)
                && !approvalState(order, approvalTargets, approvals).complete()
                && !pendingApprovalTargets(user, order, approvalTargets, approvals).isEmpty();
    }

    private boolean targetMatchesUser(SessionUser user, ApprovalTarget target) {
        if (user == null || target == null) return false;
        if (isLeadership(user)) return true;
        return user.getRole() == UserRole.BUILDING_HEAD
                && !scopeCode(user.getManagedBuildingCode()).isBlank()
                && scopeCode(user.getManagedBuildingCode()).equals(scopeCode(target.scopeCode()));
    }

    private List<ApprovalTarget> pendingApprovalTargets(SessionUser user,
                                                        ProbeOrder order,
                                                        ProbeOrderApprovalMode approvalMode,
                                                        List<ProbeOrderApproval> approvals) {
        return pendingApprovalTargets(user, order, approvalTargets(order, approvalMode), approvals);
    }

    private List<ApprovalTarget> pendingApprovalTargets(SessionUser user,
                                                        ProbeOrder order,
                                                        List<ApprovalTarget> approvalTargets,
                                                        List<ProbeOrderApproval> approvals) {
        if ((approvals == null || approvals.isEmpty()) && order.getBuildingApprovedAt() != null) {
            return List.of();
        }
        Set<String> approvedKeys = Optional.ofNullable(approvals).orElseGet(List::of).stream()
                .map(approval -> approvalKey(approval.getScopeType(), approval.getScopeCode()))
                .collect(Collectors.toSet());
        return approvalTargets.stream()
                .filter(target -> targetMatchesUser(user, target))
                .filter(target -> !approvedKeys.contains(approvalKey(target.scopeType(), target.scopeCode())))
                .toList();
    }

    private List<ApprovalTarget> approvalTargets(ProbeOrder order, ProbeOrderApprovalMode approvalMode) {
        if (order == null || approvalMode == null) return List.of();
        LinkedHashMap<String, ApprovalTarget> targets = new LinkedHashMap<>();
        if (approvalMode == ProbeOrderApprovalMode.ORGANIZATIONAL_BUILDING
                || approvalMode == ProbeOrderApprovalMode.BOTH) {
            Set<String> orderClasses = classNames(order).stream().map(this::classKey).collect(Collectors.toSet());
            classroomLeadershipRepository.findAllByAcademicYear(order.getAcademicYear()).stream()
                    .filter(entry -> orderClasses.contains(classKey(entry.getClassName())))
                    .map(ClassroomLeadershipEntry::getNumberSchoolBuilding)
                    .map(this::scopeCode)
                    .filter(code -> !code.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(code -> {
                        ApprovalTarget target = new ApprovalTarget(ProbeOrderApprovalScope.ORGANIZATIONAL_BUILDING,
                                code, "Корпус " + code);
                        targets.putIfAbsent(approvalKey(target.scopeType(), target.scopeCode()), target);
                    });
        }
        if (approvalMode == ProbeOrderApprovalMode.PHYSICAL_SITE
                || approvalMode == ProbeOrderApprovalMode.BOTH) {
            SchoolBuilding building = order.getSchoolBuilding();
            String siteCode = building == null ? "" : scopeCode(building.getBuildingGroup() == null
                    ? building.getCode() : building.getBuildingGroup().getCode());
            if (!siteCode.isBlank()) {
                String address = display(building.getAddress());
                String label = "Площадка " + siteCode + (address.isBlank() ? "" : " — " + address);
                ApprovalTarget target = new ApprovalTarget(ProbeOrderApprovalScope.PHYSICAL_SITE, siteCode, label);
                targets.putIfAbsent(approvalKey(target.scopeType(), target.scopeCode()), target);
            }
        }
        return List.copyOf(targets.values());
    }

    private ApprovalState approvalState(ProbeOrder order,
                                        ProbeOrderApprovalMode approvalMode,
                                        List<ProbeOrderApproval> approvals) {
        return approvalState(order, approvalTargets(order, approvalMode), approvals);
    }

    private ApprovalState approvalState(ProbeOrder order,
                                        List<ApprovalTarget> targets,
                                        List<ProbeOrderApproval> approvals) {
        List<ProbeOrderApproval> saved = Optional.ofNullable(approvals).orElseGet(List::of);
        boolean legacyApproval = saved.isEmpty() && order.getBuildingApprovedAt() != null;
        Map<String, ProbeOrderApproval> byKey = saved.stream().collect(Collectors.toMap(
                approval -> approvalKey(approval.getScopeType(), approval.getScopeCode()),
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<ProbeOrderDtos.ApprovalView> views = targets.stream().map(target -> {
            ProbeOrderApproval approval = byKey.get(approvalKey(target.scopeType(), target.scopeCode()));
            return new ProbeOrderDtos.ApprovalView(target.scopeType(), target.scopeCode(), target.scopeLabel(),
                    legacyApproval ? order.getBuildingApprovedAt() : approval == null ? null : approval.getApprovedAt(),
                    legacyApproval ? order.getBuildingApprovedBy() : approval == null ? null : approval.getApprovedBy());
        }).toList();
        boolean complete = legacyApproval || (!targets.isEmpty()
                && (approvalModeAllowsAny(targets)
                ? views.stream().anyMatch(view -> view.approvedAt() != null)
                : views.stream().allMatch(view -> view.approvedAt() != null)));
        return new ApprovalState(targets, views, complete);
    }

    private boolean approvalModeAllowsAny(List<ApprovalTarget> targets) {
        return targets.stream().map(ApprovalTarget::scopeType).collect(Collectors.toSet()).size() > 1;
    }

    private void refreshApprovalSummary(ProbeOrder order,
                                        ProbeOrderApprovalMode approvalMode,
                                        List<ProbeOrderApproval> approvals) {
        if ((approvals == null || approvals.isEmpty()) && order.getBuildingApprovedAt() != null) {
            return;
        }
        ApprovalState state = approvalState(order, approvalMode, approvals);
        if (state.complete()) {
            LocalDateTime completedAt = state.views().stream().map(ProbeOrderDtos.ApprovalView::approvedAt)
                    .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now());
            String approvedBy = state.views().stream().map(ProbeOrderDtos.ApprovalView::approvedBy)
                    .map(this::display).filter(value -> !value.isBlank()).distinct().collect(Collectors.joining(", "));
            order.setBuildingApprovedAt(completedAt);
            order.setBuildingApprovedBy(approvedBy);
            if (order.getStatus() == ProbeOrderStatus.DRAFT || order.getStatus() == ProbeOrderStatus.BUILDING_APPROVED) {
                order.setStatus(ProbeOrderStatus.BUILDING_APPROVED);
            }
        } else {
            order.setBuildingApprovedAt(null);
            order.setBuildingApprovedBy(null);
            if (order.getStatus() == ProbeOrderStatus.BUILDING_APPROVED) {
                order.setStatus(ProbeOrderStatus.DRAFT);
            }
        }
    }

    private void refreshPendingApprovalSummaries(ProbeOrderApprovalMode approvalMode) {
        for (ProbeOrder order : orderRepository.findAll()) {
            if (order.getStatus() == ProbeOrderStatus.GENERATED || order.getStatus() == ProbeOrderStatus.RELEASED
                    || order.getStatus() == ProbeOrderStatus.CANCELLED) {
                continue;
            }
            refreshApprovalSummary(order, approvalMode, approvalRepository.findAllByOrder_Id(order.getId()));
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
    }

    private String approvalKey(ProbeOrderApprovalScope scopeType, String scopeCode) {
        return String.valueOf(scopeType) + "|" + scopeCode(scopeCode);
    }

    private ProbeOrderApprovalMode currentApprovalMode() {
        return settingsRepository.findById(ProbeOrderSettings.DEFAULT_ID)
                .map(ProbeOrderSettings::getApprovalMode)
                .orElse(ProbeOrderApprovalMode.ORGANIZATIONAL_BUILDING);
    }

    private String approvalModeLabel(ProbeOrderApprovalMode mode) {
        if (mode == ProbeOrderApprovalMode.PHYSICAL_SITE) return "Руководитель фактической площадки";
        if (mode == ProbeOrderApprovalMode.BOTH) return "Руководитель корпуса или руководитель площадки";
        return "Руководитель организационного корпуса";
    }

    private boolean canEditSettings(SessionUser user) {
        return isLeadership(user) && user.canEditTab(AppTab.DOCUMENTS_PROBE_ORDERS);
    }

    private String scopeCode(String value) {
        String normalized = display(value).toUpperCase(Locale.ROOT)
                .replace('–', '-').replace('—', '-')
                .replaceAll("[CС][ПPР]", "СП")
                .replaceAll("\\s+", "");
        int detail = normalized.indexOf("::");
        if (detail >= 0) normalized = normalized.substring(0, detail);
        int address = normalized.indexOf('|');
        if (address >= 0) normalized = normalized.substring(0, address);
        return normalized.replaceFirst("^СП-(\\d+)$", "СП$1");
    }

    private boolean isLeadership(SessionUser user) {
        return user != null && (user.isAdmin() || user.getRole() == UserRole.DIRECTOR
                || user.getRole() == UserRole.DEPUTY_DIRECTOR);
    }

    private void ensureView(SessionUser user) {
        if (user == null || !user.canViewTab(AppTab.DOCUMENTS_PROBE_ORDERS)) {
            throw new AuthExceptions.ForbiddenException("Нет права просматривать приказы на пробы");
        }
    }

    private void ensureImport(SessionUser user) {
        if (!isLeadership(user) || !user.canImportTab(AppTab.DOCUMENTS_PROBE_ORDERS)) {
            throw new AuthExceptions.ForbiddenException("Загружать регистрацию может заместитель директора, директор или администратор");
        }
    }

    private void ensureLeadershipEdit(SessionUser user) {
        if (!isLeadership(user) || !user.canEditTab(AppTab.DOCUMENTS_PROBE_ORDERS)) {
            throw new AuthExceptions.ForbiddenException("Формировать и выпускать приказ может только администрация");
        }
    }

    private void ensureExport(SessionUser user) {
        if (user == null || !user.canExportTab(AppTab.DOCUMENTS_PROBE_ORDERS)) {
            throw new AuthExceptions.ForbiddenException("Нет права скачивать приказы на пробы");
        }
    }

    private void ensureManageOrder(SessionUser user, ProbeOrder order) {
        if (!canManageOrder(user, order)) {
            throw new AuthExceptions.ForbiddenException("Нет права изменять этот приказ");
        }
    }

    private void ensureVisibleOrder(SessionUser user, ProbeOrder order) {
        ensureView(user);
        if (!canSeeOrder(user, order)) {
            throw new AuthExceptions.ForbiddenException("Приказ относится к другому корпусу");
        }
    }

    private void ensureOrderDetails(SessionUser user, ProbeOrder order) {
        if (!canViewOrderDetails(user, order)) {
            throw new AuthExceptions.ForbiddenException("Приказ доступен только для информационного просмотра");
        }
    }

    private Map<Long, ProbeOrderGeneratedDocument> generatedDocuments(List<ProbeOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        return generatedDocumentRepository.findAllByOrder_IdIn(orders.stream().map(ProbeOrder::getId).toList()).stream()
                .collect(Collectors.toMap(document -> document.getOrder().getId(), Function.identity()));
    }

    private Map<Long, ProbeOrderScan> scans(List<ProbeOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        return scanRepository.findAllByOrder_IdIn(orders.stream().map(ProbeOrder::getId).toList()).stream()
                .collect(Collectors.toMap(scan -> scan.getOrder().getId(), Function.identity()));
    }

    private Map<Long, List<ProbeOrderApproval>> approvals(List<ProbeOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        return approvalRepository.findAllByOrder_IdIn(orders.stream().map(ProbeOrder::getId).toList()).stream()
                .collect(Collectors.groupingBy(approval -> approval.getOrder().getId()));
    }

    private Sheet sheet(Workbook workbook, String name) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (normalizeHeader(sheet.getSheetName()).equals(normalizeHeader(name))) return sheet;
        }
        return null;
    }

    private Row firstNonEmptyRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int index = sheet.getFirstRowNum(); index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            for (Cell cell : row) {
                if (!formatter.formatCellValue(cell, evaluator).trim().isBlank()) return row;
            }
        }
        return null;
    }

    private Map<String, Integer> columns(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Cell cell : row) {
            String value = normalizeHeader(formatter.formatCellValue(cell, evaluator));
            if (!value.isBlank()) result.putIfAbsent(value, cell.getColumnIndex());
        }
        return result;
    }

    private int requiredColumn(Map<String, Integer> columns, String label, String... aliases) {
        int result = optionalColumn(columns, aliases);
        if (result < 0) throw new IllegalArgumentException("На листе «Заявки» нет колонки «" + label + "»");
        return result;
    }

    private int optionalColumn(Map<String, Integer> columns, String... aliases) {
        for (String alias : aliases) {
            String normalized = normalizeHeader(alias);
            Integer exact = columns.get(normalized);
            if (exact != null) return exact;
        }
        for (Map.Entry<String, Integer> entry : columns.entrySet()) {
            for (String alias : aliases) {
                if (entry.getKey().contains(normalizeHeader(alias))) return entry.getValue();
            }
        }
        return -1;
    }

    private int column(Map<String, Integer> columns, int fallback, String... aliases) {
        int resolved = optionalColumn(columns, aliases);
        return resolved >= 0 ? resolved : fallback;
    }

    private String cell(Row row, int column, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null || column < 0) return "";
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : display(formatter.formatCellValue(cell, evaluator));
    }

    private LocalDate parseDate(Cell cell, String raw) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = display(raw);
        Matcher embedded = Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.\\d{4})").matcher(value);
        if (embedded.find()) value = embedded.group(1);
        for (DateTimeFormatter formatter : SOURCE_DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Следующий формат.
            }
        }
        return null;
    }

    private TimePair parseTimes(String raw) {
        Matcher matcher = TIME_RANGE.matcher(display(raw));
        if (!matcher.find()) return new TimePair(null, null);
        return new TimePair(LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))),
                LocalTime.of(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4))));
    }

    private String normalizeClass(String value, String letter) {
        String raw = display(value);
        String suffix = display(letter);
        if (!suffix.isBlank() && !raw.toUpperCase(Locale.ROOT).endsWith(suffix.toUpperCase(Locale.ROOT))) {
            raw = raw + "-" + suffix;
        }
        return ClassNameNormalizer.normalize(raw);
    }

    private String classKey(String value) {
        return ClassNameNormalizer.normalize(value).toLowerCase(Locale.ROOT)
                .replace('ё', 'е').replaceAll("[^0-9a-zа-я]", "");
    }

    private String normalizeName(String value) {
        return display(value).toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ");
    }

    private String normalizeHeader(String value) {
        return display(value).toLowerCase(Locale.ROOT).replace('ё', 'е')
                .replaceAll("[^0-9a-zа-я]+", " ").trim();
    }

    private boolean isSchoolClass(String value) {
        Integer parallel = ClassNameNormalizer.extractParallel(value);
        return parallel != null && parallel >= 1 && parallel <= 11;
    }

    private boolean isActiveStaff(TeacherDirectoryEntry teacher) {
        return teacher != null && teacher.getDismissalDate() == null && !teacher.isArchived()
                && teacher.getFioTeacher() != null
                && !teacher.getFioTeacher().trim().toLowerCase(Locale.ROOT).startsWith("вакансия");
    }

    private String participantFingerprint(List<ProbeOrderParticipant> rows) {
        return rows.stream().map(row -> normalizeName(row.getFullNameSnapshot()) + "|" + classKey(row.getClassNameSnapshot())
                        + "|" + display(row.getChildPhone()) + "|" + display(row.getRepresentativeName())
                        + "|" + display(row.getRepresentativePhone()))
                .sorted().collect(Collectors.joining("\n"));
    }

    private String resolvedFingerprint(List<ResolvedApplication> rows) {
        return rows.stream().map(row -> normalizeName(row.application().fullName()) + "|" + classKey(row.className())
                        + "|" + display(row.childPhone()) + "|" + display(row.representativeName())
                        + "|" + display(row.representativePhone()))
                .sorted().collect(Collectors.joining("\n"));
    }

    private List<ResolvedApplication> deduplicate(List<ResolvedApplication> source) {
        return new ArrayList<>(source.stream().collect(Collectors.toMap(
                row -> normalizeName(row.application().fullName()), Function.identity(), (left, right) -> left,
                LinkedHashMap::new)).values());
    }

    private String displayClass(String value) {
        String normalized = ClassNameNormalizer.normalize(value);
        Matcher matcher = Pattern.compile("^(\\d{1,2})-?([А-ЯA-Z])$").matcher(normalized);
        return matcher.matches() ? matcher.group(1) + " «" + matcher.group(2) + "»" : normalized;
    }

    private String initials(String fullName) {
        String[] parts = display(fullName).split("\\s+");
        if (parts.length == 0) return "";
        StringBuilder value = new StringBuilder(parts[0]);
        for (int i = 1; i < Math.min(parts.length, 3); i++) {
            if (!parts[i].isBlank()) value.append(" ").append(parts[i].charAt(0)).append(".");
        }
        return value.toString();
    }

    private String documentFilename(ProbeOrder order) {
        String event = safeToken(order.getEventName(), 36);
        String building = safeToken(order.getSchoolBuilding().getCode(), 20);
        return "Приказ_" + order.getEventDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_" + building + "_" + event + "_" + safeToken(order.getOrderNumber(), 20) + ".docx";
    }

    private String safeToken(String value, int max) {
        String safe = display(value).replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", "_");
        return safe.length() > max ? safe.substring(0, max) : safe;
    }

    private String safeFilename(String value, String fallback) {
        String name = firstNotBlank(value, fallback).replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "").trim();
        return name.isBlank() ? fallback : name;
    }

    private String requireText(String value, String message) {
        String normalized = display(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) return value.trim();
        }
        return "";
    }

    private String display(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private void addWarning(List<String> warnings, String value) {
        if (warnings.size() < 100) warnings.add(value);
    }

    private record SourceEvent(String id, String name, LocalDate date, LocalTime start, LocalTime end,
                               String organizer, String partner, String address) {
    }

    private record SourceApplication(String eventId, String fullName, String className,
                                     String representativeName, String representativePhone) {
    }

    private record ParsedRegistration(Map<String, SourceEvent> eventsById,
                                      List<SourceApplication> applications,
                                      List<String> warnings) {
    }

    private record StudentContext(Map<String, List<ContingentStudent>> rowsByName,
                                  Map<Long, StudentProfile> profilesById,
                                  Map<String, List<StudentProfile>> profilesByName,
                                  Map<String, List<ClassroomLeadershipEntry>> leadershipByClass) {
    }

    private record Resolution(StudentProfile student, String className, ClassroomLeadershipEntry leadership,
                              String childPhone, String representativeName, String representativePhone) {
    }

    private record ResolvedApplication(SourceEvent event, SourceApplication application, StudentProfile student,
                                       String className, ClassroomLeadershipEntry leadership,
                                       String childPhone, String representativeName, String representativePhone) {
    }

    private record ContactData(String childPhone, String representativeName, String representativePhone) {
    }

    private record ParticipantSource(StudentProfile student, ContactData contacts) {
    }

    private record OrderKey(String eventId, Long buildingId) {
    }

    private record TimePair(LocalTime start, LocalTime end) {
    }

    private record ApprovalTarget(ProbeOrderApprovalScope scopeType,
                                  String scopeCode,
                                  String scopeLabel) {
    }

    private record ApprovalState(List<ApprovalTarget> targets,
                                 List<ProbeOrderDtos.ApprovalView> views,
                                 boolean complete) {
    }

    private record DocumentPersonnel(TeacherDirectoryEntry director,
                                     TeacherDirectoryEntry deputyDirector,
                                     ProbeOrderDocumentService.PersonData executor) {
    }
}
