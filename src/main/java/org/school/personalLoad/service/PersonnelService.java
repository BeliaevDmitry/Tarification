package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.school.personalLoad.dto.PersonnelDtos.*;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.pa.repository.PaSpecificationRepository;
import org.school.personalLoad.pa.repository.PaReportVersionRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.repository.PaReportStudentResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonnelService {
    private final TeacherDirectoryRepository teachers;
    private final ManualLoadEntryRepository loadRows;
    private final ClassroomLeadershipRepository classroomLeadership;
    private final HrServiceMemoRepository hrMemos;
    private final ServiceMemoRepository loadMemos;
    private final MckoCertificateRepository mckoCertificates;
    private final HrPersonalDataRepository personalData;
    private final EmploymentContractRepository contracts;
    private final LoadInRateRuleRepository inRateRules;
    private final LoadSalaryCalculationService salaryCalculation;
    private final PaSpecificationRepository paSpecifications;
    private final PaReportVersionRepository paReportVersions;
    private final PaReportAnalysisSummaryRepository paSummaries;
    private final PaReportStudentResultRepository paStudentResults;

    @Transactional(readOnly = true)
    public List<PersonnelRow> personnel(String academicYear) {
        Map<Long, LinkedHashSet<String>> duties = new HashMap<>();
        classroomLeadership.findAllByAcademicYear(academicYear).forEach(row -> {
            Long teacherId = row.getTeacherId();
            if (teacherId != null) {
                duties.computeIfAbsent(teacherId, ignored -> new LinkedHashSet<>())
                        .add("Классное руководство: " + row.getClassName());
            }
        });
        hrMemos.findAllByAcademicYearOrderByCreatedAtDesc(academicYear).stream()
                .filter(memo -> memo.getTeacherId() != null)
                .filter(memo -> memo.getStatus() != HrServiceMemo.Status.ANNULLED
                        && memo.getStatus() != HrServiceMemo.Status.ARCHIVED)
                .map(memo -> Map.entry(memo.getTeacherId(), shortDuty(memo)))
                .filter(entry -> !entry.getValue().isBlank())
                .forEach(entry -> {
                    LinkedHashSet<String> values = duties.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>());
                    boolean genericClassLeadership = entry.getValue().toLowerCase(Locale.ROOT)
                            .contains("классное руководство");
                    if (!genericClassLeadership || values.stream().noneMatch(value ->
                            value.toLowerCase(Locale.ROOT).startsWith("классное руководство:"))) {
                        values.add(entry.getValue());
                    }
                });
        return teachers.findAll().stream()
                .filter(teacher -> !teacher.isArchived())
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(teacher -> PersonnelRow.from(teacher,
                        String.join("; ", duties.getOrDefault(teacher.getId(), new LinkedHashSet<>())),
                        storedNameCases(teacher)))
                .toList();
    }

    @Transactional
    public AutoBuildingResult autoAssignBuildings(String academicYear) {
        Map<Long, Map<String, BigDecimal>> totals = new HashMap<>();
        loadRows.findAllByAcademicYear(academicYear).stream()
                .filter(row -> row.getTeacherId() != null)
                .filter(row -> row.getNumberSchoolBuilding() != null && !row.getNumberSchoolBuilding().isBlank())
                .forEach(row -> totals.computeIfAbsent(row.getTeacherId(), ignored -> new HashMap<>())
                        .merge(row.getNumberSchoolBuilding().trim(),
                                salaryCalculation.totalHours(row), BigDecimal::add));
        int assigned = 0;
        int unchanged = 0;
        int skippedWithoutLoad = 0;
        int skippedTies = 0;
        List<Long> ties = new ArrayList<>();
        for (TeacherDirectoryEntry teacher : teachers.findAll()) {
            if (teacher.isArchived() || isVacancy(teacher.getFioTeacher())) continue;
            Map<String, BigDecimal> byBuilding = totals.getOrDefault(teacher.getId(), Map.of());
            if (byBuilding.isEmpty()) {
                skippedWithoutLoad++;
                continue;
            }
            BigDecimal max = byBuilding.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            List<String> leaders = byBuilding.entrySet().stream()
                    .filter(entry -> entry.getValue().compareTo(max) == 0)
                    .map(Map.Entry::getKey).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            if (leaders.size() != 1) {
                skippedTies++;
                ties.add(teacher.getId());
                continue;
            }
            String target = leaders.get(0);
            if (target.equalsIgnoreCase(Objects.toString(teacher.getNumberSchoolBuilding(), ""))) {
                unchanged++;
            } else {
                teacher.setNumberSchoolBuilding(target);
                teachers.save(teacher);
                assigned++;
            }
        }
        return new AutoBuildingResult(assigned, unchanged, skippedWithoutLoad, skippedTies, ties);
    }

    @Transactional
    public AcceptEmployeeResult acceptEmployee(AcceptEmployeeRequest request, String username) {
        if (request == null || request.fioTeacher() == null || request.fioTeacher().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите ФИО сотрудника");
        }
        String fio = request.fioTeacher().trim().replaceAll("\\s+", " ");
        TeacherDirectoryEntry teacher;
        boolean linked = request.vacancyTeacherId() != null;
        if (linked) {
            teacher = teachers.findById(request.vacancyTeacherId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Вакансия не найдена"));
            if (!isVacancy(teacher.getFioTeacher())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Выбранная запись уже является сотрудником, а не вакансией");
            }
        } else {
            teacher = new TeacherDirectoryEntry();
        }
        Long selectedTeacherId = teacher.getId();
        teachers.findByFioTeacherIgnoreCase(fio).filter(existing -> !Objects.equals(existing.getId(), selectedTeacherId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Сотрудник с таким ФИО уже есть в справочнике");
                });
        String email = blankToNull(request.email());
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            String normalizedEmail = email;
            boolean duplicateEmail = teachers.findAll().stream()
                    .filter(existing -> !Objects.equals(existing.getId(), selectedTeacherId))
                    .map(TeacherDirectoryEntry::getEmail).filter(Objects::nonNull)
                    .anyMatch(existing -> existing.equalsIgnoreCase(normalizedEmail));
            if (duplicateEmail) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Сотрудник с таким email уже есть в справочнике");
            }
        }
        String previousName = teacher.getFioTeacher();
        NameCases cases = mergeNameCases(fio, request.nameCases());
        applyNameCases(teacher, cases);
        teacher.setPhone(blankToNull(request.phone()));
        teacher.setEmail(email);
        teacher.setNumberSchoolBuilding(blankToNull(request.numberSchoolBuilding()));
        teacher.setPrimaryPosition(blankToNull(request.primaryPosition()));
        teacher.setEmploymentType(blankToNull(request.employmentType()));
        teacher.setEmploymentDate(request.employmentDate());
        teacher.setDismissalDate(null);
        teacher.setPlannedDismissalDate(null);
        teacher.setArchived(false);
        teacher.setArchivedAt(null);
        teacher = teachers.save(teacher);
        if (linked) updateNameSnapshots(teacher.getId(), previousName, fio);

        HrPersonalData personal = personalData.findByTeacherId(teacher.getId()).orElseGet(HrPersonalData::new);
        personal.setTeacherId(teacher.getId());
        personal.setBirthDate(request.birthDate());
        personal.setPassportSeries(blankToNull(request.passportSeries()));
        personal.setPassportNumber(blankToNull(request.passportNumber()));
        personal.setPassportIssuedBy(blankToNull(request.passportIssuedBy()));
        personal.setPassportIssueDate(request.passportIssueDate());
        personal.setPassportDepartmentCode(blankToNull(request.passportDepartmentCode()));
        personal.setRegistrationAddress(blankToNull(request.registrationAddress()));
        personal.setActualAddress(blankToNull(request.actualAddress()));
        personal.setPhone(blankToNull(request.phone()));
        personal.setInn(blankToNull(request.inn()));
        personal.setSnils(blankToNull(request.snils()));
        personal.setRevision(Math.max(1, personal.getRevision()) + (personal.getId() == null ? 0 : 1));
        personal.setUpdatedAt(LocalDateTime.now());
        personal.setUpdatedBy(username);
        personalData.save(personal);

        if (present(request.contractNumber()) || request.contractDate() != null) {
            if (!present(request.contractNumber()) || request.contractDate() == null || !present(request.primaryPosition())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Для трудового договора заполните номер, дату и основную должность");
            }
            EmploymentContract contract = contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(teacher.getId())
                    .stream().filter(EmploymentContract::isPrimaryContract).findFirst()
                    .orElseGet(EmploymentContract::new);
            contract.setTeacherId(teacher.getId());
            contract.setContractNumber(request.contractNumber().trim());
            contract.setContractDate(request.contractDate());
            contract.setPositionName(request.primaryPosition().trim());
            contract.setStartDate(request.contractStartDate());
            contract.setEndDate(request.contractEndDate());
            contract.setPrimaryContract(true);
            contract.setActive(true);
            LoadInRateRule inRateRule=ruleForPosition(request.primaryPosition(),request.loadInRateRuleId());
            contract.setLoadHoursMayBeIncludedInRate(inRateRule!=null);
            contract.setLoadInRateRuleId(inRateRule==null?null:inRateRule.getId());
            contract.setLoadInRateDocumentLabel(null);
            contracts.save(contract);
        }
        return new AcceptEmployeeResult(teacher.getId(), linked, previousName, fio, cases);
    }

    private LoadInRateRule ruleForPosition(String positionName,Long preferredRuleId){
        String position=Objects.toString(positionName,"").trim();
        if(position.isBlank())return null;
        if(preferredRuleId!=null){
            LoadInRateRule preferred=inRateRules.findById(preferredRuleId).orElse(null);
            if(preferred!=null&&preferred.isActive()&&preferred.getName().equalsIgnoreCase(position))return preferred;
        }
        return inRateRules.findAllByOrderByNameAsc().stream()
                .filter(LoadInRateRule::isActive)
                .filter(rule->rule.getName().equalsIgnoreCase(position))
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public NameCases nameCases(Long teacherId) {
        return storedNameCases(teachers.findById(teacherId).orElseThrow());
    }

    public NameCases deriveNameCases(String fio) {
        return RussianNameCases.derive(fio);
    }

    @Transactional(readOnly = true)
    public byte[] employeeDataSheet(Long teacherId) {
        TeacherDirectoryEntry teacher = teachers.findById(teacherId).orElseThrow();
        HrPersonalData personal = personalData.findByTeacherId(teacherId).orElse(null);
        NameCases cases = storedNameCases(teacher);
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var section = document.getDocument().getBody().addNewSectPr();
            var pageSize = section.addNewPgSz();
            pageSize.setW(java.math.BigInteger.valueOf(11906));
            pageSize.setH(java.math.BigInteger.valueOf(16838));
            var margins = section.addNewPgMar();
            margins.setLeft(java.math.BigInteger.valueOf(850));
            margins.setRight(java.math.BigInteger.valueOf(850));
            margins.setTop(java.math.BigInteger.valueOf(720));
            margins.setBottom(java.math.BigInteger.valueOf(720));
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontFamily("Calibri");
            titleRun.setFontSize(15);
            titleRun.setText("Лист проверки данных сотрудника");
            XWPFParagraph note = document.createParagraph();
            note.setAlignment(ParagraphAlignment.CENTER);
            run(note, "Проверьте сведения. Исправления внесите разборчиво рядом с соответствующей строкой.", 10, false);

            XWPFTable table = document.createTable(1, 2);
            table.setWidth("100%");
            table.setTableAlignment(TableRowAlign.CENTER);
            table.getCTTbl().getTblPr().addNewTblLayout().setType(
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType.FIXED);
            addRow(table, "ФИО — именительный падеж", cases.nominative());
            addRow(table, "ФИО — родительный падеж", cases.genitive());
            addRow(table, "ФИО — дательный падеж", cases.dative());
            addRow(table, "ФИО — винительный падеж", cases.accusative());
            addRow(table, "ФИО — творительный падеж", cases.instrumental());
            addRow(table, "ФИО — предложный падеж", cases.prepositional());
            addRow(table, "Фамилия и инициалы — именительный", cases.initials());
            addRow(table, "Фамилия и инициалы — родительный", cases.initialsGenitive());
            addRow(table, "Фамилия и инициалы — дательный", cases.initialsDative());
            addRow(table, "Фамилия и инициалы — винительный", cases.initialsAccusative());
            addRow(table, "Фамилия и инициалы — творительный", cases.initialsInstrumental());
            addRow(table, "Фамилия и инициалы — предложный", cases.initialsPrepositional());
            addRow(table, "Дата рождения", date(personal == null ? null : personal.getBirthDate()));
            addRow(table, "Телефон", first(teacher.getPhone(), personal == null ? null : personal.getPhone()));
            addRow(table, "Email", teacher.getEmail());
            addRow(table, "Корпус", teacher.getNumberSchoolBuilding());
            addRow(table, "Основная должность", teacher.getPrimaryPosition());
            addRow(table, "Вид занятости", teacher.getEmploymentType());
            addRow(table, "Дата приёма", date(teacher.getEmploymentDate()));
            addRow(table, "Паспорт", personal == null ? "" :
                    first(personal.getPassportSeries(), "") + " " + first(personal.getPassportNumber(), ""));
            addRow(table, "Кем выдан", personal == null ? "" : personal.getPassportIssuedBy());
            addRow(table, "Дата выдачи / код подразделения", personal == null ? "" :
                    date(personal.getPassportIssueDate()) + " / " + first(personal.getPassportDepartmentCode(), ""));
            addRow(table, "Адрес регистрации", personal == null ? "" : personal.getRegistrationAddress());
            addRow(table, "Фактический адрес", personal == null ? "" : personal.getActualAddress());
            addRow(table, "ИНН", personal == null ? "" : personal.getInn());
            addRow(table, "СНИЛС", personal == null ? "" : personal.getSnils());
            table.removeRow(0);

            XWPFParagraph confirmation = document.createParagraph();
            confirmation.setSpacingBefore(240);
            run(confirmation, "Сведения проверил(а), замечания указал(а):", 11, false);
            XWPFParagraph signature = document.createParagraph();
            signature.setSpacingBefore(360);
            run(signature, "__________________ / " + cases.initials() + " /     «____» __________ 20____ г.", 11, false);
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось сформировать лист проверки данных", exception);
        }
    }

    private NameCases mergeNameCases(String fio, NameCases requested) {
        NameCases generated = RussianNameCases.derive(fio);
        if (requested == null) return generated;
        return new NameCases(
                fio,
                first(requested.genitive(), generated.genitive()),
                first(requested.dative(), generated.dative()),
                first(requested.accusative(), generated.accusative()),
                first(requested.instrumental(), generated.instrumental()),
                first(requested.prepositional(), generated.prepositional()),
                first(requested.initials(), generated.initials()),
                first(requested.initialsGenitive(), generated.initialsGenitive()),
                first(requested.initialsDative(), generated.initialsDative()),
                first(requested.initialsAccusative(), generated.initialsAccusative()),
                first(requested.initialsInstrumental(), generated.initialsInstrumental()),
                first(requested.initialsPrepositional(), generated.initialsPrepositional())
        );
    }

    private NameCases storedNameCases(TeacherDirectoryEntry teacher) {
        NameCases generated = RussianNameCases.derive(teacher.getFioTeacher());
        return new NameCases(
                teacher.getFioTeacher(),
                first(teacher.getFioTeacherGenitive(), generated.genitive()),
                first(teacher.getFioTeacherDative(), generated.dative()),
                first(teacher.getFioTeacherAccusative(), generated.accusative()),
                first(teacher.getFioTeacherInstrumental(), generated.instrumental()),
                first(teacher.getFioTeacherPrepositional(), generated.prepositional()),
                first(teacher.getInitials(), generated.initials()),
                first(teacher.getInitialsGenitive(), generated.initialsGenitive()),
                first(teacher.getInitialsDative(), generated.initialsDative()),
                first(teacher.getInitialsAccusative(), generated.initialsAccusative()),
                first(teacher.getInitialsInstrumental(), generated.initialsInstrumental()),
                first(teacher.getInitialsPrepositional(), generated.initialsPrepositional())
        );
    }

    private void applyNameCases(TeacherDirectoryEntry teacher, NameCases cases) {
        teacher.setFioTeacher(cases.nominative());
        teacher.setFioTeacherGenitive(cases.genitive());
        teacher.setFioTeacherDative(cases.dative());
        teacher.setFioTeacherAccusative(cases.accusative());
        teacher.setFioTeacherInstrumental(cases.instrumental());
        teacher.setFioTeacherPrepositional(cases.prepositional());
        teacher.setInitials(cases.initials());
        teacher.setInitialsGenitive(cases.initialsGenitive());
        teacher.setInitialsDative(cases.initialsDative());
        teacher.setInitialsAccusative(cases.initialsAccusative());
        teacher.setInitialsInstrumental(cases.initialsInstrumental());
        teacher.setInitialsPrepositional(cases.initialsPrepositional());
    }

    private void updateNameSnapshots(Long teacherId, String oldName, String newName) {
        loadRows.findByTeacherId(teacherId).forEach(row -> {
            row.setFioTeacher(newName);
            loadRows.save(row);
        });
        classroomLeadership.findAll().stream()
                .filter(row -> Objects.equals(row.getTeacherId(), teacherId)).forEach(row -> {
                    row.setFioTeacher(newName);
                    classroomLeadership.save(row);
                });
        loadMemos.findAll().stream().filter(row -> Objects.equals(row.getTeacherId(), teacherId)).forEach(row -> {
            row.setFioTeacher(newName);
            loadMemos.save(row);
        });
        mckoCertificates.findAllByTeacherId(teacherId).forEach(row -> {
            row.setTeacherFioSnapshot(newName);
            mckoCertificates.save(row);
        });
        String normalized = normalizeFio(newName);
        paSpecifications.findAll().stream().filter(row -> sameFio(row.getTeacherFio(), oldName)).forEach(row -> {
            row.setTeacherFio(newName);
            row.setTeacherFioNormalized(normalized);
            paSpecifications.save(row);
        });
        paReportVersions.findAll().stream().filter(row -> sameFio(row.getTeacherFio(), oldName)).forEach(row -> {
            row.setTeacherFio(newName);
            row.setTeacherFioNormalized(normalized);
            paReportVersions.save(row);
        });
        paSummaries.findAll().stream().filter(row -> sameFio(row.getTeacherFio(), oldName)).forEach(row -> {
            row.setTeacherFio(newName);
            paSummaries.save(row);
        });
        paStudentResults.findAll().stream().filter(row -> sameFio(row.getTeacherFio(), oldName)).forEach(row -> {
            row.setTeacherFio(newName);
            paStudentResults.save(row);
        });
    }

    private String shortDuty(HrServiceMemo memo) {
        String value = first(memo.getAssignmentName(), memo.getTitle());
        value = value.replaceFirst("(?iu)^о\\s+назначении\\s*:?\\s*", "").trim();
        return value.length() > 90 ? value.substring(0, 87) + "…" : value;
    }

    private boolean isVacancy(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).startsWith("вакансия");
    }

    private boolean sameFio(String first, String second) {
        return normalizeFio(first).equals(normalizeFio(second));
    }

    private String normalizeFio(String value) {
        return Objects.toString(value, "").trim().replace('ё', 'е').replace('Ё', 'Е')
                .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return present(value) ? value.trim() : null;
    }

    private String first(String... values) {
        return Arrays.stream(values).filter(this::present).findFirst().orElse("");
    }

    private String date(java.time.LocalDate value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private void addRow(XWPFTable table, String label, String value) {
        XWPFTableRow row = table.createRow();
        row.setCantSplitRow(true);
        for (int index = 0; index < 2; index++) {
            XWPFTableCell cell = row.getCell(index);
            CTTblWidth width = cell.getCTTc().addNewTcPr().addNewTcW();
            width.setType(STTblWidth.PCT);
            width.setW(java.math.BigInteger.valueOf(2500));
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            XWPFParagraph paragraph = cell.getParagraphs().get(0);
            paragraph.setSpacingBefore(40);
            paragraph.setSpacingAfter(40);
            run(paragraph, index == 0 ? label : first(value, "____________________________"),
                    10, index == 0);
        }
    }

    private void run(XWPFParagraph paragraph, String text, int size, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Calibri");
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(text);
    }
}
