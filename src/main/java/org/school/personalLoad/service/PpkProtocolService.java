package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.PpkProtocolRepository;
import org.school.personalLoad.repository.OvzWorkflowStageRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentProfileRepository;
import org.school.personalLoad.repository.StudentSupportDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PpkProtocolService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final PpkProtocolRepository repository;
    private final StudentProfileRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final OvzWorkflowStageRepository workflowStageRepository;
    private final StudentSupportDocumentRepository supportDocumentRepository;
    private final PpkProtocolSettingsService settingsService;

    @Transactional(readOnly = true)
    public List<OvzDtos.PpkProtocolView> findAll(String academicYear) {
        return repository.findAllByAcademicYearOrderByMeetingDateDescSequenceNumberDesc(academicYear).stream()
                .map(this::toView).toList();
    }

    @Transactional
    public OvzDtos.PpkProtocolDefaults defaults(String academicYear, Long studentId) {
        OvzDtos.PpkProtocolSettingsView settings = settingsService.get();
        OvzDtos.PpkProtocolDefaults result = new OvzDtos.PpkProtocolDefaults();
        result.setChairEmployeeId(settings.getChairEmployeeId());
        result.setSecretaryEmployeeId(settings.getSecretaryEmployeeId());
        result.setAttendeeEmployeeIds(settings.getAttendeeEmployeeIds());
        result.setAttendeeMembers(settings.getAttendeeMembers());
        result.setChairPosition(settings.getChairPosition());
        result.setSecretaryPosition(settings.getSecretaryPosition());
        result.setChairName(commissionLine(settings.getChairName(), settings.getChairPosition()));
        result.setSecretaryName(commissionLine(settings.getSecretaryName(), settings.getSecretaryPosition()));
        result.setAttendees(settings.getAttendees());
        result.setStudentId(studentId);
        result.setProtocolType(PpkProtocolType.APPOINTMENT);
        if (studentId == null) {
            result.setInvitedRepresentative("");
            result.setAgenda("");
            result.setMeetingNotes("");
            result.setDecisionText("");
            result.setMessage("Общий протокол не привязан к ребёнку. Заполнен только стандартный состав комиссии.");
            return result;
        }

        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        StudentSupportDocument conclusion = supportDocumentRepository
                .findFirstByStudent_IdAndAcademicYearAndDocumentType(
                        studentId, academicYear, StudentSupportDocumentType.CPMPC_CONCLUSION)
                .orElse(null);
        StudentSupportDocument recommendation = conclusion == null ? supportDocumentRepository
                .findFirstByStudent_IdAndAcademicYearAndDocumentType(
                        studentId, academicYear, StudentSupportDocumentType.CPMPC_RECOMMENDATION)
                .orElse(null) : null;
        var fioCases = RussianNameCases.derive(student.getCurrentFullName());
        String fio = fioCases.nominative();
        String fioGenitive = fioCases.genitive();
        String fioDative = fioCases.dative();
        boolean female = RussianNameCases.isFemale(fio);
        String learnerGenitive = female ? "обучающейся" : "обучающегося";
        String learnerDative = female ? "обучающейся" : "обучающемуся";
        String className = currentClass(studentId, academicYear);
        String variant = conclusion == null ? null : aoopVariant(conclusion.getNosologyCode());
        String conclusionNumber = conclusion == null ? null : trim(conclusion.getDocumentNumber());
        String schoolYear = displayAcademicYear(academicYear);
        String representativeName = orDefault(trim(student.getRepresentativeName()), "");
        result.setAoopVariant(variant);
        result.setConclusionNumber(conclusionNumber);
        result.setInvitedRepresentative("");
        result.setRepresentativeName(representativeName);
        result.setRepresentativeSignatureName(representativeName);
        if (recommendation != null) {
            result.setProtocolType(PpkProtocolType.RECOMMENDATION_SUPPORT);
            result.setAgenda(defaultRecommendationAgenda(fioGenitive, learnerGenitive, schoolYear));
            result.setMeetingNotes(defaultRecommendationNotes(schoolYear, learnerGenitive));
            result.setDecisionText(defaultRecommendationDecision(
                    fioGenitive, learnerGenitive, className, schoolYear));
            result.setMessage("Применён шаблон протокола по рекомендации ЦМПК: психолого-педагогическое сопровождение без назначения специальных условий и ИОМ.");
            return result;
        }
        result.setAgenda(defaultAgenda(fioGenitive, fioDative, learnerGenitive, learnerDative, variant, schoolYear));
        result.setMeetingNotes(defaultNotes(conclusionNumber, schoolYear, learnerGenitive));
        result.setDecisionText(defaultDecision(
                fioGenitive, fioDative, learnerGenitive, learnerDative, className, variant, schoolYear));
        if (conclusion == null) {
            result.setMessage("У ребёнка не найдены заключение или рекомендация ЦМПК: номер и вариант АООП оставлены для ручного заполнения.");
        } else if (variant == null) {
            result.setMessage("В заключении не заполнен код нозологии: вариант АООП оставлен для ручного заполнения.");
        } else if (conclusionNumber == null) {
            result.setMessage("В заключении не заполнен номер ЦПМПК: в ходе заседания оставлено поле №_____.");
        } else {
            result.setMessage("Данные ребёнка, заключения ЦМПК и стандартной комиссии подставлены автоматически.");
        }
        return result;
    }

    @Transactional
    public synchronized OvzDtos.PpkProtocolView save(String academicYear, OvzDtos.PpkProtocolSaveRequest request) {
        if (request == null) throw new IllegalArgumentException("Данные протокола не переданы");
        LocalDate meetingDate = Objects.requireNonNullElse(request.getMeetingDate(), LocalDate.now());
        PpkProtocol protocol = request.getId() == null ? new PpkProtocol() : repository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Протокол ППк не найден"));
        Long previousStudentId = protocol.getStudent() == null ? null : protocol.getStudent().getId();
        String previousAcademicYear = protocol.getAcademicYear();
        PpkProtocolType previousType = protocol.getProtocolType();
        if (protocol.getId() == null) {
            int year = meetingDate.getYear();
            int sequence = repository.maxSequenceNumber() + 1;
            protocol.setCalendarYear(year);
            protocol.setSequenceNumber(sequence);
            protocol.setProtocolNumber("№" + sequence);
        }
        protocol.setAcademicYear(academicYear);
        protocol.setMeetingDate(meetingDate);
        protocol.setProtocolType(Objects.requireNonNullElse(request.getProtocolType(), PpkProtocolType.APPOINTMENT));
        StudentProfile student = request.getStudentId() == null ? null : studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена"));
        OvzDtos.PpkProtocolDefaults defaults = defaults(academicYear, student == null ? null : student.getId());
        protocol.setStudent(student);
        protocol.setClassName(student == null ? null : currentClass(student.getId(), academicYear));
        protocol.setChairName(orDefault(request.getChairName(), defaults.getChairName()));
        protocol.setSecretaryName(orDefault(request.getSecretaryName(), defaults.getSecretaryName()));
        protocol.setAttendees(orDefault(request.getAttendees(), defaults.getAttendees()));
        if (request.getRepresentativeName() != null || request.getRepresentativeSignatureName() != null) {
            protocol.setRepresentativeName(trim(request.getRepresentativeName()));
            protocol.setRepresentativeSignatureName(trim(request.getRepresentativeSignatureName()));
            protocol.setInvitedRepresentative(null);
        } else {
            protocol.setInvitedRepresentative(orDefault(
                    request.getInvitedRepresentative(), defaults.getInvitedRepresentative()));
        }
        protocol.setAgenda(orDefault(request.getAgenda(), defaults.getAgenda()));
        protocol.setMeetingNotes(orDefault(request.getMeetingNotes(), defaults.getMeetingNotes()));
        protocol.setDecisionText(orDefault(request.getDecisionText(), defaults.getDecisionText()));
        protocol.setStatus(Objects.requireNonNullElse(request.getStatus(), OvzStageStatus.NOT_RELEASED));
        protocol.setUpdatedAt(LocalDateTime.now());
        protocol = repository.save(protocol);
        if (previousStudentId != null && (!Objects.equals(previousStudentId, student == null ? null : student.getId())
                || !Objects.equals(previousAcademicYear, academicYear) || previousType != protocol.getProtocolType())) {
            refreshRoadmap(previousStudentId, previousAcademicYear, previousType);
        }
        refreshRoadmap(protocol);
        return toView(protocol);
    }

    @Transactional
    public void delete(String academicYear, Long id) {
        PpkProtocol protocol = require(academicYear, id);
        Long studentId = protocol.getStudent() == null ? null : protocol.getStudent().getId();
        PpkProtocolType type = protocol.getProtocolType();
        repository.delete(protocol);
        repository.flush();
        refreshRoadmap(studentId, academicYear, type);
    }

    @Transactional
    public OvzDtos.PpkProtocolView markSigned(String academicYear, Long id) {
        PpkProtocol protocol = require(academicYear, id);
        protocol.setStatus(OvzStageStatus.COMPLETED);
        protocol.setUpdatedAt(LocalDateTime.now());
        protocol = repository.save(protocol);
        refreshRoadmap(protocol);
        return toView(protocol);
    }

    @Transactional
    public GeneratedDocument generate(String academicYear, Long id) {
        PpkProtocol protocol = require(academicYear, id);
        if (protocol.getStatus() == OvzStageStatus.NOT_RELEASED) {
            protocol.setStatus(OvzStageStatus.PRINTED);
            protocol.setUpdatedAt(LocalDateTime.now());
            protocol = repository.save(protocol);
            refreshRoadmap(protocol);
        }
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            configurePage(document);
            centered(document, "ПРОТОКОЛ ЗАСЕДАНИЯ", true, 12);
            centered(document, "ПСИХОЛОГО-ПЕДАГОГИЧЕСКОГО КОНСИЛИУМА", true, 12);
            centered(document, "ГБОУ ШКОЛА №7 Г. МОСКВЫ", true, 12);
            paragraph(document, protocol.getProtocolNumber() + " от " + protocol.getMeetingDate().format(DATE), false, ParagraphAlignment.CENTER);
            paragraph(document, "Председатель ППк: " + protocol.getChairName(), false, ParagraphAlignment.LEFT);
            paragraph(document, "Секретарь ППк: " + protocol.getSecretaryName(), false, ParagraphAlignment.LEFT);
            paragraph(document, "Присутствовали:", false, ParagraphAlignment.LEFT);
            listLines(document, protocol.getAttendees());
            String invitation = representativeInvitation(protocol);
            if (invitation != null) {
                paragraph(document, "Приглашены: " + invitation, false, ParagraphAlignment.LEFT);
            }
            heading(document, "Повестка заседания:");
            numberedLines(document, protocol.getAgenda());
            heading(document, "Ход заседания ППк:");
            numberedLines(document, protocol.getMeetingNotes());
            heading(document, "Решение ППк:");
            numberedLines(document, protocol.getDecisionText());
            paragraph(document, "Председатель ППк __________________ / " + protocol.getChairName() + " /", false, ParagraphAlignment.LEFT);
            if (hasRepresentative(protocol)) {
                paragraph(document, "Представитель ребёнка __________________ / "
                        + orDefault(protocol.getRepresentativeSignatureName(), "____________________________")
                        + " /", false, ParagraphAlignment.LEFT);
            }
            document.write(out);
            return new GeneratedDocument(protocol.getProtocolNumber() + ".docx", out.toByteArray());
        } catch (Exception error) {
            throw new IllegalStateException("Не удалось сформировать протокол ППк", error);
        }
    }

    private PpkProtocol require(String academicYear, Long id) {
        return repository.findById(id).filter(p -> Objects.equals(p.getAcademicYear(), academicYear))
                .orElseThrow(() -> new IllegalArgumentException("Протокол ППк не найден"));
    }

    private void refreshRoadmap(PpkProtocol protocol) {
        refreshRoadmap(protocol.getStudent() == null ? null : protocol.getStudent().getId(),
                protocol.getAcademicYear(), protocol.getProtocolType());
    }

    private void refreshRoadmap(Long studentId, String academicYear, PpkProtocolType protocolType) {
        if (studentId == null || academicYear == null || protocolType == null) return;
        OvzRoadmapStage roadmapStage = protocolType == PpkProtocolType.IOM
                ? OvzRoadmapStage.PPK_IOM : OvzRoadmapStage.PPK_APPOINTMENT;
        List<PpkProtocol> linked = repository.findAllByStudent_IdAndAcademicYearOrderByMeetingDateDesc(studentId, academicYear)
                .stream().filter(item -> roadmapStage == OvzRoadmapStage.PPK_IOM
                        ? item.getProtocolType() == PpkProtocolType.IOM
                        : item.getProtocolType() != PpkProtocolType.IOM).toList();
        if (linked.isEmpty()) {
            workflowStageRepository.findByStudent_IdAndAcademicYearAndStage(studentId, academicYear, roadmapStage)
                    .ifPresent(workflowStageRepository::delete);
            return;
        }
        OvzStageStatus aggregateStatus = linked.stream().map(PpkProtocol::getStatus)
                .max(java.util.Comparator.comparingInt(this::statusRank)).orElse(OvzStageStatus.NOT_RELEASED);
        OvzWorkflowStage stage = workflowStageRepository.findByStudent_IdAndAcademicYearAndStage(
                studentId, academicYear, roadmapStage
        ).orElseGet(OvzWorkflowStage::new);
        stage.setStudent(studentRepository.getById(studentId)); stage.setAcademicYear(academicYear);
        stage.setStage(roadmapStage); stage.setStatus(aggregateStatus); stage.setUpdatedAt(LocalDateTime.now());
        stage.setPrintedAt(aggregateStatus == OvzStageStatus.NOT_RELEASED ? null
                : Objects.requireNonNullElse(stage.getPrintedAt(), LocalDateTime.now()));
        stage.setCompletedAt(aggregateStatus == OvzStageStatus.COMPLETED ? LocalDateTime.now() : null);
        workflowStageRepository.save(stage);
    }

    private int statusRank(OvzStageStatus status) {
        return switch (status) {
            case NOT_RELEASED -> 0;
            case PRINTED -> 1;
            case COMPLETED -> 2;
        };
    }

    private OvzDtos.PpkProtocolView toView(PpkProtocol p) {
        OvzDtos.PpkProtocolView view = new OvzDtos.PpkProtocolView();
        view.setId(p.getId()); view.setProtocolNumber(p.getProtocolNumber()); view.setMeetingDate(p.getMeetingDate());
        view.setProtocolType(p.getProtocolType()); view.setStudentId(p.getStudent() == null ? null : p.getStudent().getId());
        view.setStudentFullName(p.getStudent() == null ? null : p.getStudent().getCurrentFullName()); view.setClassName(p.getClassName());
        view.setChairName(p.getChairName()); view.setSecretaryName(p.getSecretaryName()); view.setAttendees(p.getAttendees());
        view.setInvitedRepresentative(p.getInvitedRepresentative()); view.setRepresentativeName(p.getRepresentativeName());
        view.setRepresentativeSignatureName(p.getRepresentativeSignatureName()); view.setAgenda(p.getAgenda());
        view.setMeetingNotes(p.getMeetingNotes()); view.setDecisionText(p.getDecisionText()); view.setStatus(p.getStatus());
        return view;
    }

    private String currentClass(Long studentId, String academicYear) {
        return enrollmentRepository.findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(studentId, academicYear)
                .map(StudentClassEnrollment::getClassName)
                .orElseGet(() -> enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(studentId, academicYear)
                        .stream().map(StudentClassEnrollment::getClassName).findFirst().orElse(""));
    }

    private String defaultAgenda(String fioGenitive, String fioDative,
                                 String learnerGenitive, String learnerDative,
                                 String variant, String academicYear) {
        String aoop = orDefault(variant, "____");
        return "Создание специальных условий и организация коррекционно-развивающих занятий "
                + "в образовательной организации " + learnerDative + " с ОВЗ " + fioDative
                + " по АООП вариант " + aoop + " в " + academicYear + " учебном году.\n"
                + "Организация коррекционно-развивающих занятий по индивидуальному образовательному маршруту (ИОМ) "
                + "для " + learnerGenitive + " с ОВЗ " + fioGenitive + " по АООП вариант " + aoop
                + " на " + academicYear + " учебный год.";
    }

    private String defaultRecommendationAgenda(String fioGenitive, String learnerGenitive, String academicYear) {
        return "Оказание психолого-педагогического сопровождения и организация коррекционно-развивающих занятий "
                + "в образовательной организации для " + learnerGenitive + " " + fioGenitive
                + " в " + academicYear + " учебном году.";
    }

    private String defaultRecommendationNotes(String academicYear, String learnerGenitive) {
        return "Консультирование родителя/законного представителя по вопросам оказания психолого-педагогического "
                + "сопровождения для " + learnerGenitive + " в " + academicYear
                + " учебном году согласно рекомендации ЦПМПК.";
    }

    private String defaultRecommendationDecision(String fioGenitive, String learnerGenitive,
                                                  String className, String academicYear) {
        return "На основании рекомендации ЦПМПК г. Москвы оказать психолого-педагогическое сопровождение для "
                + learnerGenitive + " " + displayClassName(className) + " класса " + fioGenitive
                + " на " + academicYear + " учебный год.";
    }

    private String representativeInvitation(PpkProtocol protocol) {
        String legacy = trim(protocol.getInvitedRepresentative());
        if (legacy != null && protocol.getRepresentativeName() == null
                && protocol.getRepresentativeSignatureName() == null) return legacy;
        if (!hasRepresentative(protocol)) return null;
        return "законный представитель ребёнка — "
                + orDefault(protocol.getRepresentativeName(), "________________________________________");
    }

    private boolean hasRepresentative(PpkProtocol protocol) {
        return protocol.getStudent() != null || trim(protocol.getInvitedRepresentative()) != null
                || trim(protocol.getRepresentativeName()) != null
                || trim(protocol.getRepresentativeSignatureName()) != null;
    }

    private String defaultNotes(String conclusionNumber, String academicYear, String learnerGenitive) {
        return "Разработали рекомендации по созданию специальных условий обучения и индивидуального образовательного маршрута (ИОМ) "
                + "для " + learnerGenitive + " с ОВЗ в " + academicYear + " учебном году в соответствии с заключением ЦПМПК №"
                + orDefault(conclusionNumber, "_____") + " в образовательной организации.\n"
                + "Консультирование родителя/законного представителя по вопросам создания специальных условий, "
                + "организации психолого-педагогического сопровождения для " + learnerGenitive + " с ОВЗ в "
                + academicYear + " учебном году.";
    }

    private String defaultDecision(String fioGenitive, String fioDative,
                                   String learnerGenitive, String learnerDative,
                                   String className, String variant, String academicYear) {
        String aoop = orDefault(variant, "____");
        return "На основании заключения ЦПМПК г. Москвы создать специальные условия обучения для "
                + learnerGenitive + " " + displayClassName(className) + " класса " + fioGenitive
                + " по АООП вариант " + aoop
                + " на " + academicYear + " учебный год.\n"
                + "Организовать коррекционно-развивающие занятия по индивидуальному образовательному маршруту (ИОМ) "
                + learnerDative + " с ОВЗ " + fioDative + " по АООП вариант " + aoop
                + " на " + academicYear + " учебный год.";
    }

    private String aoopVariant(String nosologyCode) {
        String value = trim(nosologyCode);
        if (value == null) return null;
        value = value.replaceFirst("^[ИиОо]", "").trim();
        return value.matches("\\d+\\.\\d+") ? value : null;
    }

    private String displayAcademicYear(String academicYear) {
        return orDefault(academicYear, "____/____").replace('/', '-');
    }

    private String displayClassName(String className) {
        String value = orDefault(className, "____").trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d{1,2})\\s*[-–—]?\\s*([A-Za-zА-Яа-яЁё])$").matcher(value);
        return matcher.matches() ? matcher.group(1) + " «" + matcher.group(2).toUpperCase() + "»" : value;
    }

    private String commissionLine(String fullName, String position) {
        String name = orDefault(fullName, "");
        String role = trim(position);
        return role == null ? name : name + " — " + role;
    }

    private void configurePage(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = sect.addNewPgSz(); size.setOrient(STPageOrientation.PORTRAIT);
        size.setW(BigInteger.valueOf(11906)); size.setH(BigInteger.valueOf(16838));
        CTPageMar mar = sect.addNewPgMar(); mar.setTop(BigInteger.valueOf(850)); mar.setBottom(BigInteger.valueOf(850));
        mar.setLeft(BigInteger.valueOf(1134)); mar.setRight(BigInteger.valueOf(850));
    }

    private void centered(XWPFDocument doc, String text, boolean bold, int size) {
        XWPFParagraph p = doc.createParagraph(); p.setAlignment(ParagraphAlignment.CENTER); p.setSpacingAfter(0);
        run(p, text, bold, size);
    }
    private void heading(XWPFDocument doc, String text) { paragraph(doc, text, true, ParagraphAlignment.LEFT); }
    private void paragraph(XWPFDocument doc, String text, boolean bold, ParagraphAlignment alignment) {
        XWPFParagraph p = doc.createParagraph(); p.setAlignment(alignment); p.setSpacingAfter(40); p.setFirstLineIndent(420);
        run(p, text, bold, 12);
    }
    private void lines(XWPFDocument doc, String value) {
        for (String line : orDefault(value, "").split("\\R")) paragraph(doc, line, false, ParagraphAlignment.BOTH);
    }
    private void listLines(XWPFDocument doc, String value) {
        for (String line : orDefault(value, "").split("[;\\r\\n]+")) {
            if (trim(line) != null) paragraph(doc, trim(line), false, ParagraphAlignment.LEFT);
        }
    }
    private void numberedLines(XWPFDocument doc, String value) {
        String[] parts = orDefault(value, "").split("\\R");
        for (int i = 0; i < parts.length; i++) paragraph(doc, (i + 1) + ". " + parts[i], false, ParagraphAlignment.BOTH);
    }
    private void run(XWPFParagraph p, String text, boolean bold, int size) {
        XWPFRun r = p.createRun(); r.setFontFamily("Times New Roman"); r.setFontSize(size); r.setBold(bold); r.setText(text);
    }
    private String orDefault(String value, String fallback) { String v = trim(value); return v == null ? fallback : v; }
    private String trim(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }

    public record GeneratedDocument(String fileName, byte[] content) {}
}
