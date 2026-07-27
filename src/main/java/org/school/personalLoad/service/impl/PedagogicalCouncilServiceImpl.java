package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AppUser;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.school.personalLoad.dto.PedagogicalCouncilDtos;
import org.school.personalLoad.model.EmploymentContract;
import org.school.personalLoad.model.PedagogicalCouncilAttachment;
import org.school.personalLoad.model.PedagogicalCouncilItem;
import org.school.personalLoad.model.PedagogicalCouncilProtocol;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.EmploymentContractRepository;
import org.school.personalLoad.repository.PedagogicalCouncilAttachmentRepository;
import org.school.personalLoad.repository.PedagogicalCouncilProtocolRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.auth.AppUserRepository;
import org.school.personalLoad.repository.auth.AppUserTabPermissionRepository;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.PedagogicalCouncilService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PedagogicalCouncilServiceImpl implements PedagogicalCouncilService {

    private static final long MAX_DOCX_SIZE = 30L * 1024L * 1024L;
    private static final DateTimeFormatter RUSSIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int DEFAULT_AGENDA_DURATION_MINUTES = 10;
    private static final int MAX_AGENDA_DURATION_MINUTES = 720;
    private static final List<String> PROTOCOL_SIGNER_POSITIONS =
            List.of("Директор", "Заместитель директора", "Методист", "Учитель");
    private static final String SCHOOL_7_CREST =
            "/templates/pedagogical-councils/school-7-crest.jpg";
    private static final String SCHOOL_1811_HEADER =
            "/templates/pedagogical-councils/school-1811-header.png";

    private final PedagogicalCouncilProtocolRepository protocolRepository;
    private final PedagogicalCouncilAttachmentRepository attachmentRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final EmploymentContractRepository contractRepository;
    private final AppUserRepository appUserRepository;
    private final AppUserTabPermissionRepository permissionRepository;
    private final AcademicYearService academicYearService;

    @Override
    @Transactional(readOnly = true)
    public List<PedagogicalCouncilDtos.ProtocolSummary> list(String academicYear) {
        String normalizedYear = normalizeAcademicYear(academicYear);
        return protocolRepository.findAllByAcademicYearOrderByMeetingDateDescCreatedAtDesc(normalizedYear)
                .stream()
                .map(this::summary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PedagogicalCouncilDtos.ProtocolDetails get(Long id) {
        return details(requiredProtocol(id));
    }

    @Override
    public PedagogicalCouncilDtos.ProtocolDetails create(PedagogicalCouncilDtos.CreateProtocolRequest request,
                                                         SessionUser user) {
        if (request == null) {
            throw new IllegalArgumentException("Данные протокола не переданы");
        }
        String academicYear = ensureAcademicYear(request.academicYear());
        LocalDate meetingDate = requireMeetingDate(request.meetingDate(), academicYear);
        String schoolCode = SchoolCodeResolver.resolve();

        PedagogicalCouncilProtocol protocol = new PedagogicalCouncilProtocol();
        protocol.setAcademicYear(academicYear);
        protocol.setProtocolNumber(requireText(request.protocolNumber(), "Укажите номер протокола"));
        protocol.setMeetingDate(meetingDate);
        protocol.setAgendaTime(request.agendaTime());
        protocol.setStatus(PedagogicalCouncilProtocol.Status.DRAFT);
        protocol.setSourceType(PedagogicalCouncilProtocol.SourceType.CONSTRUCTOR);
        protocol.setSchoolCodeSnapshot(schoolCode);
        protocol.setSchoolNameSnapshot(schoolName(schoolCode));
        protocol.setAttendeeCount(nonNegative(request.attendeeCount(), "Количество присутствующих"));
        protocol.setCreatedByUsername(user.getUsername());
        protocol.setCreatedByFio(user.getFullName());
        applyProtocolSigners(
                protocol,
                request.chairPosition(),
                request.chairFio(),
                request.secretaryPosition(),
                request.secretaryFio()
        );
        applyItems(protocol, Optional.ofNullable(request.items()).orElseGet(List::of));
        return details(protocolRepository.save(protocol));
    }

    @Override
    public PedagogicalCouncilDtos.ProtocolDetails update(Long id,
                                                         PedagogicalCouncilDtos.UpdateProtocolRequest request,
                                                         SessionUser user) {
        if (request == null) {
            throw new IllegalArgumentException("Данные протокола не переданы");
        }
        PedagogicalCouncilProtocol protocol = requiredProtocol(id);
        if (protocol.getSourceType() == PedagogicalCouncilProtocol.SourceType.ARCHIVE_WORD) {
            throw new IllegalStateException("Архивный Word-протокол нельзя редактировать в конструкторе");
        }
        if (request.version() != null && request.version() != protocol.getVersion()) {
            throw new IllegalStateException("Протокол был изменён другим пользователем. Обновите страницу");
        }

        protocol.setProtocolNumber(requireText(request.protocolNumber(), "Укажите номер протокола"));
        protocol.setMeetingDate(requireMeetingDate(request.meetingDate(), protocol.getAcademicYear()));
        protocol.setAgendaTime(request.agendaTime());
        protocol.setAttendeeCount(nonNegative(request.attendeeCount(), "Количество присутствующих"));
        applyProtocolSigners(
                protocol,
                request.chairPosition(),
                request.chairFio(),
                request.secretaryPosition(),
                request.secretaryFio()
        );
        applyItems(protocol, Optional.ofNullable(request.items()).orElseGet(List::of));

        PedagogicalCouncilProtocol.Status requestedStatus =
                workflowStatus(Optional.ofNullable(request.status()).orElse(protocol.getStatus()));
        validateReadyForStatus(protocol, requestedStatus);
        protocol.setStatus(requestedStatus);
        if (requestedStatus == PedagogicalCouncilProtocol.Status.REGISTERED) {
            protocol.setRegisteredAt(LocalDateTime.now());
            protocol.setRegisteredBy(user.getFullName());
        }
        return details(protocolRepository.save(protocol));
    }

    @Override
    public PedagogicalCouncilDtos.ProtocolDetails uploadArchive(String academicYear,
                                                                String protocolNumber,
                                                                LocalDate meetingDate,
                                                                MultipartFile file,
                                                                SessionUser user) throws IOException {
        String normalizedYear = ensureAcademicYear(academicYear);
        LocalDate normalizedDate = requireMeetingDate(meetingDate, normalizedYear);
        byte[] content = requireDocx(file);
        String schoolCode = SchoolCodeResolver.resolve();

        PedagogicalCouncilProtocol protocol = new PedagogicalCouncilProtocol();
        protocol.setAcademicYear(normalizedYear);
        protocol.setProtocolNumber(requireText(protocolNumber, "Укажите номер протокола"));
        protocol.setMeetingDate(normalizedDate);
        protocol.setStatus(PedagogicalCouncilProtocol.Status.REGISTERED);
        protocol.setSourceType(PedagogicalCouncilProtocol.SourceType.ARCHIVE_WORD);
        protocol.setSchoolCodeSnapshot(schoolCode);
        protocol.setSchoolNameSnapshot(schoolName(schoolCode));
        protocol.setAttendeeCount(0);
        protocol.setArchiveFilename(safeFilename(file.getOriginalFilename(), "Протокол_" + protocolNumber + ".docx"));
        protocol.setArchiveDocument(content);
        protocol.setCreatedByUsername(user.getUsername());
        protocol.setCreatedByFio(user.getFullName());
        protocol.setRegisteredAt(LocalDateTime.now());
        protocol.setRegisteredBy(user.getFullName());
        return details(protocolRepository.save(protocol));
    }

    @Override
    public PedagogicalCouncilDtos.AttachmentView addAttachment(Long protocolId,
                                                               Long itemId,
                                                               MultipartFile file,
                                                               SessionUser user) throws IOException {
        PedagogicalCouncilProtocol protocol = requiredProtocol(protocolId);
        if (protocol.getSourceType() != PedagogicalCouncilProtocol.SourceType.CONSTRUCTOR) {
            throw new IllegalStateException("К архивному Word-протоколу нельзя добавлять приложения конструктора");
        }
        PedagogicalCouncilItem item = protocol.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Пункт протокола не найден"));

        int nextNumber = protocol.getItems().stream()
                .flatMap(candidate -> candidate.getAttachments().stream())
                .mapToInt(PedagogicalCouncilAttachment::getAttachmentNumber)
                .max()
                .orElse(0) + 1;

        PedagogicalCouncilAttachment attachment = new PedagogicalCouncilAttachment();
        attachment.setItem(item);
        attachment.setAttachmentNumber(nextNumber);
        attachment.setOriginalFilename(safeFilename(file.getOriginalFilename(), "Приложение_" + nextNumber + ".docx"));
        attachment.setContent(requireDocx(file));
        attachment.setUploadedBy(user.getFullName());
        item.getAttachments().add(attachment);
        attachmentRepository.save(attachment);
        return attachmentView(attachment);
    }

    @Override
    public void deleteAttachment(Long protocolId, Long attachmentId) {
        PedagogicalCouncilProtocol protocol = requiredProtocol(protocolId);
        PedagogicalCouncilAttachment attachment = protocol.getItems().stream()
                .flatMap(item -> item.getAttachments().stream())
                .filter(candidate -> Objects.equals(candidate.getId(), attachmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Приложение не найдено"));
        attachment.getItem().getAttachments().remove(attachment);
        attachmentRepository.delete(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public PedagogicalCouncilDtos.FilePayload getAttachment(Long protocolId, Long attachmentId) {
        PedagogicalCouncilProtocol protocol = requiredProtocol(protocolId);
        PedagogicalCouncilAttachment attachment = protocol.getItems().stream()
                .flatMap(item -> item.getAttachments().stream())
                .filter(candidate -> Objects.equals(candidate.getId(), attachmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Приложение не найдено"));
        return new PedagogicalCouncilDtos.FilePayload(attachment.getOriginalFilename(), attachment.getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public PedagogicalCouncilDtos.FilePayload buildFullProtocol(Long id) {
        PedagogicalCouncilProtocol protocol = requiredProtocol(id);
        if (protocol.getSourceType() == PedagogicalCouncilProtocol.SourceType.ARCHIVE_WORD) {
            if (protocol.getArchiveDocument() == null || protocol.getArchiveDocument().length == 0) {
                throw new IllegalStateException("Файл архивного протокола отсутствует");
            }
            return new PedagogicalCouncilDtos.FilePayload(protocol.getArchiveFilename(), protocol.getArchiveDocument());
        }
        validateReadyForExport(protocol);
        String filename = safeFilename(
                "Протокол №" + protocol.getProtocolNumber() + " от " + RUSSIAN_DATE.format(protocol.getMeetingDate()) + ".docx",
                "Протокол.docx"
        );
        return new PedagogicalCouncilDtos.FilePayload(filename, generateProtocol(protocol));
    }

    @Override
    @Transactional(readOnly = true)
    public PedagogicalCouncilDtos.FilePayload buildExtract(Long id,
                                                           PedagogicalCouncilDtos.ExtractRequest request,
                                                           SessionUser user) {
        PedagogicalCouncilProtocol protocol = requiredProtocol(id);
        if (protocol.getSourceType() == PedagogicalCouncilProtocol.SourceType.ARCHIVE_WORD) {
            throw new IllegalStateException("Автоматическая выписка доступна только для протоколов, созданных в конструкторе");
        }
        if (request == null || request.itemIds() == null || request.itemIds().isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один пункт для выписки");
        }
        validateReadyForExport(protocol);

        Set<Long> selectedIds = new LinkedHashSet<>(request.itemIds());
        List<PedagogicalCouncilItem> selectedItems = protocol.getItems().stream()
                .filter(item -> selectedIds.contains(item.getId()))
                .sorted(Comparator.comparingInt(PedagogicalCouncilItem::getItemOrder))
                .toList();
        if (selectedItems.size() != selectedIds.size()) {
            throw new IllegalArgumentException("Один из выбранных пунктов не относится к этому протоколу");
        }

        List<CertifierSnapshot> signers = resolveCertifiers(request, user);
        StaffSnapshot approver = null;
        if (request.separateApproval()) {
            if (request.approverTeacherId() == null) {
                throw new IllegalArgumentException("Выберите сотрудника, который утверждает выписку");
            }
            approver = resolveStaff(request.approverTeacherId(), true, "утверждающего");
            String approverPosition = normalizeOptional(request.approverPosition());
            if (approverPosition != null) {
                approver = new StaffSnapshot(approver.teacherId(), approver.shortFio(), approverPosition);
            }
        }

        String filename = safeFilename(
                "Выписка из протокола №" + protocol.getProtocolNumber() + " от "
                        + RUSSIAN_DATE.format(protocol.getMeetingDate()) + ".docx",
                "Выписка.docx"
        );
        return new PedagogicalCouncilDtos.FilePayload(
                filename,
                generateExtract(protocol, selectedItems, signers, approver, request)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedagogicalCouncilDtos.StaffView> staff() {
        return teacherRepository.findAll().stream()
                .filter(this::isActiveTeacher)
                .map(this::staffView)
                .sorted(Comparator.comparing(PedagogicalCouncilDtos.StaffView::fio, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedagogicalCouncilDtos.CertifierView> certifiers() {
        return java.util.stream.Stream.concat(
                        permissionRepository.findAllByTabAndCanExportTrue(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)
                                .stream()
                                .map(permission -> permission.getUser()),
                        appUserRepository.findAll().stream()
                                .filter(user -> user.getRole() == org.school.personalLoad.auth.UserRole.ADMIN)
                )
                .filter(AppUser::isActive)
                .filter(AppUser::isCanView)
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .map(this::certifierView)
                .sorted(Comparator.comparing(PedagogicalCouncilDtos.CertifierView::fio, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void applyProtocolSigners(PedagogicalCouncilProtocol protocol,
                                      String chairPosition,
                                      String chairFio,
                                      String secretaryPosition,
                                      String secretaryFio) {
        ManualSigner chair = normalizeManualSigner(chairPosition, chairFio, "председателя");
        ManualSigner secretary = normalizeManualSigner(secretaryPosition, secretaryFio, "секретаря");
        protocol.setChairTeacherId(null);
        protocol.setChairPositionSnapshot(chair == null ? null : chair.position());
        protocol.setChairFioSnapshot(chair == null ? null : chair.fio());
        protocol.setSecretaryTeacherId(null);
        protocol.setSecretaryPositionSnapshot(secretary == null ? null : secretary.position());
        protocol.setSecretaryFioSnapshot(secretary == null ? null : secretary.fio());
    }

    private void applyItems(PedagogicalCouncilProtocol protocol,
                            List<PedagogicalCouncilDtos.ItemRequest> requests) {
        Map<Long, PedagogicalCouncilItem> existingById = protocol.getItems().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(PedagogicalCouncilItem::getId, Function.identity()));
        Set<Long> retainedIds = requests.stream()
                .map(PedagogicalCouncilDtos.ItemRequest::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        protocol.getItems().removeIf(item -> item.getId() != null && !retainedIds.contains(item.getId()));

        int order = 1;
        for (PedagogicalCouncilDtos.ItemRequest request : requests) {
            if (request == null) {
                continue;
            }
            String agendaTitle = requireText(request.agendaTitle(), "Укажите вопрос повестки");
            int agendaDuration = normalizeAgendaDuration(request.agendaDurationMinutes());
            StaffSnapshot speaker = resolveStaff(request.speakerTeacherId(), false, "выступающего");
            String speakerPosition = normalizeOptional(request.speakerPosition());
            if (speaker == null && speakerPosition != null) {
                throw new IllegalArgumentException("Выберите ФИО выступающего");
            }
            String speechContent = normalizeOptional(request.speechContent());
            String decisionText = requireText(request.decisionText(), "Укажите текст решения");
            int votesFor = nonNegative(request.votesFor(), "Количество голосов «за»");
            int votesAgainst = nonNegative(request.votesAgainst(), "Количество голосов «против»");
            int votesAbstained = nonNegative(request.votesAbstained(), "Количество воздержавшихся");
            long distributedVotes = (long) votesFor + votesAgainst + votesAbstained;
            if (distributedVotes > protocol.getAttendeeCount()) {
                throw new IllegalArgumentException("По пункту «" + agendaTitle + "» распределено "
                        + distributedVotes + " голосов при " + protocol.getAttendeeCount()
                        + " присутствующих");
            }

            PedagogicalCouncilItem item;
            boolean newItem = request.id() == null;
            if (newItem) {
                item = new PedagogicalCouncilItem();
                item.setProtocol(protocol);
            } else {
                item = existingById.get(request.id());
                if (item == null) {
                    throw new IllegalArgumentException("Пункт протокола №" + request.id() + " не найден");
                }
            }
            item.setItemOrder(order++);
            item.setAgendaTitle(agendaTitle);
            item.setAgendaDurationMinutes(agendaDuration);
            item.setSpeakerTeacherId(speaker == null ? null : speaker.teacherId());
            item.setSpeakerPositionSnapshot(speaker == null
                    ? null
                    : Optional.ofNullable(speakerPosition).orElse(speaker.position()));
            item.setSpeakerFioSnapshot(speaker == null ? null : speaker.shortFio());
            item.setSpeechContent(speechContent);
            item.setDecisionText(decisionText);
            item.setVotesFor(votesFor);
            item.setVotesAgainst(votesAgainst);
            item.setVotesAbstained(votesAbstained);
            if (newItem) {
                protocol.getItems().add(item);
            }
        }
    }

    private void validateReadyForStatus(PedagogicalCouncilProtocol protocol,
                                        PedagogicalCouncilProtocol.Status status) {
        if (workflowStatus(status) == PedagogicalCouncilProtocol.Status.REGISTERED) {
            validateReadyForExport(protocol);
        }
    }

    private PedagogicalCouncilProtocol.Status workflowStatus(PedagogicalCouncilProtocol.Status status) {
        return status == PedagogicalCouncilProtocol.Status.REGISTERED
                || status == PedagogicalCouncilProtocol.Status.CORRECTED
                ? PedagogicalCouncilProtocol.Status.REGISTERED
                : PedagogicalCouncilProtocol.Status.DRAFT;
    }

    private void validateReadyForExport(PedagogicalCouncilProtocol protocol) {
        if (protocol.getItems().isEmpty()) {
            throw new IllegalStateException("Добавьте хотя бы один пункт протокола");
        }
        if (isBlank(protocol.getChairFioSnapshot())) {
            throw new IllegalStateException("Укажите ФИО председателя педагогического совета");
        }
        if (isBlank(protocol.getChairPositionSnapshot())) {
            throw new IllegalStateException("Выберите должность председателя педагогического совета");
        }
        if (isBlank(protocol.getSecretaryFioSnapshot())) {
            throw new IllegalStateException("Укажите ФИО секретаря педагогического совета");
        }
        if (isBlank(protocol.getSecretaryPositionSnapshot())) {
            throw new IllegalStateException("Выберите должность секретаря педагогического совета");
        }
        for (PedagogicalCouncilItem item : protocol.getItems()) {
            requireText(item.getAgendaTitle(), "Укажите вопрос повестки");
            requireText(item.getDecisionText(), "Укажите текст решения по пункту " + item.getItemOrder());
        }
    }

    private PedagogicalCouncilProtocol requiredProtocol(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Идентификатор протокола не передан");
        }
        return protocolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Протокол не найден"));
    }

    private PedagogicalCouncilDtos.ProtocolSummary summary(PedagogicalCouncilProtocol protocol) {
        int attachments = protocol.getItems().stream().mapToInt(item -> item.getAttachments().size()).sum();
        String fileName = protocol.getSourceType() == PedagogicalCouncilProtocol.SourceType.ARCHIVE_WORD
                ? protocol.getArchiveFilename()
                : null;
        return new PedagogicalCouncilDtos.ProtocolSummary(
                protocol.getId(),
                protocol.getAcademicYear(),
                protocol.getProtocolNumber(),
                protocol.getMeetingDate(),
                protocol.getStatus(),
                protocol.getSourceType(),
                protocol.getAttendeeCount(),
                protocol.getItems().size(),
                attachments,
                fileName,
                protocol.getCreatedByFio(),
                protocol.getCreatedAt(),
                protocol.getUpdatedAt()
        );
    }

    private PedagogicalCouncilDtos.ProtocolDetails details(PedagogicalCouncilProtocol protocol) {
        return new PedagogicalCouncilDtos.ProtocolDetails(
                protocol.getId(),
                protocol.getAcademicYear(),
                protocol.getProtocolNumber(),
                protocol.getMeetingDate(),
                protocol.getAgendaTime(),
                protocol.getStatus(),
                protocol.getSourceType(),
                protocol.getSchoolCodeSnapshot(),
                protocol.getSchoolNameSnapshot(),
                protocol.getAttendeeCount(),
                protocol.getChairTeacherId(),
                protocol.getChairPositionSnapshot(),
                protocol.getChairFioSnapshot(),
                protocol.getSecretaryTeacherId(),
                protocol.getSecretaryPositionSnapshot(),
                protocol.getSecretaryFioSnapshot(),
                protocol.getArchiveFilename(),
                protocol.getCreatedByUsername(),
                protocol.getCreatedByFio(),
                protocol.getCreatedAt(),
                protocol.getUpdatedAt(),
                protocol.getRegisteredAt(),
                protocol.getRegisteredBy(),
                protocol.getVersion(),
                protocol.getItems().stream()
                        .sorted(Comparator.comparingInt(PedagogicalCouncilItem::getItemOrder))
                        .map(this::itemView)
                        .toList()
        );
    }

    private PedagogicalCouncilDtos.ItemView itemView(PedagogicalCouncilItem item) {
        return new PedagogicalCouncilDtos.ItemView(
                item.getId(),
                item.getItemOrder(),
                item.getAgendaTitle(),
                agendaDurationMinutes(item),
                item.getSpeakerTeacherId(),
                item.getSpeakerPositionSnapshot(),
                item.getSpeakerFioSnapshot(),
                item.getSpeechContent(),
                item.getDecisionText(),
                item.getVotesFor(),
                item.getVotesAgainst(),
                item.getVotesAbstained(),
                item.getAttachments().stream()
                        .sorted(Comparator.comparingInt(PedagogicalCouncilAttachment::getAttachmentNumber))
                        .map(this::attachmentView)
                        .toList()
        );
    }

    private PedagogicalCouncilDtos.AttachmentView attachmentView(PedagogicalCouncilAttachment attachment) {
        return new PedagogicalCouncilDtos.AttachmentView(
                attachment.getId(),
                attachment.getAttachmentNumber(),
                attachment.getOriginalFilename(),
                attachment.getUploadedBy(),
                attachment.getCreatedAt()
        );
    }

    private PedagogicalCouncilDtos.StaffView staffView(TeacherDirectoryEntry teacher) {
        return new PedagogicalCouncilDtos.StaffView(
                teacher.getId(),
                teacher.getFioTeacher(),
                shortFio(teacher),
                positionForTeacher(teacher.getId())
        );
    }

    private PedagogicalCouncilDtos.CertifierView certifierView(AppUser user) {
        Optional<TeacherDirectoryEntry> teacher = user.getTeacherId() == null
                ? teacherRepository.findByFioTeacherIgnoreCase(user.getFullName())
                : teacherRepository.findById(user.getTeacherId());
        String position = teacher
                .map(row -> positionForTeacher(row.getId()))
                .filter(value -> !isBlank(value))
                .orElseGet(() -> isBlank(user.getDocumentPosition()) ? "Уполномоченный сотрудник" : user.getDocumentPosition().trim());
        String fio = teacher.map(TeacherDirectoryEntry::getFioTeacher).orElse(user.getFullName());
        String shortFio = teacher.map(this::shortFio).orElse(user.getFullName());
        return new PedagogicalCouncilDtos.CertifierView(
                user.getId(),
                teacher.map(TeacherDirectoryEntry::getId).orElse(null),
                fio,
                shortFio,
                position
        );
    }

    private StaffSnapshot resolveStaff(Long teacherId, boolean required, String roleName) {
        if (teacherId == null) {
            if (required) {
                throw new IllegalArgumentException("Выберите " + roleName);
            }
            return null;
        }
        TeacherDirectoryEntry teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Сотрудник для роли «" + roleName + "» не найден в кадрах"));
        if (!isActiveTeacher(teacher)) {
            throw new IllegalStateException("Сотрудник " + teacher.getFioTeacher() + " не является действующим");
        }
        return new StaffSnapshot(teacher.getId(), shortFio(teacher), positionForTeacher(teacher.getId()));
    }

    private String shortFio(TeacherDirectoryEntry teacher) {
        if (!isBlank(teacher.getInitials())) {
            return teacher.getInitials().trim();
        }
        String[] parts = teacher.getFioTeacher().trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < Math.min(parts.length, 3); i++) {
            if (!parts[i].isBlank()) {
                result.append(' ').append(Character.toUpperCase(parts[i].charAt(0))).append('.');
            }
        }
        return result.toString();
    }

    private String positionForTeacher(Long teacherId) {
        TeacherDirectoryEntry teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher != null && !isBlank(teacher.getPrimaryPosition())) {
            return teacher.getPrimaryPosition().trim();
        }
        return contractRepository.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(teacherId).stream()
                .filter(EmploymentContract::isActive)
                .map(EmploymentContract::getPositionName)
                .filter(value -> !isBlank(value))
                .findFirst()
                .orElse("Педагогический работник");
    }

    private boolean isActiveTeacher(TeacherDirectoryEntry teacher) {
        LocalDate today = LocalDate.now();
        return !teacher.isArchived()
                && (teacher.getDismissalDate() == null || teacher.getDismissalDate().isAfter(today));
    }

    private List<CertifierSnapshot> resolveCertifiers(PedagogicalCouncilDtos.ExtractRequest request,
                                                      SessionUser currentSessionUser) {
        LinkedHashMap<Long, String> requestedPositions = new LinkedHashMap<>();
        Optional.ofNullable(request.certifiers()).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .filter(signer -> signer.userId() != null)
                .forEach(signer -> requestedPositions.put(signer.userId(), normalizeOptional(signer.position())));
        Optional.ofNullable(request.certifierUserIds()).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .forEach(userId -> requestedPositions.putIfAbsent(userId, null));

        Long currentUserId = currentSessionUser.getId();
        if (currentUserId == null) {
            throw new IllegalStateException("Не удалось определить пользователя, который формирует выписку");
        }
        LinkedHashMap<Long, String> ordered = new LinkedHashMap<>();
        if (requestedPositions.isEmpty()) {
            ordered.put(currentUserId, null);
        } else {
            ordered.putAll(requestedPositions);
        }

        Set<Long> allowedIds = permissionRepository.findAllByTabAndCanExportTrue(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)
                .stream()
                .map(permission -> permission.getUser().getId())
                .collect(Collectors.toSet());
        List<CertifierSnapshot> result = new ArrayList<>();
        for (Map.Entry<Long, String> requested : ordered.entrySet()) {
            AppUser user = requiredActiveUser(requested.getKey());
            boolean currentUser = Objects.equals(user.getId(), currentUserId);
            if (!currentUser
                    && user.getRole() != org.school.personalLoad.auth.UserRole.ADMIN
                    && !allowedIds.contains(user.getId())) {
                throw new IllegalStateException("У сотрудника " + user.getFullName() + " нет права заверять выписки");
            }
            PedagogicalCouncilDtos.CertifierView view = certifierView(user);
            String position = Optional.ofNullable(requested.getValue())
                    .filter(value -> !value.isBlank())
                    .orElse(view.position());
            result.add(new CertifierSnapshot(user.getId(), view.shortFio(), position));
        }
        return result;
    }

    private AppUser requiredActiveUser(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Выбранный сотрудник не найден"));
        if (!user.isActive() || !user.isCanView()) {
            throw new IllegalStateException("Сотрудник " + user.getFullName() + " отключён");
        }
        return user;
    }

    private byte[] generateProtocol(PedagogicalCouncilProtocol protocol) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configureDocument(document);
            if (!appendSchoolLetterhead(document, protocol.getSchoolCodeSnapshot())) {
                centered(document, protocol.getSchoolNameSnapshot(), true, 12);
            }
            centered(document, "ПРОТОКОЛ № " + protocol.getProtocolNumber(), true, 14);
            centered(document, "заседания педагогического совета", false, 14);
            centered(document, "от " + formatDateLong(protocol.getMeetingDate()), false, 14);
            blank(document);

            paragraph(document, "Присутствовали: " + protocol.getAttendeeCount() + " чел.", false, ParagraphAlignment.LEFT);
            paragraph(document, "Председатель: " + signerText(protocol.getChairPositionSnapshot(), protocol.getChairFioSnapshot()), false, ParagraphAlignment.LEFT);
            paragraph(document, "Секретарь: " + signerText(protocol.getSecretaryPositionSnapshot(), protocol.getSecretaryFioSnapshot()), false, ParagraphAlignment.LEFT);
            blank(document);

            paragraph(document, "Повестка педагогического совета", true, ParagraphAlignment.CENTER);
            for (PedagogicalCouncilItem item : sortedItems(protocol)) {
                String duration = " (" + agendaDurationMinutes(item) + " минут)";
                paragraph(document, item.getItemOrder() + ". " + item.getAgendaTitle() + duration, false, ParagraphAlignment.LEFT);
            }
            blank(document);

            for (PedagogicalCouncilItem item : sortedItems(protocol)) {
                String heard = roman(item.getItemOrder()) + ". Слушали: "
                        + signerText(item.getSpeakerPositionSnapshot(), item.getSpeakerFioSnapshot());
                if (!isBlank(item.getSpeechContent())) {
                    heard += " " + item.getSpeechContent().trim();
                }
                paragraph(document, heard, false, ParagraphAlignment.BOTH);
                paragraph(document, "Решили: " + item.getDecisionText().trim(), false, ParagraphAlignment.BOTH);
                for (PedagogicalCouncilAttachment attachment : sortedAttachments(item)) {
                    compactParagraph(document, "Приложение № " + attachment.getAttachmentNumber()
                            + " к пункту " + item.getItemOrder() + ".", false, ParagraphAlignment.LEFT, 12);
                }
                paragraph(document, votesText(item), false, ParagraphAlignment.LEFT);
                blank(document);
            }

            appendSourceSignatures(document, protocol);
            appendAllAttachments(document, protocol, sortedItems(protocol));
            document.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать Word-протокол", e);
        }
    }

    private byte[] generateExtract(PedagogicalCouncilProtocol protocol,
                                   List<PedagogicalCouncilItem> selectedItems,
                                   List<CertifierSnapshot> signers,
                                   StaffSnapshot approver,
                                   PedagogicalCouncilDtos.ExtractRequest request) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configureDocument(document);
            if (!appendSchoolLetterhead(document, protocol.getSchoolCodeSnapshot())) {
                centered(document, protocol.getSchoolNameSnapshot(), true, 12);
            }
            if (approver != null) {
                XWPFParagraph p = paragraph(document, "УТВЕРЖДАЮ", true, ParagraphAlignment.RIGHT);
                appendLine(p, approver.position(), false);
                appendLine(p, "____________ " + approver.shortFio(), false);
                appendLine(p, "«____» ____________ 20__ г.", false);
            }
            centered(document, "ВЫПИСКА ИЗ ПРОТОКОЛА", true, 14);
            centered(document, "заседания педагогического совета", false, 14);
            centered(document, "от " + formatDateLong(protocol.getMeetingDate())
                    + " № " + protocol.getProtocolNumber(), false, 14);
            blank(document);
            if (request.includeSourceSigners()) {
                paragraph(document, "Председатель педагогического совета: "
                        + signerText(protocol.getChairPositionSnapshot(), protocol.getChairFioSnapshot()),
                        false, ParagraphAlignment.LEFT);
                paragraph(document, "Секретарь: "
                        + signerText(protocol.getSecretaryPositionSnapshot(), protocol.getSecretaryFioSnapshot()),
                        false, ParagraphAlignment.LEFT);
                blank(document);
            }

            for (PedagogicalCouncilItem item : selectedItems) {
                paragraph(document, item.getItemOrder() + ". " + item.getAgendaTitle(), true, ParagraphAlignment.LEFT);
                String heard = "Слушали: " + signerText(item.getSpeakerPositionSnapshot(), item.getSpeakerFioSnapshot());
                if (!isBlank(item.getSpeechContent())) {
                    heard += " " + item.getSpeechContent().trim();
                }
                paragraph(document, heard, false, ParagraphAlignment.BOTH);
                paragraph(document, "Решили: " + item.getDecisionText().trim(), false, ParagraphAlignment.BOTH);
                for (PedagogicalCouncilAttachment attachment : sortedAttachments(item)) {
                    compactParagraph(document, "Приложение № " + attachment.getAttachmentNumber()
                            + " к пункту " + item.getItemOrder() + ".", false, ParagraphAlignment.LEFT, 12);
                }
                paragraph(document, votesText(item), false, ParagraphAlignment.LEFT);
                blank(document);
            }

            for (CertifierSnapshot certifier : signers) {
                paragraph(document, "Верно", true, ParagraphAlignment.LEFT);
                XWPFTable table = document.createTable(1, 3);
                table.setWidth("100%");
                setCellText(table.getRow(0).getCell(0), certifier.position(), false);
                setCellText(table.getRow(0).getCell(1), "________________", false);
                setCellText(table.getRow(0).getCell(2), certifier.shortFio(), false);
                removeTableBorders(table);
                formatSignatureTable(table);
                paragraph(document, "«____» ____________ 20__ г.", false, ParagraphAlignment.LEFT);
                blank(document);
            }
            paragraph(document, "М.П.", false, ParagraphAlignment.LEFT);

            appendAllAttachments(document, protocol, selectedItems);
            document.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать выписку в Word", e);
        }
    }

    private void appendSourceSignatures(XWPFDocument document, PedagogicalCouncilProtocol protocol) {
        XWPFTable table = document.createTable(2, 3);
        table.setWidth("100%");
        setCellText(table.getRow(0).getCell(0), "Председатель педагогического совета", false);
        setCellText(table.getRow(0).getCell(1), "________________", false);
        setCellText(table.getRow(0).getCell(2), protocol.getChairFioSnapshot(), false);
        setCellText(table.getRow(1).getCell(0), "Секретарь", false);
        setCellText(table.getRow(1).getCell(1), "________________", false);
        setCellText(table.getRow(1).getCell(2), protocol.getSecretaryFioSnapshot(), false);
        removeTableBorders(table);
        formatSignatureTable(table);
    }

    private void appendAllAttachments(XWPFDocument target,
                                      PedagogicalCouncilProtocol protocol,
                                      List<PedagogicalCouncilItem> items) throws IOException {
        List<PedagogicalCouncilAttachment> attachments = items.stream()
                .flatMap(item -> item.getAttachments().stream())
                .sorted(Comparator.comparingInt(PedagogicalCouncilAttachment::getAttachmentNumber))
                .toList();
        for (PedagogicalCouncilAttachment attachment : attachments) {
            XWPFParagraph pageBreak = target.createParagraph();
            pageBreak.createRun().addBreak(BreakType.PAGE);
            compactParagraph(target, "Приложение № " + attachment.getAttachmentNumber(),
                    true, ParagraphAlignment.RIGHT, 12);
            compactParagraph(target, "к протоколу педагогического совета",
                    false, ParagraphAlignment.RIGHT, 12);
            compactParagraph(
                    target,
                    "от " + formatDateLong(protocol.getMeetingDate()) + " № " + protocol.getProtocolNumber()
                            + ", пункт " + attachment.getItem().getItemOrder(),
                    false,
                    ParagraphAlignment.RIGHT,
                    12
            );
            try (XWPFDocument source = new XWPFDocument(new ByteArrayInputStream(attachment.getContent()))) {
                Map<String, String> pictureRelations = copyPictureRelations(source, target);
                for (IBodyElement element : source.getBodyElements()) {
                    if (element instanceof XWPFParagraph sourceParagraph) {
                        XWPFParagraph targetParagraph = target.createParagraph();
                        targetParagraph.getCTP().set(copyParagraph(sourceParagraph, pictureRelations));
                    } else if (element instanceof XWPFTable sourceTable) {
                        XWPFTable targetTable = target.createTable();
                        targetTable.getCTTbl().set(copyTable(sourceTable, pictureRelations));
                    }
                }
            }
        }
    }

    private Map<String, String> copyPictureRelations(XWPFDocument source, XWPFDocument target) throws IOException {
        Map<String, String> relationIds = new LinkedHashMap<>();
        for (XWPFPictureData picture : source.getAllPictures()) {
            String sourceId = source.getRelationId(picture);
            if (sourceId == null || sourceId.isBlank()) {
                continue;
            }
            try {
                relationIds.put(sourceId, target.addPictureData(picture.getData(), picture.getPictureType()));
            } catch (Exception e) {
                throw new IOException("Не удалось перенести изображение из приложения Word", e);
            }
        }
        return relationIds;
    }

    private CTP copyParagraph(XWPFParagraph source, Map<String, String> relationIds) throws IOException {
        try {
            return CTP.Factory.parse(remapRelationshipIds(source.getCTP().xmlText(), relationIds));
        } catch (XmlException e) {
            throw new IOException("Не удалось перенести абзац из приложения Word", e);
        }
    }

    private CTTbl copyTable(XWPFTable source, Map<String, String> relationIds) throws IOException {
        try {
            return CTTbl.Factory.parse(remapRelationshipIds(source.getCTTbl().xmlText(), relationIds));
        } catch (XmlException e) {
            throw new IOException("Не удалось перенести таблицу из приложения Word", e);
        }
    }

    private String remapRelationshipIds(String xml, Map<String, String> relationIds) {
        String remapped = xml;
        for (Map.Entry<String, String> relation : relationIds.entrySet()) {
            remapped = remapped.replace(
                    "\"" + relation.getKey() + "\"",
                    "\"" + relation.getValue() + "\""
            );
        }
        return remapped;
    }

    private void configureDocument(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1134));
        margins.setBottom(BigInteger.valueOf(1134));
        margins.setLeft(BigInteger.valueOf(1701));
        margins.setRight(BigInteger.valueOf(850));
        configurePageNumbering(document);
    }

    private void configurePageNumbering(XWPFDocument document) {
        XWPFHeader defaultHeader = document.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph pageNumber = firstParagraph(defaultHeader);
        pageNumber.setAlignment(ParagraphAlignment.CENTER);
        pageNumber.setSpacingAfter(0);

        XWPFRun begin = pageNumber.createRun();
        begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        XWPFRun instruction = pageNumber.createRun();
        instruction.getCTR().addNewInstrText().setStringValue("PAGE");
        XWPFRun separate = pageNumber.createRun();
        separate.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        XWPFRun value = pageNumber.createRun();
        value.setText("2");
        value.setFontFamily("Times New Roman");
        value.setFontSize(10);
        XWPFRun end = pageNumber.createRun();
        end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);

        XWPFHeader firstHeader = document.createHeader(HeaderFooterType.FIRST);
        XWPFParagraph firstPage = firstParagraph(firstHeader);
        firstPage.setSpacingAfter(0);
    }

    private XWPFParagraph firstParagraph(XWPFHeader header) {
        if (header.getParagraphs().isEmpty()) {
            return header.createParagraph();
        }
        return header.getParagraphs().get(0);
    }

    private boolean appendSchoolLetterhead(XWPFDocument document, String schoolCode) {
        String normalizedCode = Optional.ofNullable(schoolCode).orElse("").trim();
        if ("7".equalsIgnoreCase(normalizedCode)) {
            appendSchool7Letterhead(document);
            return true;
        }
        if ("1811".equalsIgnoreCase(normalizedCode)) {
            appendSchool1811Letterhead(document);
            return true;
        }
        return false;
    }

    private void appendSchool7Letterhead(XWPFDocument document) {
        XWPFParagraph crest = document.createParagraph();
        crest.setAlignment(ParagraphAlignment.CENTER);
        crest.setSpacingAfter(20);
        addPicture(
                crest.createRun(),
                SCHOOL_7_CREST,
                XWPFDocument.PICTURE_TYPE_JPEG,
                "school-7-crest.jpg",
                inchesToEmu(0.65),
                inchesToEmu(0.90)
        );

        letterheadLine(document, "ДЕПАРТАМЕНТ ОБРАЗОВАНИЯ И НАУКИ ГОРОДА МОСКВЫ", false, 12);
        letterheadLine(document, "ГОСУДАРСТВЕННОЕ БЮДЖЕТНОЕ ОБЩЕОБРАЗОВАТЕЛЬНОЕ УЧРЕЖДЕНИЕ", true, 11);
        letterheadLine(document, "ГОРОДА МОСКВЫ «ШКОЛА № 7»", true, 11);
        letterheadLine(document, "119331 г. Москва, улица Крупской, дом № 17", false, 10);
        letterheadLine(document, "Телефон: (499) 138-38-27    E-mail: 7@edu.mos.ru    http://sch7uz.mskobr.ru", false, 10);
        XWPFParagraph identifiers = letterheadLine(
                document,
                "ОКПО 40120398    ОГРН 1027739844384    ИНН/КПП 7736050780/773601001",
                false,
                10
        );
        identifiers.setSpacingAfter(160);
        var border = identifiers.getCTP().isSetPPr()
                ? identifiers.getCTP().getPPr()
                : identifiers.getCTP().addNewPPr();
        var bottom = border.isSetPBdr() ? border.getPBdr().isSetBottom()
                ? border.getPBdr().getBottom()
                : border.getPBdr().addNewBottom()
                : border.addNewPBdr().addNewBottom();
        bottom.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE);
        bottom.setSz(BigInteger.valueOf(8));
        bottom.setSpace(BigInteger.valueOf(4));
        bottom.setColor("000000");
    }

    private void appendSchool1811Letterhead(XWPFDocument document) {
        XWPFParagraph header = document.createParagraph();
        header.setAlignment(ParagraphAlignment.CENTER);
        header.setSpacingAfter(180);
        addPicture(
                header.createRun(),
                SCHOOL_1811_HEADER,
                XWPFDocument.PICTURE_TYPE_PNG,
                "school-1811-header.png",
                inchesToEmu(6.45),
                inchesToEmu(1.65)
        );
    }

    private int inchesToEmu(double inches) {
        return Units.toEMU(inches * 72.0);
    }

    private XWPFParagraph letterheadLine(XWPFDocument document,
                                         String text,
                                         boolean bold,
                                         int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBefore(0);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily("Times New Roman");
        run.setFontSize(size);
        return paragraph;
    }

    private void addPicture(XWPFRun run,
                            String resourcePath,
                            int pictureType,
                            String filename,
                            int width,
                            int height) {
        try (InputStream input = PedagogicalCouncilServiceImpl.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Не найден школьный бланк: " + resourcePath);
            }
            run.addPicture(input, pictureType, filename, width, height);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось добавить школьный бланк: " + resourcePath, e);
        }
    }

    private XWPFParagraph centered(XWPFDocument document, String text, boolean bold, int size) {
        XWPFParagraph paragraph = paragraph(document, text, bold, ParagraphAlignment.CENTER);
        paragraph.getRuns().forEach(run -> run.setFontSize(size));
        return paragraph;
    }

    private XWPFParagraph paragraph(XWPFDocument document,
                                    String text,
                                    boolean bold,
                                    ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setText(Optional.ofNullable(text).orElse(""));
        run.setBold(bold);
        run.setFontFamily("Times New Roman");
        run.setFontSize(14);
        return paragraph;
    }

    private XWPFParagraph compactParagraph(XWPFDocument document,
                                           String text,
                                           boolean bold,
                                           ParagraphAlignment alignment,
                                           int fontSize) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0);
        XWPFRun run = paragraph.createRun();
        run.setText(Optional.ofNullable(text).orElse(""));
        run.setBold(bold);
        run.setFontFamily("Times New Roman");
        run.setFontSize(fontSize);
        return paragraph;
    }

    private void appendLine(XWPFParagraph paragraph, String text, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.addBreak();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily("Times New Roman");
        run.setFontSize(14);
    }

    private void blank(XWPFDocument document) {
        document.createParagraph();
    }

    private void setCellText(XWPFTableCell cell, String value, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingBefore(180);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(Optional.ofNullable(value).orElse(""));
        run.setBold(bold);
        run.setFontFamily("Times New Roman");
        run.setFontSize(12);
    }

    private void formatSignatureTable(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            var rowProperties = row.getCtRow().isSetTrPr()
                    ? row.getCtRow().getTrPr()
                    : row.getCtRow().addNewTrPr();
            var height = rowProperties.sizeOfTrHeightArray() > 0
                    ? rowProperties.getTrHeightArray(0)
                    : rowProperties.addNewTrHeight();
            height.setVal(BigInteger.valueOf(720));
            height.setHRule(org.openxmlformats.schemas.wordprocessingml.x2006.main.STHeightRule.AT_LEAST);
        }
    }

    private void removeTableBorders(XWPFTable table) {
        table.getCTTbl().getTblPr().addNewTblBorders().addNewTop().setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL);
        table.getCTTbl().getTblPr().getTblBorders().addNewBottom().setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL);
        table.getCTTbl().getTblPr().getTblBorders().addNewLeft().setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL);
        table.getCTTbl().getTblPr().getTblBorders().addNewRight().setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL);
        table.getCTTbl().getTblPr().getTblBorders().addNewInsideH().setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL);
        table.getCTTbl().getTblPr().getTblBorders().addNewInsideV().setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NIL);
    }

    private List<PedagogicalCouncilItem> sortedItems(PedagogicalCouncilProtocol protocol) {
        return protocol.getItems().stream()
                .sorted(Comparator.comparingInt(PedagogicalCouncilItem::getItemOrder))
                .toList();
    }

    private List<PedagogicalCouncilAttachment> sortedAttachments(PedagogicalCouncilItem item) {
        return item.getAttachments().stream()
                .sorted(Comparator.comparingInt(PedagogicalCouncilAttachment::getAttachmentNumber))
                .toList();
    }

    private String votesText(PedagogicalCouncilItem item) {
        return "Голосовали: за — " + item.getVotesFor()
                + ", против — " + item.getVotesAgainst()
                + ", воздержались — " + item.getVotesAbstained() + ".";
    }

    private String signerText(String position, String fio) {
        String normalizedPosition = normalizeOptional(position);
        String normalizedFio = normalizeOptional(fio);
        if (normalizedPosition == null) {
            return normalizedFio == null ? "" : normalizedFio;
        }
        if (normalizedFio == null) {
            return normalizedPosition;
        }
        return normalizedPosition + " " + normalizedFio;
    }

    private ManualSigner normalizeManualSigner(String position, String fio, String role) {
        String normalizedPosition = normalizeOptional(position);
        String normalizedFio = normalizeOptional(fio);
        if (normalizedPosition == null && normalizedFio == null) {
            return null;
        }
        if (normalizedPosition == null) {
            throw new IllegalArgumentException("Выберите должность " + role);
        }
        if (normalizedFio == null) {
            throw new IllegalArgumentException("Укажите ФИО " + role);
        }
        String allowedPosition = PROTOCOL_SIGNER_POSITIONS.stream()
                .filter(candidate -> candidate.equalsIgnoreCase(normalizedPosition))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Должность " + role + " должна быть выбрана из списка: "
                                + String.join(", ", PROTOCOL_SIGNER_POSITIONS)
                ));
        return new ManualSigner(allowedPosition, normalizedFio);
    }

    private int normalizeAgendaDuration(Integer value) {
        int normalized = value == null ? DEFAULT_AGENDA_DURATION_MINUTES : value;
        if (normalized < 1 || normalized > MAX_AGENDA_DURATION_MINUTES) {
            throw new IllegalArgumentException("Продолжительность выступления должна быть от 1 до "
                    + MAX_AGENDA_DURATION_MINUTES + " минут");
        }
        return normalized;
    }

    private int agendaDurationMinutes(PedagogicalCouncilItem item) {
        return item.getAgendaDurationMinutes() == null
                ? DEFAULT_AGENDA_DURATION_MINUTES
                : item.getAgendaDurationMinutes();
    }

    private String ensureAcademicYear(String value) {
        String normalized = normalizeAcademicYear(value);
        boolean exists = academicYearService.findAll().stream().anyMatch(year -> normalized.equals(year.getCode()));
        if (!exists) {
            academicYearService.create(normalized);
        }
        return normalized;
    }

    private String normalizeAcademicYear(String value) {
        String normalized = requireText(value, "Укажите учебный год").replace('\\', '/');
        if (normalized.matches("\\d{4}")) {
            int start = Integer.parseInt(normalized);
            return start + "/" + (start + 1);
        }
        if (!normalized.matches("\\d{4}/\\d{4}")) {
            throw new IllegalArgumentException("Учебный год должен быть указан в формате YYYY/YYYY");
        }
        int start = Integer.parseInt(normalized.substring(0, 4));
        int end = Integer.parseInt(normalized.substring(5));
        if (end != start + 1) {
            throw new IllegalArgumentException("Учебный год должен быть последовательным");
        }
        return normalized;
    }

    private LocalDate requireMeetingDate(LocalDate meetingDate, String academicYear) {
        if (meetingDate == null) {
            throw new IllegalArgumentException("Укажите дату педагогического совета");
        }
        int start = Integer.parseInt(academicYear.substring(0, 4));
        LocalDate from = LocalDate.of(start, 8, 1);
        LocalDate to = LocalDate.of(start + 1, 7, 31);
        if (meetingDate.isBefore(from) || meetingDate.isAfter(to)) {
            throw new IllegalArgumentException("Дата протокола не относится к учебному году " + academicYear
                    + " (с 1 августа по 31 июля)");
        }
        return meetingDate;
    }

    private byte[] requireDocx(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите Word-файл");
        }
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("Можно загружать только файлы Word в формате .docx");
        }
        if (file.getSize() > MAX_DOCX_SIZE) {
            throw new IllegalArgumentException("Размер Word-файла не должен превышать 30 МБ");
        }
        byte[] content = file.getBytes();
        try (XWPFDocument ignored = new XWPFDocument(new ByteArrayInputStream(content))) {
            return content;
        } catch (Exception e) {
            throw new IllegalArgumentException("Файл повреждён или не является документом Word .docx");
        }
    }

    private int nonNegative(Integer value, String label) {
        int normalized = value == null ? 0 : value;
        if (normalized < 0) {
            throw new IllegalArgumentException(label + " не может быть отрицательным");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeFilename(String value, String fallback) {
        String normalized = Optional.ofNullable(value).orElse("")
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll("[\\r\\n\\t]", " ")
                .trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String schoolName(String code) {
        if ("demo".equalsIgnoreCase(code)) {
            return "ГБОУ Школа";
        }
        return "ГБОУ Школа № " + code;
    }

    private String formatDateLong(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " г.";
    }

    private String roman(int value) {
        if (value <= 0) {
            return String.valueOf(value);
        }
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                result.append(symbols[i]);
                remaining -= numbers[i];
            }
        }
        return result.toString();
    }

    private record StaffSnapshot(Long teacherId, String shortFio, String position) {
    }

    private record CertifierSnapshot(Long userId, String shortFio, String position) {
    }

    private record ManualSigner(String position, String fio) {
    }
}
