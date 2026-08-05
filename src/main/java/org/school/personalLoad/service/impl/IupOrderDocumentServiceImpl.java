package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.school.personalLoad.dto.contingent.IupOrderDocumentDtos;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.IupDeliveryForm;
import org.school.personalLoad.model.IupOrderTemplateType;
import org.school.personalLoad.model.IupParticipationMode;
import org.school.personalLoad.model.IupPlan;
import org.school.personalLoad.model.IupSubjectLine;
import org.school.personalLoad.model.IupTeacherAssignment;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.StudentClassEnrollment;
import org.school.personalLoad.model.StudentGender;
import org.school.personalLoad.model.StudentSupportStatus;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.IupPlanRepository;
import org.school.personalLoad.repository.IupSubjectLineRepository;
import org.school.personalLoad.repository.IupTeacherAssignmentRepository;
import org.school.personalLoad.repository.StudentClassEnrollmentRepository;
import org.school.personalLoad.repository.StudentSupportStatusRepository;
import org.school.personalLoad.service.IupOrderDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IupOrderDocumentServiceImpl implements IupOrderDocumentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String SCHOOL_NAME = "ГБОУ Школа № 7";
    private static final String DEFAULT_DIRECTOR = "И. О. Фамилия";
    private static final String DEFAULT_CONTROL_OFFICER = "И. О. Фамилия";
    private static final String DEFAULT_EJ_ADMIN = "И. О. Фамилия";

    private final IupPlanRepository iupPlanRepository;
    private final IupSubjectLineRepository iupSubjectLineRepository;
    private final IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentSupportStatusRepository supportStatusRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;

    @Override
    @Transactional(readOnly = true)
    public GeneratedDocument generate(String academicYear,
                                      IupOrderDocumentDtos.GenerateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Параметры приказа не переданы");
        }
        IupOrderTemplateType templateType = Objects.requireNonNullElse(
                request.getTemplateType(),
                IupOrderTemplateType.INDIVIDUAL_IUP
        );
        List<Long> ids = request.getIupPlanIds() == null
                ? List.of()
                : request.getIupPlanIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один ИУП для приказа");
        }
        if (templateType != IupOrderTemplateType.OVZ_GROUP && ids.size() != 1) {
            throw new IllegalArgumentException("Для этого вида приказа должен быть выбран один ребёнок");
        }

        List<IupPlan> plans = iupPlanRepository.findAllById(ids);
        if (plans.size() != ids.size()) {
            throw new IllegalArgumentException("Один или несколько ИУП не найдены");
        }
        if (plans.stream().anyMatch(plan -> !Objects.equals(academicYear, plan.getAcademicYear()))) {
            throw new IllegalArgumentException("Нельзя объединять в приказ ИУП другого учебного года");
        }

        Map<Long, StudentCategory> categoryByStudent = activeCategories(academicYear, plans);
        if (templateType == IupOrderTemplateType.OVZ_GROUP) {
            boolean containsNormal = plans.stream()
                    .anyMatch(plan -> categoryByStudent.getOrDefault(plan.getStudent().getId(), StudentCategory.NORMAL)
                            == StudentCategory.NORMAL);
            if (containsNormal) {
                throw new IllegalArgumentException("В сводный приказ ОВЗ включаются только дети со статусом К2 или К3");
            }
        }

        List<PlanData> data = plans.stream()
                .map(plan -> loadPlanData(academicYear, plan,
                        categoryByStudent.getOrDefault(plan.getStudent().getId(), StudentCategory.NORMAL)))
                .sorted(Comparator.comparing(PlanData::className, this::compareClassNames)
                        .thenComparing(item -> item.plan().getStudent().getCurrentFullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        String orderNumber = firstNonBlank(
                request.getOrderNumber(),
                data.size() == 1 ? data.get(0).plan().getOrderNumber() : null
        );
        LocalDate orderDate = request.getOrderDate() != null
                ? request.getOrderDate()
                : data.size() == 1 ? data.get(0).plan().getOrderDate() : null;
        requireText(orderNumber, "Укажите номер приказа");
        if (orderDate == null) {
            throw new IllegalArgumentException("Укажите дату приказа");
        }
        validateTemplateFields(templateType, request);

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            addLetterhead(document);
            addOrderNumber(document, orderDate, orderNumber);
            switch (templateType) {
                case INDIVIDUAL_IUP -> buildIndividualOrder(document, data.get(0), request, orderDate, orderNumber);
                case HOME_EDUCATION -> buildHomeOrder(document, data.get(0), request, orderDate, orderNumber, false);
                case HOME_EDUCATION_EXTENSION -> buildHomeOrder(document, data.get(0), request, orderDate, orderNumber, true);
                case OVZ_GROUP -> buildOvzGroupOrder(document, data, request, orderDate, orderNumber);
            }
            document.write(output);
            String fileName = safeFileName("Приказ № " + orderNumber + " от " + DATE.format(orderDate)
                    + " ИУП.docx");
            return new GeneratedDocument(output.toByteArray(), fileName);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сформировать приказ Word", exception);
        }
    }

    private void buildIndividualOrder(XWPFDocument document,
                                      PlanData data,
                                      IupOrderDocumentDtos.GenerateRequest request,
                                      LocalDate orderDate,
                                      String orderNumber) {
        title(document, "Об организации обучения по индивидуальному учебному плану");
        body(document, "В соответствии с личным заявлением родителя (законного представителя) и на основании решения педагогического совета (протокол от "
                + date(request.getPedagogicalCouncilProtocolDate()) + " г. №"
                + text(request.getPedagogicalCouncilProtocolNumber()) + ")");
        command(document);

        String learner = learnerWord(request.getStudentGender(), false);
        String name = text(request.getStudentNameForOrder());
        body(document, "1. Организовать обучение " + learner + " " + classDisplay(data.className())
                + " класса – " + name + birthSuffix(data)
                + ", по индивидуальному учебному плану с " + date(data.plan().getValidFrom()) + " г.");
        body(document, "2. Утвердить индивидуальный учебный план обучающегося (Приложение 1).");
        body(document, "3. Утвердить индивидуальное расписание обучающегося с "
                + date(data.plan().getValidFrom()) + " года (Приложение 2).");
        body(document, "4. Утвердить график промежуточной аттестации в соответствии с индивидуальным учебным планом (Приложение 3).");
        appendTeacherControlPoint(document, data, 5);
        body(document, "6. Администратору электронного журнала "
                + person(request.getElectronicJournalAdministrator(), DEFAULT_EJ_ADMIN)
                + " разместить в ЭЖД индивидуальный учебный план обучающегося и расписание обучающегося согласно индивидуальному учебному плану.");
        body(document, "7. Классному руководителю " + classDisplay(data.className()) + " класса – "
                + classTeacher(data) + " обеспечить методическую и консультативную помощь обучающемуся и родителям (законным представителям); довести информацию о сроках прохождения промежуточной аттестации до всех участников образовательного процесса.");
        body(document, "8. Контроль за исполнением приказа возложить на заместителя директора "
                + person(request.getControlOfficer(), DEFAULT_CONTROL_OFFICER) + ".");
        signature(document, request);
        executor(document, request);

        pageBreak(document);
        appendPlanAppendix(document, data, "Приложение 1", orderDate, orderNumber, request);
        pageBreak(document);
        appendScheduleAppendix(document, data, "Приложение 2", orderDate, orderNumber);
        pageBreak(document);
        appendAssessmentAppendix(document, data, "Приложение 3", orderDate, orderNumber);
    }

    private void buildHomeOrder(XWPFDocument document,
                                PlanData data,
                                IupOrderDocumentDtos.GenerateRequest request,
                                LocalDate orderDate,
                                String orderNumber,
                                boolean extension) {
        title(document, extension
                ? "Об организации обучения\nпо индивидуальному учебному плану"
                : "Об изменении формы обучения и организации обучения\nпо индивидуальному учебному плану");
        String basis = "В соответствии с личным заявлением родителя (законного представителя), на основании медицинского заключения №"
                + text(request.getMedicalConclusionNumber()) + " от " + date(request.getMedicalConclusionDate())
                + " г. " + text(request.getMedicalOrganization());
        if (!extension) {
            basis += " и на основании решения педагогического совета (протокол №"
                    + text(request.getPedagogicalCouncilProtocolNumber()) + " от "
                    + date(request.getPedagogicalCouncilProtocolDate()) + " г.)";
        }
        body(document, basis);
        command(document);

        String learner = learnerWord(request.getStudentGender(), false);
        String name = text(request.getStudentNameForOrder());
        int point = 1;
        if (extension) {
            body(document, point++ + ". Внести изменения в приказ от "
                    + date(request.getPreviousOrderDate()) + " № " + text(request.getPreviousOrderNumber()) + ".");
            body(document, point++ + ". Продлить индивидуальные учебные занятия на дому с применением дистанционных и электронных образовательных технологий с "
                    + date(data.plan().getValidFrom()) + " г. по " + date(data.plan().getValidTo()) + " г. "
                    + learner + " " + classDisplay(data.className()) + " класса – " + name + birthSuffix(data) + ".");
        } else {
            body(document, point++ + ". Перевести с очной формы обучения на очно-заочную форму обучения c организацией индивидуальных учебных занятий на дому с применением дистанционных и электронных образовательных технологий с "
                    + date(data.plan().getValidFrom()) + " г. по " + date(data.plan().getValidTo()) + " г. "
                    + learner + " " + classDisplay(data.className()) + " класса – " + name + ".");
            body(document, point++ + ". Организовать для " + name
                    + " индивидуальные учебные занятия на дому по индивидуальному учебному плану (далее ИУП) с "
                    + date(data.plan().getValidFrom()) + " г.");
            body(document, point++ + ". Утвердить ИУП обучающегося (Приложение 1).");
        }
        body(document, point++ + ". " + person(request.getResponsibleCoordinator(), "Ответственному за организацию обучения")
                + " – разработать и согласовать с родителями (законными представителями) обучающегося расписание индивидуальных учебных занятий на дому, график промежуточной аттестации в соответствии с индивидуальным учебным планом.");
        body(document, point++ + ". " + person(request.getElectronicJournalAdministrator(), DEFAULT_EJ_ADMIN)
                + (extension ? " продлить" : " разместить") + " в ЭЖД ИУП обучающегося (Приложение 1).");
        body(document, point++ + ". Утвердить дополнительную учебную нагрузку учителей (Приложение 2).");
        if (!extension && trimToNull(request.getEnrollmentAdministrator()) != null) {
            body(document, point++ + ". " + request.getEnrollmentAdministrator().trim()
                    + " изменить в системе АИС «Зачисление в образовательные учреждения» форму обучения обучающегося.");
        }
        body(document, point++ + ". Классному руководителю " + classDisplay(data.className()) + " класса – "
                + classTeacher(data) + " обеспечить методическую и консультативную помощь обучающемуся и родителям (законным представителям); довести информацию о сроках прохождения промежуточной аттестации до всех участников образовательного процесса.");
        body(document, point + ". Контроль за исполнением приказа возложить на заместителя директора "
                + person(request.getControlOfficer(), DEFAULT_CONTROL_OFFICER) + ".");
        signature(document, request);
        executor(document, request);
        appendAcknowledgement(document, data);

        pageBreak(document);
        appendPlanAppendix(document, data, "Приложение 1", orderDate, orderNumber, request);
        pageBreak(document);
        appendTeacherLoadAppendix(document, List.of(data), "Приложение 2", orderDate, orderNumber);
    }

    private void buildOvzGroupOrder(XWPFDocument document,
                                    List<PlanData> plans,
                                    IupOrderDocumentDtos.GenerateRequest request,
                                    LocalDate orderDate,
                                    String orderNumber) {
        title(document, "Об организации обучения по\nиндивидуальному учебному плану\nв "
                + academicYearText(plans.get(0).plan().getAcademicYear()) + " учебном году");
        body(document, "В соответствии с личными заявлениями родителей (законных представителей), на основании решения ППк №"
                + text(request.getPpkProtocolNumber()) + " " + SCHOOL_NAME + " от "
                + date(request.getPpkProtocolDate()) + " г. и на основании решения педагогического совета (протокол от "
                + date(request.getPedagogicalCouncilProtocolDate()) + " г. №"
                + text(request.getPedagogicalCouncilProtocolNumber()) + ")");
        command(document);

        Map<LocalDate, List<PlanData>> byEndDate = new LinkedHashMap<>();
        for (PlanData item : plans) {
            byEndDate.computeIfAbsent(item.plan().getValidTo(), ignored -> new ArrayList<>()).add(item);
        }
        int point = 1;
        for (Map.Entry<LocalDate, List<PlanData>> entry : byEndDate.entrySet()) {
            LocalDate from = entry.getValue().stream().map(item -> item.plan().getValidFrom())
                    .min(LocalDate::compareTo).orElse(null);
            body(document, point++ + ". Организовать обучение обучающихся с ограниченными возможностями здоровья (далее с ОВЗ) и инвалидностью (далее ИНВ) по индивидуальному учебному плану (далее ИУП) в очной форме с "
                    + date(from) + " г. по " + date(entry.getKey()) + " г.:");
            int index = 1;
            for (PlanData item : entry.getValue()) {
                body(document, index++ + ") " + item.plan().getStudent().getCurrentFullName()
                        + ", " + classDisplay(item.className()) + " класс" + birthSuffix(item) + ";");
            }
        }
        body(document, point++ + ". Утвердить ИУП обучающихся с ОВЗ и ИНВ (Приложения 1.1–1."
                + plans.size() + ").");
        body(document, point++ + ". " + person(request.getResponsibleCoordinator(), "Ответственным за организацию обучения")
                + " – разработать и согласовать с родителями (законными представителями) обучающихся график промежуточной аттестации в соответствии с ИУП.");
        body(document, point++ + ". " + person(request.getElectronicJournalAdministrator(), DEFAULT_EJ_ADMIN)
                + " разместить в ЭЖД обучающихся с ОВЗ и ИНВ занятия согласно ИУП.");
        body(document, point++ + ". Утвердить дополнительную учебную нагрузку учителей, обеспечивающих проведение индивидуальных и внеурочных занятий для обучающихся с ОВЗ и (или) ИНВ (Приложение 2).");
        body(document, point++ + ". Классным руководителям обеспечить методическую и консультативную помощь обучающимся с ОВЗ и (или) инвалидностью и родителям (законным представителям); довести информацию о сроках прохождения промежуточной аттестации до всех участников образовательного процесса.");
        body(document, point + ". Контроль за исполнением приказа возложить на заместителя директора "
                + person(request.getControlOfficer(), DEFAULT_CONTROL_OFFICER) + ".");
        signature(document, request);
        executor(document, request);
        appendAcknowledgement(document, plans);

        int appendix = 1;
        for (PlanData plan : plans) {
            pageBreak(document);
            appendPlanAppendix(document, plan, "Приложение 1." + appendix++, orderDate, orderNumber, request);
        }
        pageBreak(document);
        appendTeacherLoadAppendix(document, plans, "Приложение 2", orderDate, orderNumber);
    }

    private void appendTeacherControlPoint(XWPFDocument document, PlanData data, int point) {
        String teachers = data.assignments().stream()
                .map(IupTeacherAssignment::getTeacherFioSnapshot)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
        if (teachers.isBlank()) {
            teachers = "учителям, назначенным по ИУП";
        }
        body(document, point + ". " + teachers
                + " проводить текущий контроль успеваемости обучающегося в соответствии с положением "
                + SCHOOL_NAME + " «О формах, периодичности и порядке текущего контроля успеваемости и промежуточной аттестации обучающихся», организовать проведение промежуточной аттестации в соответствии с графиком проведения промежуточной аттестации.");
    }

    private void appendPlanAppendix(XWPFDocument document,
                                    PlanData data,
                                    String appendix,
                                    LocalDate orderDate,
                                    String orderNumber,
                                    IupOrderDocumentDtos.GenerateRequest request) {
        appendixHeader(document, appendix, orderDate, orderNumber);
        centered(document, "ИНДИВИДУАЛЬНЫЙ УЧЕБНЫЙ ПЛАН", true, 14);
        centered(document, "обучающегося " + classDisplay(data.className()) + " класса " + SCHOOL_NAME, false, 12);
        centered(document, data.plan().getStudent().getCurrentFullName(), true, 12);
        centered(document, firstNonBlank(request.getEducationLevelAndForm(), "очная форма обучения"), false, 11);
        centered(document, "(срок реализации: с " + date(data.plan().getValidFrom()) + " по "
                + date(data.plan().getValidTo()) + ")", false, 11);

        String[] headers = {"Предметная область", "Учебный предмет", "Всего часов в неделю",
                "С классом", "Индивидуально", "Группа", "Форма проведения", "Учитель", "Форма контроля"};
        XWPFTable table = createTable(document, headers);
        BigDecimal totalClass = BigDecimal.ZERO;
        BigDecimal totalIndividual = BigDecimal.ZERO;
        for (IupSubjectLine line : data.lines()) {
            List<IupTeacherAssignment> assignments = data.assignmentsByLine().getOrDefault(line.getId(), List.of());
            BigDecimal total = safe(line.getClassHours()).add(safe(line.getIndividualHours()));
            totalClass = totalClass.add(safe(line.getClassHours()));
            totalIndividual = totalIndividual.add(safe(line.getIndividualHours()));
            addRow(table,
                    data.subjectAreaByLine().getOrDefault(line.getId(), ""),
                    line.getSubjectName(),
                    number(total),
                    number(line.getClassHours()),
                    number(line.getIndividualHours()),
                    Objects.toString(line.getGroupNameEducationalPlan(), ""),
                    deliveryText(line, assignments),
                    assignments.stream().map(IupTeacherAssignment::getTeacherFioSnapshot)
                            .filter(Objects::nonNull).distinct().collect(Collectors.joining("; ")),
                    "Согласно КТП"
            );
        }
        addRow(table, "", "Итого", number(totalClass.add(totalIndividual)), number(totalClass),
                number(totalIndividual), "", "", "", "");
        setColumnWidths(table, 1900, 1900, 1050, 800, 900, 900, 1250, 1600, 1200);
    }

    private void appendScheduleAppendix(XWPFDocument document,
                                        PlanData data,
                                        String appendix,
                                        LocalDate orderDate,
                                        String orderNumber) {
        appendixHeader(document, appendix, orderDate, orderNumber);
        centered(document, "Индивидуальное расписание обучающегося " + SCHOOL_NAME + " "
                + data.plan().getStudent().getCurrentFullName() + " в соответствии с индивидуальным учебным планом", true, 12);
        centered(document, "с " + date(data.plan().getValidFrom()) + " по " + date(data.plan().getValidTo()), false, 11);
        XWPFTable table = createTable(document,
                new String[]{"№ урока", "Начало урока", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница"});
        for (int index = 1; index <= 8; index++) {
            addRow(table, String.valueOf(index), "", "", "", "", "", "");
        }
        setColumnWidths(table, 700, 900, 1750, 1750, 1750, 1750, 1750);
        smallNote(document, "Расписание заполняется после согласования с родителем (законным представителем).");
    }

    private void appendAssessmentAppendix(XWPFDocument document,
                                          PlanData data,
                                          String appendix,
                                          LocalDate orderDate,
                                          String orderNumber) {
        appendixHeader(document, appendix, orderDate, orderNumber);
        centered(document, "График проведения промежуточной аттестации в соответствии с индивидуальным учебным планом обучающегося "
                + data.plan().getStudent().getCurrentFullName(), true, 12);
        XWPFTable table = createTable(document,
                new String[]{"Предмет", "Сроки промежуточной аттестации", "Форма промежуточной аттестации", "Учитель"});
        for (IupSubjectLine line : data.lines()) {
            String teachers = data.assignmentsByLine().getOrDefault(line.getId(), List.of()).stream()
                    .map(IupTeacherAssignment::getTeacherFioSnapshot)
                    .filter(Objects::nonNull).distinct().collect(Collectors.joining("; "));
            addRow(table, line.getSubjectName(), "", "Согласно КТП", teachers);
        }
        setColumnWidths(table, 2400, 2200, 2600, 2400);
        smallNote(document, "Текущий контроль осуществляется согласно положению " + SCHOOL_NAME
                + " «О формах, периодичности и порядке текущего контроля успеваемости и промежуточной аттестации обучающихся».");
    }

    private void appendTeacherLoadAppendix(XWPFDocument document,
                                           List<PlanData> plans,
                                           String appendix,
                                           LocalDate orderDate,
                                           String orderNumber) {
        appendixHeader(document, appendix, orderDate, orderNumber);
        centered(document, "РАСПРЕДЕЛЕНИЕ ДОПОЛНИТЕЛЬНОЙ УЧЕБНОЙ НАГРУЗКИ", true, 13);
        centered(document, "В СООТВЕТСТВИИ С ИУП", true, 13);
        XWPFTable table = createTable(document,
                new String[]{"№ п/п", "ФИО обучающегося", "Класс", "ФИО учителя", "Предмет", "Форма занятий", "Количество часов"});
        int index = 1;
        for (PlanData plan : plans) {
            for (IupTeacherAssignment assignment : plan.assignments()) {
                addRow(table,
                        String.valueOf(index++),
                        plan.plan().getStudent().getCurrentFullName(),
                        plan.className(),
                        assignment.getTeacherFioSnapshot(),
                        assignment.getSubjectLine().getSubjectName(),
                        deliveryFormLabel(assignment.getDeliveryForm()),
                        number(assignment.getHoursPerWeek())
                );
            }
        }
        if (index == 1) {
            addRow(table, "", "", "", "", "", "", "");
        }
        setColumnWidths(table, 650, 2100, 850, 1800, 1700, 1350, 950);
    }

    private void appendAcknowledgement(XWPFDocument document, PlanData data) {
        appendAcknowledgement(document, List.of(data));
    }

    private void appendAcknowledgement(XWPFDocument document, List<PlanData> plans) {
        body(document, "С приказом ознакомлены:");
        XWPFTable table = createTable(document, new String[]{"Дата", "ФИО", "Подпись"});
        List<String> people = new ArrayList<>();
        for (PlanData plan : plans) {
            people.add(classTeacher(plan));
            plan.assignments().stream().map(IupTeacherAssignment::getTeacherFioSnapshot)
                    .filter(Objects::nonNull).forEach(people::add);
        }
        people.stream().filter(value -> !value.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(person -> addRow(table, "", person, ""));
        setColumnWidths(table, 1600, 5000, 2400);
    }

    private PlanData loadPlanData(String academicYear, IupPlan plan, StudentCategory category) {
        List<IupSubjectLine> lines = iupSubjectLineRepository
                .findAllByIupPlan_IdOrderBySubjectNameAsc(plan.getId());
        List<IupTeacherAssignment> assignments = iupTeacherAssignmentRepository
                .findAllBySubjectLine_IupPlan_Id(plan.getId());
        Map<Long, List<IupTeacherAssignment>> assignmentsByLine = assignments.stream()
                .collect(Collectors.groupingBy(item -> item.getSubjectLine().getId()));
        StudentClassEnrollment enrollment = enrollmentAt(academicYear, plan);
        Map<Long, String> subjectAreaByLine = new HashMap<>();
        Map<Long, CurriculumPlanEntry> entries = curriculumPlanEntryRepository.findAllById(
                        lines.stream().map(IupSubjectLine::getCurriculumEntryId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(CurriculumPlanEntry::getId, item -> item));
        for (IupSubjectLine line : lines) {
            CurriculumPlanEntry entry = entries.get(line.getCurriculumEntryId());
            if (entry != null && entry.getSubject() != null) {
                subjectAreaByLine.put(line.getId(), Objects.toString(entry.getSubject().getSubjectAreaName(), ""));
            }
        }
        return new PlanData(plan, enrollment.getClassName(), enrollment, category, lines, assignments,
                assignmentsByLine, subjectAreaByLine);
    }

    private StudentClassEnrollment enrollmentAt(String academicYear, IupPlan plan) {
        LocalDate date = plan.getValidFrom();
        return enrollmentRepository.findAllByStudent_IdAndAcademicYearOrderByValidFromDesc(
                        plan.getStudent().getId(), academicYear).stream()
                .filter(item -> item.getValidFrom() == null || !date.isBefore(item.getValidFrom()))
                .filter(item -> item.getValidTo() == null || !date.isAfter(item.getValidTo()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Не найден класс ребёнка на дату начала ИУП: "
                                + plan.getStudent().getCurrentFullName()));
    }

    private Map<Long, StudentCategory> activeCategories(String academicYear, List<IupPlan> plans) {
        Map<Long, List<StudentSupportStatus>> byStudent = supportStatusRepository.findAllByAcademicYear(academicYear)
                .stream().collect(Collectors.groupingBy(item -> item.getStudent().getId()));
        Map<Long, StudentCategory> result = new HashMap<>();
        for (IupPlan plan : plans) {
            StudentCategory category = byStudent.getOrDefault(plan.getStudent().getId(), List.of()).stream()
                    .filter(status -> contains(status.getValidFrom(), status.getValidTo(), plan.getValidFrom()))
                    .max(Comparator.comparing(StudentSupportStatus::getValidFrom))
                    .map(StudentSupportStatus::getCategory)
                    .orElse(StudentCategory.NORMAL);
            result.put(plan.getStudent().getId(), category);
        }
        return result;
    }

    private void validateTemplateFields(IupOrderTemplateType type,
                                        IupOrderDocumentDtos.GenerateRequest request) {
        if (type != IupOrderTemplateType.OVZ_GROUP) {
            if (request.getStudentGender() == null) {
                throw new IllegalArgumentException("Укажите пол ребёнка для правильной формулировки приказа");
            }
            requireText(request.getStudentNameForOrder(),
                    "Укажите ФИО ребёнка в форме, необходимой для текста приказа");
        }
        switch (type) {
            case INDIVIDUAL_IUP -> requirePedagogicalCouncil(request);
            case HOME_EDUCATION -> {
                requireMedicalConclusion(request);
                requirePedagogicalCouncil(request);
            }
            case HOME_EDUCATION_EXTENSION -> {
                requireMedicalConclusion(request);
                requireText(request.getPreviousOrderNumber(), "Укажите номер предыдущего приказа");
                if (request.getPreviousOrderDate() == null) {
                    throw new IllegalArgumentException("Укажите дату предыдущего приказа");
                }
            }
            case OVZ_GROUP -> {
                requireText(request.getPpkProtocolNumber(), "Укажите номер протокола ППк");
                if (request.getPpkProtocolDate() == null) {
                    throw new IllegalArgumentException("Укажите дату протокола ППк");
                }
                requirePedagogicalCouncil(request);
            }
        }
    }

    private void requireMedicalConclusion(IupOrderDocumentDtos.GenerateRequest request) {
        requireText(request.getMedicalConclusionNumber(), "Укажите номер медицинского заключения");
        requireText(request.getMedicalOrganization(), "Укажите организацию, выдавшую медицинское заключение");
        if (request.getMedicalConclusionDate() == null) {
            throw new IllegalArgumentException("Укажите дату медицинского заключения");
        }
    }

    private void requirePedagogicalCouncil(IupOrderDocumentDtos.GenerateRequest request) {
        requireText(request.getPedagogicalCouncilProtocolNumber(), "Укажите номер протокола педагогического совета");
        if (request.getPedagogicalCouncilProtocolDate() == null) {
            throw new IllegalArgumentException("Укажите дату протокола педагогического совета");
        }
    }

    private void addLetterhead(XWPFDocument document) {
        centered(document, "ДЕПАРТАМЕНТ ОБРАЗОВАНИЯ И НАУКИ ГОРОДА МОСКВЫ", true, 11);
        centered(document, "ГОСУДАРСТВЕННОЕ БЮДЖЕТНОЕ ОБЩЕОБРАЗОВАТЕЛЬНОЕ УЧРЕЖДЕНИЕ ГОРОДА МОСКВЫ «ШКОЛА № 7»", true, 11);
        centered(document, "119331 г. Москва, улица Крупской, дом № 17", false, 10);
        centered(document, "Телефон: (499) 138-38-27   E-mail: 7@edu.mos.ru   http://sch7uz.mskobr.ru", false, 10);
        centered(document, "ОКПО 40120398   ОГРН 1027739844384   ИНН/КПП 7736050780/773601001", false, 10);
        centered(document, "ПРИКАЗ", true, 14);
    }

    private void addOrderNumber(XWPFDocument document, LocalDate orderDate, String orderNumber) {
        XWPFTable table = document.createTable(1, 4);
        table.setWidth("100%");
        noBorders(table);
        setCell(table.getRow(0).getCell(0), "от", false, ParagraphAlignment.LEFT, 11);
        setCell(table.getRow(0).getCell(1), DATE.format(orderDate), false, ParagraphAlignment.LEFT, 11);
        setCell(table.getRow(0).getCell(2), "№", false, ParagraphAlignment.CENTER, 11);
        setCell(table.getRow(0).getCell(3), orderNumber, false, ParagraphAlignment.LEFT, 11);
        setColumnWidths(table, 500, 2600, 500, 2600);
    }

    private void title(XWPFDocument document, String value) {
        for (String line : value.split("\\n")) {
            centered(document, line, true, 12);
        }
    }

    private void command(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(180);
        paragraph.setSpacingAfter(120);
        run(paragraph, "Приказываю:", true, 12);
    }

    private void signature(XWPFDocument document, IupOrderDocumentDtos.GenerateRequest request) {
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingAfter(80);
        XWPFTable table = document.createTable(1, 2);
        table.setWidth("100%");
        noBorders(table);
        setCell(table.getRow(0).getCell(0), "Директор", false, ParagraphAlignment.LEFT, 12);
        setCell(table.getRow(0).getCell(1), person(request.getDirectorName(), DEFAULT_DIRECTOR), false,
                ParagraphAlignment.RIGHT, 12);
        setColumnWidths(table, 4500, 4500);
    }

    private void executor(XWPFDocument document, IupOrderDocumentDtos.GenerateRequest request) {
        if (trimToNull(request.getExecutor()) != null) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingBefore(180);
            run(paragraph, "Исп.: " + request.getExecutor().trim(), false, 9);
        }
    }

    private void appendixHeader(XWPFDocument document,
                                String appendix,
                                LocalDate orderDate,
                                String orderNumber) {
        XWPFParagraph first = document.createParagraph();
        first.setAlignment(ParagraphAlignment.RIGHT);
        first.setSpacingAfter(0);
        run(first, appendix, false, 10);
        XWPFParagraph second = document.createParagraph();
        second.setAlignment(ParagraphAlignment.RIGHT);
        second.setSpacingAfter(160);
        run(second, "к приказу от " + DATE.format(orderDate) + " г. №" + orderNumber, false, 10);
    }

    private XWPFTable createTable(XWPFDocument document, String[] headers) {
        XWPFTable table = document.createTable(1, headers.length);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        XWPFTableRow row = table.getRow(0);
        row.setRepeatHeader(true);
        for (int index = 0; index < headers.length; index++) {
            setCell(row.getCell(index), headers[index], true, ParagraphAlignment.CENTER, 9);
        }
        return table;
    }

    private void addRow(XWPFTable table, String... values) {
        XWPFTableRow row = table.createRow();
        for (int index = 0; index < values.length; index++) {
            setCell(row.getCell(index), Objects.toString(values[index], ""), false,
                    ParagraphAlignment.CENTER, 9);
        }
    }

    private void setCell(XWPFTableCell cell,
                         String value,
                         boolean bold,
                         ParagraphAlignment alignment,
                         int size) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0);
        XWPFRun run = paragraph.createRun();
        configureRun(run, bold, size);
        run.setText(Objects.toString(value, ""));
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
    }

    private void setColumnWidths(XWPFTable table, int... widths) {
        for (XWPFTableRow row : table.getRows()) {
            for (int index = 0; index < row.getTableCells().size() && index < widths.length; index++) {
                row.getCell(index).setWidth(String.valueOf(widths[index]));
            }
        }
    }

    private void centered(XWPFDocument document, String value, boolean bold, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        run(paragraph, value, bold, size);
    }

    private void body(XWPFDocument document, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setIndentationFirstLine(708);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.0);
        run(paragraph, value, false, 12);
    }

    private void smallNote(XWPFDocument document, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setSpacingBefore(100);
        paragraph.setSpacingAfter(0);
        run(paragraph, value, false, 9);
    }

    private void run(XWPFParagraph paragraph, String value, boolean bold, int size) {
        XWPFRun run = paragraph.createRun();
        configureRun(run, bold, size);
        run.setText(Objects.toString(value, ""));
    }

    private void configureRun(XWPFRun run, boolean bold, int size) {
        run.setFontFamily("Times New Roman");
        run.setFontSize(size);
        run.setBold(bold);
    }

    private void pageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().addBreak(BreakType.PAGE);
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setOrient(STPageOrientation.PORTRAIT);
        size.setW(11906);
        size.setH(16838);
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setLeft(1134);
        margins.setRight(567);
        margins.setTop(850);
        margins.setBottom(425);
        margins.setHeader(0);
        margins.setFooter(0);
        margins.setGutter(0);
    }

    private void noBorders(XWPFTable table) {
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblBorders borders = properties.isSetTblBorders()
                ? properties.getTblBorders()
                : properties.addNewTblBorders();
        setNil(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setNil(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setNil(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setNil(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setNil(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());
        setNil(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());
    }

    private void setNil(CTBorder border) {
        border.setVal(STBorder.NIL);
    }

    private String deliveryText(IupSubjectLine line, List<IupTeacherAssignment> assignments) {
        if (line.getParticipationMode() == IupParticipationMode.WITH_CLASS) {
            return "С классом";
        }
        if (line.getParticipationMode() == IupParticipationMode.NOT_STUDIED) {
            return "Не изучает";
        }
        String forms = assignments.stream().map(IupTeacherAssignment::getDeliveryForm)
                .filter(Objects::nonNull).distinct().map(this::deliveryFormLabel)
                .collect(Collectors.joining("; "));
        return forms.isBlank() ? participationModeLabel(line.getParticipationMode()) : forms;
    }

    private String participationModeLabel(IupParticipationMode mode) {
        return switch (mode) {
            case WITH_CLASS -> "С классом";
            case INDIVIDUAL -> "Индивидуально";
            case PARTIAL -> "Частично с классом";
            case NOT_STUDIED -> "Не изучает";
        };
    }

    private String deliveryFormLabel(IupDeliveryForm form) {
        if (form == null) {
            return "";
        }
        return switch (form) {
            case FACE_TO_FACE -> "Очно";
            case ELECTRONIC -> "Электронно";
            case DISTANCE -> "Дистанционно";
            case MIXED -> "Смешанно";
        };
    }

    private String learnerWord(StudentGender gender, boolean genitive) {
        if (gender == StudentGender.FEMALE) {
            return genitive ? "обучающейся" : "обучающуюся";
        }
        return genitive ? "обучающегося" : "обучающегося";
    }

    private String classTeacher(PlanData data) {
        if (data.enrollment().getClassRef() == null) {
            return "________________";
        }
        return firstNonBlank(data.enrollment().getClassRef().getFioTeacher(), "________________");
    }

    private String birthSuffix(PlanData data) {
        LocalDate birthDate = data.plan().getStudent().getBirthDate();
        return birthDate == null ? "" : ", " + DATE.format(birthDate) + " г.р.";
    }

    private String classDisplay(String value) {
        String normalized = Objects.toString(value, "").trim().replace('-', ' ');
        String[] parts = normalized.split("\\s+", 2);
        if (parts.length == 2) {
            return parts[0] + " «" + parts[1].toUpperCase(Locale.ROOT) + "»";
        }
        return normalized;
    }

    private String academicYearText(String academicYear) {
        return Objects.toString(academicYear, "").replace('/', '-');
    }

    private String number(BigDecimal value) {
        BigDecimal normalized = safe(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return normalized.signum() == 0 ? "" : normalized.toPlainString().replace('.', ',');
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String date(LocalDate value) {
        return value == null ? "____________" : DATE.format(value);
    }

    private String text(String value) {
        return firstNonBlank(value, "____________");
    }

    private String person(String value, String fallback) {
        return firstNonBlank(value, fallback);
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = trimToNull(preferred);
        return normalized == null ? Objects.toString(fallback, "") : normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void requireText(String value, String message) {
        if (trimToNull(value) == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean contains(LocalDate from, LocalDate to, LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private int compareClassNames(String left, String right) {
        return Objects.toString(left, "").compareToIgnoreCase(Objects.toString(right, ""));
    }

    private String safeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record PlanData(IupPlan plan,
                            String className,
                            StudentClassEnrollment enrollment,
                            StudentCategory category,
                            List<IupSubjectLine> lines,
                            List<IupTeacherAssignment> assignments,
                            Map<Long, List<IupTeacherAssignment>> assignmentsByLine,
                            Map<Long, String> subjectAreaByLine) {
    }
}
