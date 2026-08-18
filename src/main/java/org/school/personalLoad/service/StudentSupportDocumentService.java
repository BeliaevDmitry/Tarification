package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.StudentSupportDocumentDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StudentSupportDocumentService {

    private static final long MAX_ATTACHMENT_SIZE = 15L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx"
    );
    private static final Set<StudentSupportDocumentType> CERTIFICATE_TYPES = Set.of(
            StudentSupportDocumentType.MSE_CERTIFICATE,
            StudentSupportDocumentType.CPMPC_CONCLUSION,
            StudentSupportDocumentType.CPMPC_RECOMMENDATION
    );
    private static final List<String> DEFAULT_SPECIALISTS = List.of(
            "Социальный педагог",
            "Учитель-дефектолог",
            "Учитель-логопед",
            "Педагог-психолог"
    );
    private static final Set<String> RECOMMENDATION_PROGRAMS = Set.of(
            "Основная образовательная программа дошкольного образования.",
            "Основная образовательная программа начального образования.",
            "Основная образовательная программа общего образования.",
            "Основная образовательная программа среднего образования."
    );

    private final StudentSupportDocumentRepository documentRepository;
    private final StudentSupportDocumentAttachmentRepository attachmentRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentSupportStatusRepository supportStatusRepository;
    private final NosologyCatalogEntryRepository nosologyRepository;
    private final CorrectionSpecialistCatalogEntryRepository specialistRepository;
    private final StudentSupportDocumentCorrectionRepository correctionRepository;

    @Transactional(readOnly = true)
    public List<StudentSupportDocumentDtos.View> findAll(String academicYear, LocalDate asOfDate) {
        LocalDate effectiveDate = Objects.requireNonNullElse(asOfDate, LocalDate.now());
        return documentRepository
                .findAllByAcademicYearOrderByValidToAscStudent_CurrentFullNameAsc(academicYear).stream()
                .filter(document -> CERTIFICATE_TYPES.contains(document.getDocumentType()))
                .map(document -> toView(document, effectiveDate))
                .toList();
    }

    @Transactional
    public StudentSupportDocumentDtos.View save(String academicYear,
                                                StudentSupportDocumentDtos.SaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Документ не передан");
        }
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("Выберите ребёнка");
        }
        if (request.getDocumentType() == null) {
            throw new IllegalArgumentException("Выберите тип документа");
        }
        if (!CERTIFICATE_TYPES.contains(request.getDocumentType())) {
            throw new IllegalArgumentException("В разделе «Справки» доступны МСЭ, заключения и рекомендации ЦМПК");
        }
        StudentProfile student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        StudentSupportDocument document = request.getId() == null
                ? new StudentSupportDocument()
                : documentRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден"));
        if (document.getId() != null
                && (!Objects.equals(document.getAcademicYear(), academicYear)
                || !Objects.equals(document.getStudent().getId(), student.getId()))) {
            throw new IllegalArgumentException("Документ относится к другому ребёнку или учебному году");
        }
        StudentSupportDocumentType previousType = document.getDocumentType();
        validateDocument(academicYear, student.getId(), request);
        document.setStudent(student);
        document.setAcademicYear(academicYear);
        document.setDocumentType(request.getDocumentType());
        boolean conclusion = request.getDocumentType() == StudentSupportDocumentType.CPMPC_CONCLUSION;
        boolean recommendation = request.getDocumentType() == StudentSupportDocumentType.CPMPC_RECOMMENDATION;
        boolean mse = request.getDocumentType() == StudentSupportDocumentType.MSE_CERTIFICATE;
        document.setAcceptedForm(recommendation
                ? StudentSupportDocumentForm.COPY
                : Objects.requireNonNullElse(
                        request.getAcceptedForm(),
                        conclusion ? StudentSupportDocumentForm.ORIGINAL : StudentSupportDocumentForm.COPY
                ));
        document.setDocumentNumber(conclusion
                ? trim(request.getDocumentNumber()) : null);
        document.setIssueDate(null);
        document.setValidFrom(recommendation ? null : request.getValidFrom());
        document.setValidTo(recommendation ? null : request.getValidTo());
        document.setNosologyCode(conclusion ? normalizeFullNosologyCode(request.getNosologyCode()) : null);
        document.setEducationStage(conclusion || recommendation ? request.getEducationStage() : null);
        document.setEducationProgram(conclusion || recommendation
                ? trim(request.getEducationProgram()) : null);
        document.setProlongationAvailable(conclusion && request.isProlongationAvailable());
        document.setProlongationUsed(conclusion && request.isProlongationAvailable() && request.isProlongationUsed());
        document.setProlongedGrade(document.isProlongationUsed() ? request.getProlongedGrade() : null);
        document.setProlongedAcademicYear(document.isProlongationUsed()
                ? normalizeAcademicYear(request.getProlongedAcademicYear()) : null);
        document.setIpraPresent(mse && request.isIpraPresent());
        // These legacy requisites are intentionally no longer collected.
        document.setIssuingOrganization(null);
        document.setReceivedAt(null);
        document.setResponsibleEmployee(null);
        document.setComment(null);
        document.setUpdatedAt(LocalDateTime.now());
        document = documentRepository.save(document);
        replaceCorrectionDirections(document, conclusion || recommendation
                ? request.getCorrectionDirections() : List.of());
        if (previousType == StudentSupportDocumentType.MSE_CERTIFICATE
                && document.getDocumentType() != StudentSupportDocumentType.MSE_CERTIFICATE) {
            supportStatusRepository.deleteAllBySourceDocumentId(document.getId());
        }
        if (document.getDocumentType() == StudentSupportDocumentType.MSE_CERTIFICATE) {
            synchronizeMseStatus(document);
        }
        return toView(document, LocalDate.now());
    }

    @Transactional
    public void delete(String academicYear, Long documentId) {
        StudentSupportDocument document = requireDocument(academicYear, documentId);
        supportStatusRepository.deleteAllBySourceDocumentId(document.getId());
        correctionRepository.deleteAllByDocument_Id(document.getId());
        attachmentRepository.deleteAllByDocument_Id(document.getId());
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public List<StudentSupportDocumentDtos.NosologyView> findNosologies() {
        return nosologyRepository.findAllByOrderByCodeAsc().stream()
                .filter(entry -> entry.getStudentCategory() == StudentCategory.K3)
                .map(this::toNosologyView)
                .toList();
    }

    @Transactional
    public StudentSupportDocumentDtos.NosologyView saveNosology(
            StudentSupportDocumentDtos.NosologySaveRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Нозология не передана");
        }
        String code = normalizeCatalogNosologyCode(request.getCode());
        NosologyCatalogEntry entry = request.getId() == null
                ? nosologyRepository.findByCodeIgnoreCase(code).orElseGet(NosologyCatalogEntry::new)
                : nosologyRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Нозология не найдена"));
        Optional<NosologyCatalogEntry> duplicate = nosologyRepository.findByCodeIgnoreCase(code);
        if (duplicate.isPresent() && !Objects.equals(duplicate.get().getId(), entry.getId())) {
            throw new IllegalArgumentException("Нозология " + code + " уже есть в справочнике");
        }
        entry.setCode(code);
        entry.setName(code);
        entry.setStudentCategory(StudentCategory.K3);
        entry.setActive(request.isActive());
        entry.setUpdatedAt(LocalDateTime.now());
        entry = nosologyRepository.save(entry);
        return toNosologyView(entry);
    }

    @Transactional
    public List<StudentSupportDocumentDtos.SpecialistView> findSpecialists() {
        ensureDefaultSpecialists();
        return specialistRepository.findAllByOrderByNameAsc().stream()
                .map(this::toSpecialistView)
                .toList();
    }

    @Transactional
    public StudentSupportDocumentDtos.SpecialistView saveSpecialist(
            StudentSupportDocumentDtos.SpecialistSaveRequest request
    ) {
        String name = trim(request == null ? null : request.getName());
        if (name == null) {
            throw new IllegalArgumentException("Укажите наименование специалиста");
        }
        CorrectionSpecialistCatalogEntry entry = specialistRepository.findByNameIgnoreCase(name)
                .orElseGet(CorrectionSpecialistCatalogEntry::new);
        entry.setName(name);
        entry.setActive(true);
        entry.setUpdatedAt(LocalDateTime.now());
        return toSpecialistView(specialistRepository.save(entry));
    }

    @Transactional
    public void synchronizeMseStatuses() {
        resynchronizeAllMseStatuses();
    }

    @Transactional
    public StudentSupportDocumentDtos.AttachmentView addAttachment(String academicYear,
                                                                  Long documentId,
                                                                  MultipartFile file,
                                                                  String username) {
        StudentSupportDocument document = requireDocument(academicYear, documentId);
        validateFile(file);
        try {
            StudentSupportDocumentAttachment attachment = new StudentSupportDocumentAttachment();
            attachment.setDocument(document);
            attachment.setOriginalFileName(cleanFileName(file.getOriginalFilename()));
            attachment.setContentType(Objects.toString(file.getContentType(), "application/octet-stream"));
            attachment.setFileSize(file.getSize());
            attachment.setContent(file.getBytes());
            attachment.setUploadedAt(LocalDateTime.now());
            attachment.setUploadedBy(Objects.toString(username, "SYSTEM"));
            return toAttachmentView(attachmentRepository.save(attachment));
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить прикреплённую копию", exception);
        }
    }

    @Transactional(readOnly = true)
    public StudentSupportDocumentDtos.AttachmentDownload downloadAttachment(String academicYear,
                                                                            Long documentId,
                                                                            Long attachmentId) {
        requireDocument(academicYear, documentId);
        StudentSupportDocumentAttachment attachment = attachmentRepository.findById(attachmentId)
                .filter(item -> Objects.equals(item.getDocument().getId(), documentId))
                .orElseThrow(() -> new IllegalArgumentException("Прикреплённая копия не найдена"));
        return new StudentSupportDocumentDtos.AttachmentDownload(
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getContent()
        );
    }

    @Transactional
    public void deleteAttachment(String academicYear, Long documentId, Long attachmentId) {
        requireDocument(academicYear, documentId);
        StudentSupportDocumentAttachment attachment = attachmentRepository.findById(attachmentId)
                .filter(item -> Objects.equals(item.getDocument().getId(), documentId))
                .orElseThrow(() -> new IllegalArgumentException("Прикреплённая копия не найдена"));
        attachmentRepository.delete(attachment);
    }

    private StudentSupportDocument requireDocument(String academicYear, Long documentId) {
        return documentRepository.findById(documentId)
                .filter(document -> Objects.equals(document.getAcademicYear(), academicYear))
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден"));
    }

    private StudentSupportDocumentDtos.View toView(StudentSupportDocument document, LocalDate asOfDate) {
        StudentSupportDocumentDtos.View view = new StudentSupportDocumentDtos.View();
        view.setId(document.getId());
        view.setStudentId(document.getStudent().getId());
        view.setStudentFullName(document.getStudent().getCurrentFullName());
        view.setClassName(currentClass(document.getStudent().getId(), document.getAcademicYear(), asOfDate));
        view.setDocumentType(document.getDocumentType());
        view.setAcceptedForm(document.getAcceptedForm());
        view.setDocumentNumber(document.getDocumentNumber());
        view.setIssueDate(document.getIssueDate());
        view.setValidFrom(document.getValidFrom());
        view.setValidTo(document.getValidTo());
        view.setNosologyCode(document.getDocumentType() == StudentSupportDocumentType.CPMPC_CONCLUSION
                ? document.getNosologyCode() : null);
        view.setDerivedCategory(document.getDocumentType() == StudentSupportDocumentType.MSE_CERTIFICATE
                ? StudentCategory.K2 : null);
        view.setEducationStage(document.getEducationStage());
        view.setEducationProgram(document.getEducationProgram());
        view.setProlongationAvailable(document.isProlongationAvailable());
        view.setProlongationUsed(document.isProlongationUsed());
        view.setProlongedGrade(document.getProlongedGrade());
        view.setProlongedAcademicYear(document.getProlongedAcademicYear());
        view.setIpraPresent(document.getDocumentType() == StudentSupportDocumentType.MSE_CERTIFICATE
                && document.isIpraPresent());
        view.setCorrectionDirections(document.getId() == null ? List.of() : correctionRepository
                .findAllByDocument_IdOrderBySpecialist_NameAsc(document.getId()).stream()
                .map(this::toCorrectionDirectionView)
                .toList());
        view.setIssuingOrganization(document.getIssuingOrganization());
        view.setReceivedAt(document.getReceivedAt());
        view.setResponsibleEmployee(document.getResponsibleEmployee());
        view.setComment(document.getComment());
        view.setValidityStatus(validityStatus(document, asOfDate));
        view.setAttachments(document.getId() == null ? List.of() : attachmentRepository
                .findAllByDocument_IdOrderByUploadedAtAsc(document.getId()).stream()
                .map(this::toAttachmentView)
                .toList());
        return view;
    }

    private StudentSupportDocumentDtos.AttachmentView toAttachmentView(
            StudentSupportDocumentAttachment attachment
    ) {
        StudentSupportDocumentDtos.AttachmentView view = new StudentSupportDocumentDtos.AttachmentView();
        view.setId(attachment.getId());
        view.setFileName(attachment.getOriginalFileName());
        view.setContentType(attachment.getContentType());
        view.setFileSize(attachment.getFileSize());
        view.setUploadedAt(attachment.getUploadedAt());
        view.setUploadedBy(attachment.getUploadedBy());
        return view;
    }

    private StudentSupportDocumentDtos.CorrectionDirectionView toCorrectionDirectionView(
            StudentSupportDocumentCorrection correction
    ) {
        StudentSupportDocumentDtos.CorrectionDirectionView view =
                new StudentSupportDocumentDtos.CorrectionDirectionView();
        view.setId(correction.getId());
        view.setSpecialistId(correction.getSpecialist().getId());
        view.setSpecialistName(correction.getSpecialist().getName());
        view.setTasks(correction.getTasks());
        return view;
    }

    private StudentSupportDocumentDtos.NosologyView toNosologyView(NosologyCatalogEntry entry) {
        StudentSupportDocumentDtos.NosologyView view = new StudentSupportDocumentDtos.NosologyView();
        view.setId(entry.getId());
        view.setCode(entry.getCode());
        view.setActive(entry.isActive());
        return view;
    }

    private StudentSupportDocumentDtos.SpecialistView toSpecialistView(
            CorrectionSpecialistCatalogEntry entry
    ) {
        StudentSupportDocumentDtos.SpecialistView view = new StudentSupportDocumentDtos.SpecialistView();
        view.setId(entry.getId());
        view.setName(entry.getName());
        view.setActive(entry.isActive());
        view.setBuiltIn(entry.isBuiltIn());
        return view;
    }

    private void validateDocument(String academicYear,
                                  Long studentId,
                                  StudentSupportDocumentDtos.SaveRequest request) {
        validateDates(request.getValidFrom(), request.getValidTo());
        if (request.getDocumentType() == StudentSupportDocumentType.CPMPC_RECOMMENDATION) {
            if (request.getEducationStage() == null) {
                throw new IllegalArgumentException("Выберите уровень образования");
            }
            String educationProgram = trim(request.getEducationProgram());
            if (educationProgram == null) {
                throw new IllegalArgumentException("Выберите образовательную программу");
            }
            if (!RECOMMENDATION_PROGRAMS.contains(educationProgram)) {
                throw new IllegalArgumentException("Выберите образовательную программу из списка");
            }
            return;
        }
        if (request.getValidFrom() == null || request.getValidTo() == null) {
            throw new IllegalArgumentException("Укажите дату установления и дату окончания справки");
        }
        if (request.getDocumentType() == StudentSupportDocumentType.MSE_CERTIFICATE) {
            StudentSupportDocumentForm acceptedForm = Objects.requireNonNullElse(
                    request.getAcceptedForm(), StudentSupportDocumentForm.COPY);
            if (acceptedForm != StudentSupportDocumentForm.COPY) {
                throw new IllegalArgumentException("Справка МСЭ принимается только как копия");
            }
            return;
        }
        String nosologyCode = normalizeFullNosologyCode(request.getNosologyCode());
        StudentSupportDocumentForm acceptedForm = Objects.requireNonNullElse(
                request.getAcceptedForm(), StudentSupportDocumentForm.ORIGINAL);
        if (acceptedForm != StudentSupportDocumentForm.ORIGINAL
                && acceptedForm != StudentSupportDocumentForm.ELECTRONIC_COPY) {
            throw new IllegalArgumentException("Заключение ЦМПК принимается как оригинал или электронная копия");
        }
        if (nosologyCode == null) {
            throw new IllegalArgumentException("Укажите нозологию для заключения ЦМПК");
        }
        if (trim(request.getDocumentNumber()) == null) {
            throw new IllegalArgumentException("Укажите номер заключения ЦМПК");
        }
        if (request.getEducationStage() == null) {
            throw new IllegalArgumentException("Выберите уровень образования");
        }
        String educationProgram = trim(request.getEducationProgram());
        if (educationProgram == null) {
            throw new IllegalArgumentException("Укажите образовательную программу");
        }
        if (educationProgram.length() > 2000) {
            throw new IllegalArgumentException("Образовательная программа не должна превышать 2000 символов");
        }
        if (request.isProlongationUsed() && !request.isProlongationAvailable()) {
            throw new IllegalArgumentException("Использование пролонгирования возможно только при наличии такого права");
        }
        if (request.isProlongationUsed()) {
            if (request.getProlongedGrade() == null
                    || request.getProlongedGrade() < 1
                    || request.getProlongedGrade() > 11) {
                throw new IllegalArgumentException("Выберите параллель, которую пролонгировали");
            }
            normalizeAcademicYearRequired(request.getProlongedAcademicYear());
        }
        validateCpmPcEndDate(academicYear, studentId, request);
    }

    private void validateCpmPcEndDate(String academicYear,
                                      Long studentId,
                                      StudentSupportDocumentDtos.SaveRequest request) {
        if (request.getEducationStage() == SupportEducationStage.DO
                || request.isProlongationAvailable()) {
            return;
        }
        LocalDate validTo = request.getValidTo();
        if (validTo.getMonthValue() != 8 || validTo.getDayOfMonth() != 31) {
            throw new IllegalArgumentException("Срок заключения ЦМПК должен оканчиваться 31.08");
        }
        Integer currentGrade = currentGrade(studentId, academicYear, request.getValidFrom());
        if (currentGrade == null) {
            return;
        }
        int terminalGrade = switch (request.getEducationStage()) {
            case NOO -> 4;
            case OOO -> 9;
            case SOO -> 11;
            case DO -> currentGrade;
        };
        if (currentGrade > terminalGrade) {
            throw new IllegalArgumentException("Выбранный уровень образования не соответствует классу ребёнка");
        }
        int expectedYear = academicYearEnd(academicYear) + terminalGrade - currentGrade;
        LocalDate expected = LocalDate.of(expectedYear, 8, 31);
        if (!expected.equals(validTo)) {
            throw new IllegalArgumentException("Для выбранного уровня и текущего класса дата окончания должна быть "
                    + expected + ". При возможности пролонгирования укажите «Да» — тогда дата может отличаться");
        }
    }

    private Integer currentGrade(Long studentId, String academicYear, LocalDate date) {
        return enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear)
                .stream()
                .filter(enrollment -> contains(enrollment.getValidFrom(), enrollment.getValidTo(), date))
                .map(StudentClassEnrollment::getClassName)
                .map(this::extractGrade)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> enrollmentRepository
                        .findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear).stream()
                        .map(StudentClassEnrollment::getClassName)
                        .map(this::extractGrade)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    private Integer extractGrade(String className) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*(\\d{1,2})")
                .matcher(Objects.toString(className, ""));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private int academicYearEnd(String academicYear) {
        String normalized = Objects.toString(academicYear, "");
        if (!normalized.matches("\\d{4}/\\d{4}")) {
            throw new IllegalArgumentException("Некорректный учебный год: " + academicYear);
        }
        return Integer.parseInt(normalized.substring(5));
    }

    private void replaceCorrectionDirections(
            StudentSupportDocument document,
            List<StudentSupportDocumentDtos.CorrectionDirectionRequest> requests
    ) {
        correctionRepository.deleteAllByDocument_Id(document.getId());
        if (requests == null || requests.isEmpty()) {
            return;
        }
        Set<Long> specialistIds = new HashSet<>();
        for (StudentSupportDocumentDtos.CorrectionDirectionRequest request : requests) {
            if (request == null || request.getSpecialistId() == null) {
                throw new IllegalArgumentException("В направлении коррекционной работы выберите специалиста");
            }
            if (!specialistIds.add(request.getSpecialistId())) {
                throw new IllegalArgumentException("Один специалист не может быть добавлен дважды");
            }
            String tasks = trim(request.getTasks());
            if (tasks == null) {
                throw new IllegalArgumentException("Укажите задачи для выбранного специалиста");
            }
            CorrectionSpecialistCatalogEntry specialist = specialistRepository.findById(request.getSpecialistId())
                    .filter(CorrectionSpecialistCatalogEntry::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Специалист не найден или отключён"));
            StudentSupportDocumentCorrection correction = new StudentSupportDocumentCorrection();
            correction.setDocument(document);
            correction.setSpecialist(specialist);
            correction.setTasks(tasks);
            correction.setUpdatedAt(LocalDateTime.now());
            correctionRepository.save(correction);
        }
    }

    private void synchronizeMseStatus(StudentSupportDocument document) {
        StudentSupportStatus status = supportStatusRepository.findBySourceDocumentId(document.getId())
                .orElseGet(StudentSupportStatus::new);
        status.setStudent(document.getStudent());
        status.setAcademicYear(document.getAcademicYear());
        status.setCategory(StudentCategory.K2);
        status.setNosology(null);
        status.setNosologyCodeSnapshot(null);
        status.setAoopVariantSnapshot(null);
        status.setValidFrom(document.getValidFrom());
        status.setValidTo(document.getValidTo());
        status.setSourceDocumentId(document.getId());
        status.setComment("Автоматически по справке МСЭ");
        status.setUpdatedAt(LocalDateTime.now());
        supportStatusRepository.save(status);
    }

    private void resynchronizeAllMseStatuses() {
        documentRepository.findAllByDocumentType(StudentSupportDocumentType.MSE_CERTIFICATE)
                .stream()
                .filter(document -> document.getStudent() != null && document.getValidFrom() != null)
                .forEach(this::synchronizeMseStatus);
    }

    private String normalizeFullNosologyCode(String value) {
        String normalized = Objects.toString(value, "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("I", "И")
                .replace("O", "О")
                .replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            return null;
        }
        if (!normalized.matches("[ИО][0-9]\\.[0-9]")) {
            throw new IllegalArgumentException("Код нозологии должен иметь вид И4.1 или О5.2");
        }
        return normalized;
    }

    private String normalizeCatalogNosologyCode(String value) {
        String normalized = Objects.toString(value, "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("I", "И")
                .replace("O", "О")
                .replaceAll("\\s+", "");
        if (normalized.matches("[ИО][0-9]\\.[0-9]")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[0-9]\\.[0-9]")) {
            throw new IllegalArgumentException("Код К3 должен иметь вид 4.1, 4.2 или 6.1");
        }
        return normalized;
    }

    private String normalizeAcademicYear(String value) {
        String normalized = trim(value);
        return normalized != null && normalized.matches("\\d{4}/\\d{4}") ? normalized : null;
    }

    private String normalizeAcademicYearRequired(String value) {
        String normalized = normalizeAcademicYear(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Выберите учебный год пролонгирования");
        }
        return normalized;
    }

    private void ensureDefaultSpecialists() {
        for (String name : DEFAULT_SPECIALISTS) {
            CorrectionSpecialistCatalogEntry entry = specialistRepository.findByNameIgnoreCase(name)
                    .orElseGet(CorrectionSpecialistCatalogEntry::new);
            entry.setName(name);
            entry.setActive(true);
            entry.setBuiltIn(true);
            entry.setUpdatedAt(LocalDateTime.now());
            specialistRepository.save(entry);
        }
    }

    private String currentClass(Long studentId, String academicYear, LocalDate date) {
        return enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(
                        studentId,
                        academicYear
                ).stream()
                .filter(enrollment -> contains(enrollment.getValidFrom(), enrollment.getValidTo(), date))
                .map(StudentClassEnrollment::getClassName)
                .findFirst()
                .orElse("");
    }

    private String validityStatus(StudentSupportDocument document, LocalDate date) {
        if (document.getDocumentType() == StudentSupportDocumentType.CPMPC_RECOMMENDATION) {
            return "АКТУАЛЬНО";
        }
        if (document.getValidFrom() != null && date.isBefore(document.getValidFrom())) {
            return "ОЖИДАЕТ НАЧАЛА";
        }
        if (document.getValidTo() == null) {
            return "БЕЗ СРОКА";
        }
        if (date.isAfter(document.getValidTo())) {
            return "ИСТЁК";
        }
        if (!document.getValidTo().isAfter(date.plusDays(30))) {
            return "ИСТЕКАЕТ";
        }
        return "ДЕЙСТВУЕТ";
    }

    private boolean contains(LocalDate from, LocalDate to, LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите файл");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("Размер одного файла не должен превышать 15 МБ");
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Разрешены PDF, изображения, Word и Excel");
        }
    }

    private String cleanFileName(String value) {
        String fileName = Objects.toString(value, "document").replace('\\', '/');
        int separator = fileName.lastIndexOf('/');
        return (separator >= 0 ? fileName.substring(separator + 1) : fileName)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
    }

    private String trim(String value) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
