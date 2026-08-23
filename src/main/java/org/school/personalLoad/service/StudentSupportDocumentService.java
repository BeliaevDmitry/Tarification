package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.StudentSupportDocumentDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StudentSupportDocumentService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
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
    private static final Map<SupportEducationStage, List<EducationProgram>> CONCLUSION_PROGRAMS = Map.of(
            SupportEducationStage.DO, List.of(
                    program("АООП для диагностических групп детей раннего и дошкольного возраста",
                            "https://drive.google.com/file/d/1KvPbXj7cPPoZ5C5f6rckPWQ2lJuGIctU/view?usp=sharing"),
                    program("АООП дошкольного образования для глухих детей",
                            "https://drive.google.com/file/d/1EZR-izIGiknqCU27yBbqjgfmKawJIrr0/view?usp=sharing"),
                    program("АООП дошкольного образования для детей, перенесших кохлеарную имплантацию",
                            "https://drive.google.com/file/d/1Ts3rnnlJFCidUkSBPnM_LRoq9VoR5wxY/view?usp=sharing"),
                    program("АООП дошкольного образования для слабослышащих и позднооглохших детей",
                            "https://drive.google.com/file/d/1waosTuRD7gMz16hiPiSMHCm9e5kpKHTZ/view?usp=sharing"),
                    program("АООП дошкольного образования для слабовидящих детей",
                            "https://drive.google.com/file/d/1W6InP-TWeQxYa4wryvzrFv61xP5bCj_x/view?usp=sharing"),
                    program("АООП дошкольного образования для слепых детей",
                            "https://drive.google.com/file/d/1zT32HxF6FgyjmSLAmobJEg-OLCv3MoDr/view?usp=sharing"),
                    program("АООП дошкольного образования для детей с косоглазием и амблиопией",
                            "https://drive.google.com/file/d/1KUiz4pyJMwv9gVJOhZ7jnRVnMzXWUQOT/view?usp=sharing"),
                    program("АООП дошкольного образования для детей с ТНР",
                            "https://drive.google.com/file/d/1MVyqpp8EIjaVpGzn8k5t7atMdITVsMOD/view?usp=sharing"),
                    program("АООП дошкольного образования для детей с НОДА",
                            "https://drive.google.com/file/d/1y5ZGyyKTsb6AaR_QolA7_AgA0_xqxyyF/view?usp=sharing"),
                    program("АООП дошкольного образования для детей с ЗПР",
                            "https://drive.google.com/file/d/1r1sslTRK3aoBXVSR5SlpC7dUxyHX8DOJ/view?usp=sharing"),
                    program("АООП дошкольного образования для детей с УО",
                            "https://drive.google.com/file/d/1hN3VMsAaGfmMTMPlTjuQ6nJaKaD1nN7G/view?usp=sharing"),
                    program("АООП дошкольного образования для детей с ТМНР",
                            "https://drive.google.com/file/d/1ru45az3V5XYr6M_sRceNg7xcXWKyZWS0/view?usp=sharing")
            ),
            SupportEducationStage.NOO, List.of(
                    program("АООП НОО глухих обучающихся",
                            "https://drive.google.com/file/d/12C1W6mrhShDFcewcJ5leRWSKPmg21jTj/view?usp=sharing"),
                    program("АООП НОО слабослышащих и позднооглохших обучающихся",
                            "https://drive.google.com/file/d/1MQedsLcGxtSf8uq-clCziE-yWzuecQVi/view?usp=sharing"),
                    program("АООП НОО слепых обучающихся",
                            "https://drive.google.com/file/d/1VnB6SX8CJB-6Drz7JOJdBswslqj_wHdi/view?usp=sharing"),
                    program("АООП НОО слабовидящих обучающихся",
                            "https://drive.google.com/file/d/1EtxbecEKbzIUOgFxXmQqsTOZ1HDqFvtF/view?usp=sharing"),
                    program("АООП НОО обучающихся с ТНР",
                            "https://drive.google.com/file/d/1r1d7wzO5n3OF-cxKPDUJ3uizBtFfSTri/view?usp=sharing"),
                    program("АООП НОО обучающихся с НОДА",
                            "https://drive.google.com/file/d/1V5Twhhhkj2QRtahtblJjqAFxav8W6qY7/view?usp=sharing"),
                    program("АООП НОО обучающихся с ЗПР",
                            "https://drive.google.com/file/d/10dXzHdSGGTHegr-g2PzlzwncB457ILg1/view?usp=sharing"),
                    program("АООП НОО обучающихся с РАС",
                            "https://drive.google.com/file/d/1n8Dqs1q_zMaeOZu2a6DsSLGfXgbWTCHk/view?usp=sharing"),
                    program("АООП образования обучающихся с умственной отсталостью (интеллектуальными нарушениями)",
                            "https://drive.google.com/file/d/1JLsNfepZGJxkG5rnlfMI0oYzGc7Jtdfw/view?usp=sharing")
            ),
            SupportEducationStage.OOO, List.of(
                    program("АООП ООО обучающихся с нарушениями слуха",
                            "https://drive.google.com/file/d/1yA7XwosND7D_AXFe0zmXJYckgNsgQQZg/view?usp=sharing"),
                    program("АООП ООО слепых обучающихся",
                            "https://drive.google.com/file/d/1fY52-wVC537fxCunZ0azYHbcmV__lmpc/view?usp=sharing"),
                    program("АООП ООО слабовидящих обучающихся",
                            "https://drive.google.com/file/d/1YtTM-qXboPfqcRcGXGJT0-lA8u7W8tUh/view?usp=sharing"),
                    program("АООП ООО обучающихся с ТНР",
                            "https://drive.google.com/file/d/13tUqc3zQU9Rxb-Pbjhxdf974H93_70vw/view?usp=sharing"),
                    program("АООП ООО обучающихся с НОДА",
                            "https://drive.google.com/file/d/1uHReclFW-3BZQXikbrF7tw0CnNQ1mwnz/view?usp=sharing"),
                    program("АООП ООО обучающихся с ЗПР",
                            "https://drive.google.com/file/d/13P56kJDDRwgYYntWSMS4dDLgwW8Ukeif/view?usp=sharing"),
                    program("АООП ООО обучающихся с РАС",
                            "https://drive.google.com/file/d/1kAe9k9ESzgjEYnhU6fMoHM0rOzTTSs5C/view?usp=sharing")
            ),
            SupportEducationStage.SOO, List.of()
    );

    private final StudentSupportDocumentRepository documentRepository;
    private final StudentSupportDocumentAttachmentRepository attachmentRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentSupportStatusRepository supportStatusRepository;
    private final NosologyCatalogEntryRepository nosologyRepository;
    private final CorrectionSpecialistCatalogEntryRepository specialistRepository;
    private final StudentSupportDocumentCorrectionRepository correctionRepository;
    private final CorrectionStudentAssignmentRepository assignmentRepository;

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
        validateDossierCombination(academicYear, student.getId(), document.getId(), request.getDocumentType());
        validateDocument(academicYear, student, request);
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
        document.setValidTo(request.getValidTo());
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
        cleanupInvalidAssignments(academicYear, student.getId());
        return toView(document, LocalDate.now());
    }

    private void validateDossierCombination(String academicYear,
                                            Long studentId,
                                            Long documentId,
                                            StudentSupportDocumentType requestedType) {
        long ignoredId = documentId == null ? -1L : documentId;
        if (documentRepository.existsByStudent_IdAndAcademicYearAndDocumentTypeAndIdNot(
                studentId, academicYear, requestedType, ignoredId)) {
            throw new IllegalArgumentException("У ребёнка уже есть документ этого вида. Откройте его через кнопку «Изменить» в реестре.");
        }
        StudentSupportDocumentType incompatible = requestedType == StudentSupportDocumentType.CPMPC_CONCLUSION
                ? StudentSupportDocumentType.CPMPC_RECOMMENDATION
                : requestedType == StudentSupportDocumentType.CPMPC_RECOMMENDATION
                ? StudentSupportDocumentType.CPMPC_CONCLUSION
                : null;
        if (incompatible != null && documentRepository.existsByStudent_IdAndAcademicYearAndDocumentTypeAndIdNot(
                studentId, academicYear, incompatible, ignoredId)) {
            throw new IllegalArgumentException("У ребёнка не могут одновременно действовать заключение и рекомендация ЦМПК.");
        }
    }

    @Transactional
    public void delete(String academicYear, Long documentId) {
        StudentSupportDocument document = requireDocument(academicYear, documentId);
        supportStatusRepository.deleteAllBySourceDocumentId(document.getId());
        correctionRepository.deleteAllByDocument_Id(document.getId());
        attachmentRepository.deleteAllByDocument_Id(document.getId());
        documentRepository.delete(document);
        documentRepository.flush();
        cleanupInvalidAssignments(academicYear, document.getStudent().getId());
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

    @Transactional(readOnly = true)
    public StudentSupportDocumentDtos.EducationDefaultsView educationDefaults(
            String academicYear,
            Long studentId,
            StudentSupportDocumentType documentType,
            boolean prolongationAvailable,
            boolean prolongationUsed
    ) {
        return educationDefaults(academicYear, studentId, documentType,
                prolongationAvailable, prolongationUsed, null);
    }

    @Transactional(readOnly = true)
    public StudentSupportDocumentDtos.EducationDefaultsView educationDefaults(
            String academicYear,
            Long studentId,
            StudentSupportDocumentType documentType,
            boolean prolongationAvailable,
            boolean prolongationUsed,
            String nosologyCode
    ) {
        if (documentType != StudentSupportDocumentType.CPMPC_CONCLUSION
                && documentType != StudentSupportDocumentType.CPMPC_RECOMMENDATION) {
            throw new IllegalArgumentException("Автоматический срок рассчитывается только для документов ЦМПК");
        }
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        boolean throughNinthGrade = documentType == StudentSupportDocumentType.CPMPC_CONCLUSION
                && isNinthGradeNosology(nosologyCode);
        EducationDeadline deadline = educationDeadline(
                academicYear,
                student,
                documentType == StudentSupportDocumentType.CPMPC_CONCLUSION
                        && prolongationAvailable
                        && !prolongationUsed,
                true,
                throughNinthGrade
        );
        StudentSupportDocumentDtos.EducationDefaultsView view =
                new StudentSupportDocumentDtos.EducationDefaultsView();
        view.setEducationStage(deadline.stage());
        view.setValidTo(deadline.validTo());
        view.setManualCheckRequired(deadline.stage() == SupportEducationStage.DO);
        view.setEducationPrograms(documentType == StudentSupportDocumentType.CPMPC_CONCLUSION
                ? conclusionPrograms(deadline.stage()).stream()
                .map(this::toEducationProgramView)
                .toList()
                : List.of());
        String date = deadline.validTo().format(DISPLAY_DATE);
        if (throughNinthGrade) {
            view.setMessage("Нозология " + normalizeFullNosologyCode(nosologyCode)
                    + ": дата окончания " + date + " рассчитана до окончания 9 класса"
                    + (prolongationAvailable && !prolongationUsed
                    ? " с учётом одного дополнительного года неиспользованной пролонгации." : "."));
        } else if (deadline.stage() == SupportEducationStage.DO) {
            view.setMessage("ДО: дата окончания " + date
                    + " рассчитана по дате рождения (31.08 года исполнения 7 лет). Проверьте её вручную.");
        } else if (documentType == StudentSupportDocumentType.CPMPC_CONCLUSION
                && prolongationAvailable
                && !prolongationUsed) {
            view.setMessage("Уровень " + deadline.stage() + " определён по классу. Дата окончания "
                    + date + " включает один дополнительный год неиспользованной пролонгации.");
        } else {
            view.setMessage("Уровень " + deadline.stage() + " и дата окончания " + date
                    + " определены по текущему классу ребёнка.");
        }
        return view;
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
                                  StudentProfile student,
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
            if (request.getValidTo() == null) {
                throw new IllegalArgumentException("Не удалось рассчитать дату окончания рекомендации ЦМПК");
            }
            validateCpmPcEndDate(academicYear, student, request);
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
        boolean listedForStage = conclusionPrograms(request.getEducationStage()).stream()
                .anyMatch(program -> program.name().equals(educationProgram));
        if (!request.isEducationProgramCustom() && !listedForStage) {
            throw new IllegalArgumentException(
                    "Выберите образовательную программу из списка для уровня " + request.getEducationStage()
            );
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
        validateCpmPcEndDate(academicYear, student, request);
    }

    private void validateCpmPcEndDate(String academicYear,
                                      StudentProfile student,
                                      StudentSupportDocumentDtos.SaveRequest request) {
        LocalDate validTo = request.getValidTo();
        if (validTo.getMonthValue() != 8 || validTo.getDayOfMonth() != 31) {
            throw new IllegalArgumentException("Срок документа ЦМПК должен оканчиваться 31.08");
        }
        EducationDeadline expected = educationDeadline(
                academicYear,
                student,
                request.getDocumentType() == StudentSupportDocumentType.CPMPC_CONCLUSION
                        && request.isProlongationAvailable()
                        && !request.isProlongationUsed(),
                false,
                request.getDocumentType() == StudentSupportDocumentType.CPMPC_CONCLUSION
                        && isNinthGradeNosology(request.getNosologyCode())
        );
        if (expected == null) {
            return;
        }
        if (request.getEducationStage() != expected.stage()) {
            throw new IllegalArgumentException("Выбранный уровень образования не соответствует классу ребёнка");
        }
        if (!expected.validTo().equals(validTo)) {
            throw new IllegalArgumentException("Для текущего класса дата окончания должна быть "
                    + expected.validTo());
        }
    }

    private EducationDeadline educationDeadline(String academicYear,
                                                 StudentProfile student,
                                                 boolean addUnusedProlongationYear,
                                                 boolean requireIdentityData,
                                                 boolean throughNinthGrade) {
        Integer grade = currentGrade(student.getId(), academicYear, null);
        SupportEducationStage stage;
        int expectedYear;
        if (grade == null) {
            if (student.getBirthDate() == null) {
                if (requireIdentityData) {
                    throw new IllegalArgumentException(
                            "Для расчёта срока ДО в карточке ребёнка должна быть дата рождения"
                    );
                }
                return null;
            }
            stage = SupportEducationStage.DO;
            expectedYear = student.getBirthDate().getYear() + 7;
        } else if (grade >= 1 && grade <= 4) {
            stage = SupportEducationStage.NOO;
            expectedYear = academicYearEnd(academicYear) + 4 - grade;
        } else if (grade >= 5 && grade <= 9) {
            stage = SupportEducationStage.OOO;
            expectedYear = academicYearEnd(academicYear) + 9 - grade;
        } else if (grade >= 10 && grade <= 11) {
            stage = SupportEducationStage.SOO;
            expectedYear = academicYearEnd(academicYear) + 11 - grade;
        } else {
            throw new IllegalArgumentException("Класс ребёнка не позволяет определить уровень образования");
        }
        if (throughNinthGrade) {
            expectedYear = grade == null
                    ? student.getBirthDate().getYear() + 15
                    : academicYearEnd(academicYear) + 9 - grade;
        }
        if (addUnusedProlongationYear) {
            expectedYear++;
        }
        return new EducationDeadline(stage, LocalDate.of(expectedYear, 8, 31));
    }

    private boolean isNinthGradeNosology(String value) {
        String normalized = normalizeFullNosologyCode(value);
        return normalized != null && normalized.matches("^[ИО]9\\.[0-9]$");
    }

    private static EducationProgram program(String name, String sourceUrl) {
        return new EducationProgram(name, sourceUrl);
    }

    private static List<EducationProgram> conclusionPrograms(SupportEducationStage stage) {
        return stage == null ? List.of() : CONCLUSION_PROGRAMS.getOrDefault(stage, List.of());
    }

    private StudentSupportDocumentDtos.EducationProgramView toEducationProgramView(EducationProgram program) {
        StudentSupportDocumentDtos.EducationProgramView view =
                new StudentSupportDocumentDtos.EducationProgramView();
        view.setName(program.name());
        view.setSourceUrl(program.sourceUrl());
        return view;
    }

    private Integer currentGrade(Long studentId, String academicYear, LocalDate date) {
        return enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear)
                .stream()
                .filter(enrollment -> date == null
                        || contains(enrollment.getValidFrom(), enrollment.getValidTo(), date))
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
        // Derived deletes are queued in the persistence context. Execute them before
        // inserting the replacement rows, otherwise an unchanged specialist collides
        // with the unique (document_id, specialist_id) constraint during editing.
        correctionRepository.flush();
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

    private void cleanupInvalidAssignments(String academicYear, Long studentId) {
        assignmentRepository.findAllByAcademicYearAndStudent_Id(academicYear, studentId).stream()
                .filter(assignment -> !correctionRepository.existsStudentNeed(
                        academicYear, studentId, assignment.getSpecialist().getId(), List.of(
                                StudentSupportDocumentType.CPMPC_CONCLUSION,
                                StudentSupportDocumentType.CPMPC_RECOMMENDATION)))
                .forEach(assignmentRepository::delete);
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

    private String trim(String value) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record EducationProgram(String name, String sourceUrl) {
    }

    private record EducationDeadline(SupportEducationStage stage, LocalDate validTo) {
    }
}
