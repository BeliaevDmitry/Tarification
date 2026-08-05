package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.contingent.StudentDataExchangeDtos;
import org.school.personalLoad.dto.contingent.StudentSupportDtos;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.StudentDataExchangeService;
import org.school.personalLoad.service.StudentSupportService;
import org.school.personalLoad.service.IupLoadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentDataExchangeServiceImpl implements StudentDataExchangeService {

    private static final String SHEET_INSTRUCTIONS = "Инструкция";
    private static final String SHEET_CHILDREN = "Дети";
    private static final String SHEET_NOSOLOGIES = "Нозологии";
    private static final String SHEET_STATUSES = "Статусы";
    private static final String SHEET_DOCUMENTS = "Документы";
    private static final String SHEET_IUPS = "ИУП";
    private static final String SHEET_IUP_SUBJECTS = "Предметы ИУП";
    private static final String SHEET_IUP_TEACHERS = "Педагоги ИУП";
    private static final String SHEET_MESH_NAMES = "Названия УП-МЭШ";
    private static final String SHEET_DISTRIBUTION = "Распределение";
    private static final String SHEET_TEACHERS = "Справочник педагогов";
    private static final String SHEET_READINESS = "Контроль готовности";
    private static final String SHEET_PROJECTION = "Проекция групп";

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository contingentStudentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentSupportStatusRepository supportStatusRepository;
    private final StudentSupportDocumentRepository supportDocumentRepository;
    private final StudentSupportDocumentAttachmentRepository supportDocumentAttachmentRepository;
    private final NosologyCatalogEntryRepository nosologyRepository;
    private final IupPlanRepository iupPlanRepository;
    private final IupSubjectLineRepository iupSubjectLineRepository;
    private final IupTeacherAssignmentRepository iupTeacherAssignmentRepository;
    private final StudentGroupMembershipRepository groupMembershipRepository;
    private final CurriculumPlanEntryRepository curriculumRepository;
    private final CurriculumMeshMappingRepository meshMappingRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentSupportService studentSupportService;
    private final IupLoadService iupLoadService;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportPackage(String academicYear) {
        ExportContext context = exportContext(academicYear);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            WorkbookStyles styles = styles(workbook);
            writeInstructions(workbook, academicYear, styles);
            writeChildren(workbook, context, styles);
            writeNosologies(workbook, styles);
            writeStatuses(workbook, academicYear, context, styles);
            writeDocuments(workbook, academicYear, context, styles);
            writeIups(workbook, academicYear, styles);
            writeMeshNames(workbook, academicYear, styles);
            writeDistribution(workbook, academicYear, context, styles);
            writeTeachers(workbook, styles);
            StudentDataExchangeDtos.ReadinessResponse readiness = readiness(academicYear);
            writeProjection(workbook, readiness, styles);
            writeReadiness(workbook, readiness, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сформировать пакет обмена: " + exception.getMessage(), exception);
        }
    }

    @Override
    public StudentDataExchangeDtos.ImportResult importPackage(String academicYear, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите Excel-файл");
        }
        ImportAccumulator result = new ImportAccumulator();
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            importNosologies(workbook, result);
            importStatuses(workbook, academicYear, result);
            importDocuments(workbook, academicYear, result);
            importIups(workbook, academicYear, result);
            importMeshNames(workbook, academicYear, result);
            importDistribution(workbook, academicYear, result);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Не удалось прочитать Excel-файл: " + exception.getMessage(), exception);
        }
        return result.toResponse();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDataExchangeDtos.ReadinessResponse readiness(String academicYear) {
        StudentDataExchangeDtos.ReadinessResponse response = new StudentDataExchangeDtos.ReadinessResponse();
        List<String> blockers = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        ContingentSnapshot snapshot = snapshotRepository
                .findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .orElse(null);
        if (snapshot == null) {
            response.setCalculationMode("Резервный режим: текущая логика расчёта");
            blockers.add("Не загружен контингент на учебный год.");
            response.setBlockers(blockers);
            response.setNotes(List.of("До появления специальных статусов все дети считаются категорией «Норма»."));
            return response;
        }

        List<ContingentStudent> rows = contingentStudentRepository.findAllBySnapshotId(snapshot.getId());
        if (rows.isEmpty()) {
            blockers.add("Загруженный снимок контингента не содержит детей.");
        }
        Set<Long> linkedStudentIds = rows.stream()
                .map(ContingentStudent::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long linkedRows = rows.stream().filter(row -> row.getStudentId() != null).count();
        long duplicateLinkedRows = Math.max(0, linkedRows - linkedStudentIds.size());
        if (duplicateLinkedRows > 0) {
            blockers.add("Обнаружено задвоение детей в контингенте: " + duplicateLinkedRows + ".");
        }
        LocalDate asOf = snapshot.getSnapshotDate();
        Map<Long, IupPlan> activeIups = activeIups(academicYear, asOf);
        List<StudentGroupMembership> memberships = groupMembershipRepository.findAllByAcademicYear(academicYear);
        Map<String, List<StudentGroupMembership>> membershipsByScope = memberships.stream()
                .filter(membership -> contains(membership.getValidFrom(), membership.getValidTo(), asOf))
                .collect(Collectors.groupingBy(this::membershipScopeKey));

        Map<String, List<CurriculumPlanEntry>> subgroupEntriesByClass = curriculumRepository
                .findAllByAcademicYear(academicYear).stream()
                .filter(entry -> !entry.isDeprecated()
                        && entry.isSubgroupRequired()
                        && entry.getMetaGroupId() == null)
                .collect(Collectors.groupingBy(entry -> classKey(entry.getClassName())));
        Map<Long, ContingentStudent> sourceByStudent = rows.stream()
                .filter(row -> row.getStudentId() != null)
                .collect(Collectors.toMap(
                        ContingentStudent::getStudentId,
                        Function.identity(),
                        (left, right) -> left
                ));

        int expected = 0;
        int completed = 0;
        int missing = 0;
        int duplicates = 0;
        for (Long studentId : linkedStudentIds) {
            IupPlan activeIup = activeIups.get(studentId);
            if (activeIup == null) {
                ContingentStudent source = sourceByStudent.get(studentId);
                for (CurriculumPlanEntry entry : subgroupEntriesByClass.getOrDefault(
                        classKey(source == null ? "" : source.getClassName()),
                        List.of()
                )) {
                    expected++;
                    List<StudentGroupMembership> matches = membershipsByScope.getOrDefault(
                            membershipScopeKey(studentId, entry.getId(), null),
                            List.of()
                    );
                    if (matches.isEmpty()) {
                        missing++;
                    } else {
                        completed++;
                        if (matches.size() > 1) {
                            duplicates += matches.size() - 1;
                        }
                    }
                }
            } else {
                for (IupSubjectLine line : iupSubjectLineRepository
                        .findAllByIupPlan_IdOrderBySubjectNameAsc(activeIup.getId())) {
                    if (line.getCurriculumEntryId() == null || !attendsClass(line.getParticipationMode())) {
                        continue;
                    }
                    CurriculumPlanEntry entry = curriculumRepository.findById(line.getCurriculumEntryId()).orElse(null);
                    if (entry == null || !entry.isSubgroupRequired()) {
                        continue;
                    }
                    expected++;
                    List<StudentGroupMembership> matches = membershipsByScope.getOrDefault(
                            membershipScopeKey(studentId, entry.getId(), entry.getMetaGroupId()),
                            List.of()
                    );
                    if (matches.isEmpty()) {
                        missing++;
                    } else {
                        completed++;
                        if (matches.size() > 1) {
                            duplicates += matches.size() - 1;
                        }
                    }
                }
            }
        }

        int unlinked = (int) rows.stream().filter(row -> row.getStudentId() == null).count();
        if (unlinked > 0) {
            blockers.add("Не связаны с постоянными карточками: " + unlinked + ".");
        }
        if (missing > 0) {
            blockers.add("Не заполнено распределение по подгруппам: " + missing + ".");
        }
        if (duplicates > 0) {
            blockers.add("Обнаружены дубли распределения: " + duplicates + ".");
        }
        Set<Long> entriesWithMemberships = memberships.stream()
                .filter(membership -> membership.getCurriculumEntryId() != null)
                .filter(membership -> contains(membership.getValidFrom(), membership.getValidTo(), asOf))
                .map(StudentGroupMembership::getCurriculumEntryId)
                .collect(Collectors.toSet());
        long emptyMetaGroups = activeCurriculumEntries(academicYear).stream()
                .filter(entry -> entry.getMetaGroupId() != null)
                .map(CurriculumPlanEntry::getId)
                .filter(Objects::nonNull)
                .filter(entryId -> !entriesWithMemberships.contains(entryId))
                .count();
        if (emptyMetaGroups > 0) {
            blockers.add("Не загружено распределение для метагрупп учебного плана: " + emptyMetaGroups + ".");
        }
        if (nosologyRepository.count() == 0) {
            notes.add("Справочник нозологий пока пуст. Это не мешает считать детей без данных категорией «Норма».");
        }
        notes.add("Если отдельного соответствия УП↔МЭШ нет, используется название из учебного плана.");
        boolean readyForCutover = blockers.isEmpty();
        response.setCalculationMode(readyForCutover
                ? "Фактический контингент применяется автоматически"
                : "Резервный режим: текущая логика расчёта");
        notes.add(readyForCutover
                ? "Нагрузка и зарплата используют фактическую численность классов и подгрупп."
                : "До устранения блокирующих ошибок нагрузка и зарплата используют прежнюю логику численности.");

        response.setSnapshotId(snapshot.getId());
        response.setSnapshotDate(snapshot.getSnapshotDate());
        response.setTotalStudents(rows.size());
        response.setLinkedStudents(linkedStudentIds.size());
        response.setUnlinkedStudents(unlinked);
        response.setNosologies((int) nosologyRepository.count());
        response.setActiveIups(activeIups.size());
        response.setExpectedGroupAssignments(expected);
        response.setCompletedGroupAssignments(completed);
        response.setMissingGroupAssignments(missing);
        response.setDuplicateGroupAssignments(duplicates);
        response.setExplicitMeshNameMappings(meshMappingRepository.findAllByAcademicYear(academicYear).size());
        response.setReadyForStudentCountCutover(readyForCutover);
        response.setBlockers(blockers);
        response.setNotes(notes);
        response.setGroupProjection(groupProjection(academicYear, asOf, linkedStudentIds, memberships));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDataExchangeService.StudentCountResolution resolveStudentCounts(
            String academicYear,
            Collection<ManualLoadEntry> rows
    ) {
        List<ManualLoadEntry> requestedRows = Optional.ofNullable(rows).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(row -> !row.isIupLoad())
                .filter(row -> row.getId() != null)
                .toList();
        StudentDataExchangeDtos.ReadinessResponse state = readiness(academicYear);
        if (!state.isReadyForStudentCountCutover() || requestedRows.isEmpty()) {
            return new StudentDataExchangeService.StudentCountResolution(
                    false,
                    state.getCalculationMode(),
                    Map.of()
            );
        }

        ContingentSnapshot snapshot = snapshotRepository
                .findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .orElse(null);
        if (snapshot == null) {
            return new StudentDataExchangeService.StudentCountResolution(
                    false,
                    "Резервный режим: текущая логика расчёта",
                    Map.of()
            );
        }

        LocalDate asOf = snapshot.getSnapshotDate();
        List<ContingentStudent> contingent = contingentStudentRepository.findAllBySnapshotId(snapshot.getId());
        Set<Long> currentStudentIds = contingent.stream()
                .map(ContingentStudent::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, StudentCategory> categoryByStudent = supportStatusRepository
                .findAllByAcademicYear(academicYear).stream()
                .filter(status -> status.getStudent() != null)
                .filter(status -> contains(status.getValidFrom(), status.getValidTo(), asOf))
                .sorted(Comparator.comparing(
                        StudentSupportStatus::getValidFrom,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .collect(Collectors.toMap(
                        status -> status.getStudent().getId(),
                        status -> Objects.requireNonNullElse(status.getCategory(), StudentCategory.NORMAL),
                        (first, second) -> second,
                        LinkedHashMap::new
                ));
        Map<Long, IupPlan> activeIupByStudent = activeIups(academicYear, asOf);
        Map<Long, Set<Long>> classAttendanceByStudent = new HashMap<>();
        for (Map.Entry<Long, IupPlan> item : activeIupByStudent.entrySet()) {
            Set<Long> curriculumIds = iupSubjectLineRepository
                    .findAllByIupPlan_IdOrderBySubjectNameAsc(item.getValue().getId()).stream()
                    .filter(line -> attendsClass(line.getParticipationMode()))
                    .map(IupSubjectLine::getCurriculumEntryId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            classAttendanceByStudent.put(item.getKey(), curriculumIds);
        }

        List<StudentGroupMembership> activeMemberships = groupMembershipRepository
                .findAllByAcademicYear(academicYear).stream()
                .filter(membership -> membership.getStudent() != null)
                .filter(membership -> currentStudentIds.contains(membership.getStudent().getId()))
                .filter(membership -> contains(membership.getValidFrom(), membership.getValidTo(), asOf))
                .toList();
        List<CurriculumPlanEntry> curriculum = activeCurriculumEntries(academicYear);
        Map<Long, Integer> result = new LinkedHashMap<>();

        for (ManualLoadEntry row : requestedRows) {
            List<CurriculumPlanEntry> matchingEntries = matchingCurriculumEntries(row, curriculum);
            if (matchingEntries.isEmpty()) {
                continue;
            }
            Set<Long> entryIds = matchingEntries.stream()
                    .map(CurriculumPlanEntry::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            int children;
            String groupName = normalizeGroup(row.getGroupNameEducationalPlan());
            if (!groupName.isBlank() || row.getMetaGroupId() != null) {
                children = activeMemberships.stream()
                        .filter(membership -> entryIds.contains(membership.getCurriculumEntryId()))
                        .filter(membership -> row.getMetaGroupId() == null
                                || Objects.equals(row.getMetaGroupId(), membership.getMetaGroupId()))
                        .filter(membership -> groupName.isBlank()
                                || equalsNormalized(groupName, membership.getGroupNameEducationalPlan()))
                        .filter(membership -> {
                            Long studentId = membership.getStudent().getId();
                            IupPlan iup = activeIupByStudent.get(studentId);
                            return iup == null || classAttendanceByStudent
                                    .getOrDefault(studentId, Set.of()).stream()
                                    .anyMatch(entryIds::contains);
                        })
                        .map(membership -> membership.getStudent().getId())
                        .distinct()
                        .mapToInt(studentId -> calculationWeight(categoryByStudent.get(studentId)))
                        .sum();
            } else {
                String classKey = classKey(row.getClassName());
                children = contingent.stream()
                        .filter(student -> student.getStudentId() != null)
                        .filter(student -> classKey(student.getClassName()).equals(classKey))
                        .filter(student -> {
                            IupPlan iup = activeIupByStudent.get(student.getStudentId());
                            if (iup == null) {
                                return true;
                            }
                            return classAttendanceByStudent
                                    .getOrDefault(student.getStudentId(), Set.of()).stream()
                                    .anyMatch(entryIds::contains);
                        })
                        .map(ContingentStudent::getStudentId)
                        .distinct()
                        .mapToInt(studentId -> calculationWeight(categoryByStudent.get(studentId)))
                        .sum();
            }
            result.put(row.getId(), Math.max(children, 0));
        }

        return new StudentDataExchangeService.StudentCountResolution(
                true,
                state.getCalculationMode(),
                result
        );
    }

    private int calculationWeight(StudentCategory category) {
        if (category == StudentCategory.K2) {
            return 2;
        }
        if (category == StudentCategory.K3) {
            return 3;
        }
        return 1;
    }

    private void writeInstructions(Workbook workbook, String academicYear, WorkbookStyles styles) {
        Sheet sheet = workbook.createSheet(SHEET_INSTRUCTIONS);
        sheet.setDisplayGridlines(false);
        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Пакет данных по контингенту, ИУП и распределению — " + academicYear);
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        List<List<Object>> rows = List.of(
                List.of("Правило", "Описание"),
                List.of("Обратный импорт", "Файл можно выгрузить, заполнить и загрузить обратно без изменения названий листов и колонок."),
                List.of("Действие", "UPSERT — создать или обновить; DELETE — удалить допустимую запись; ПРИМЕР — строка не импортируется."),
                List.of("Норма по умолчанию", "Если на листе «Статусы» нет записи о ребёнке, он считается категорией «Норма»."),
                List.of("Названия МЭШ", "Если название МЭШ пустое или отдельной связи нет, используется название из УП."),
                List.of("ИУП и подгруппы", "Ребёнок с действующим ИУП не распределяется автоматически. Строка группы нужна только для предмета, который он посещает с классом."),
                List.of("Документы", "Лист «Документы» переносит реквизиты МСЭ, ИПР/ИПРА, ЦПМПК, ППк, ИОМ и других документов. Файлы-копии в Excel не вкладываются: их прикрепляют в карточке документа."),
                List.of("Идентификаторы", "Не меняйте заполненные ID. Для новых строк допустимо оставить ID пустым и использовать указанные ключи."),
                List.of("Даты", "Рекомендуемый формат: ГГГГ-ММ-ДД."),
                List.of("Расчёт", "До успешного контроля действует прежняя логика. После контроля фактическая численность применяется автоматически.")
        );
        int rowIndex = 2;
        for (List<Object> values : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int column = 0; column < values.size(); column++) {
                Cell cell = row.createCell(column);
                cell.setCellValue(String.valueOf(values.get(column)));
                cell.setCellStyle(rowIndex == 3 ? styles.header() : styles.wrap());
            }
        }
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 100 * 256);
    }

    private void writeChildren(Workbook workbook, ExportContext context, WorkbookStyles styles) {
        List<String> headers = List.of(
                "Карточка ID", "Личное дело", "ФИО", "Дата рождения", "Класс",
                "Статус сопоставления", "Дата снимка"
        );
        List<List<Object>> rows = context.sourceRows().stream().map(source -> {
            StudentProfile profile = context.profiles().get(source.getStudentId());
            return List.of(
                    nullable(source.getStudentId()),
                    nullable(source.getRecordNumber()),
                    nullable(source.getFullName()),
                    nullable(profile == null ? parseDate(source.getBirthDate()) : profile.getBirthDate()),
                    nullable(source.getClassName()),
                    nullable(source.getIdentityMatchStatus()),
                    context.snapshot().getSnapshotDate()
            );
        }).toList();
        writeTable(workbook, SHEET_CHILDREN, headers, rows, styles, true);
    }

    private void writeNosologies(Workbook workbook, WorkbookStyles styles) {
        List<String> headers = List.of(
                "Действие", "ID", "Код", "Наименование", "Категория ОВЗ", "Вариант АООП",
                "Коэффициент", "Дата с", "Дата по", "Активна", "Комментарий"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (NosologyCatalogEntry entry : nosologyRepository.findAllByOrderByCodeAsc()) {
            rows.add(List.of(
                    "UPSERT", entry.getId(), entry.getCode(), entry.getName(),
                    nullable(entry.getOvzCategory()), nullable(entry.getAoopVariant()),
                    entry.getStudentCategory().name(), nullable(entry.getValidFrom()),
                    nullable(entry.getValidTo()), entry.isActive(), nullable(entry.getComment())
            ));
        }
        if (rows.isEmpty()) {
            rows.add(List.of(
                    "ПРИМЕР", "", "8.2", "РАС с задержкой психического развития",
                    "ОВЗ", "АООП 8.2", "K3", "", "", true, "Условный пример — замените утверждёнными данными"
            ));
        }
        writeTable(workbook, SHEET_NOSOLOGIES, headers, rows, styles, true);
    }

    private void writeStatuses(Workbook workbook,
                               String academicYear,
                               ExportContext context,
                               WorkbookStyles styles) {
        List<String> headers = List.of(
                "Действие", "ID статуса", "Карточка ID", "Личное дело", "ФИО", "Дата рождения", "Класс",
                "Категория", "Код нозологии", "Вариант АООП", "Дата с", "Дата по", "Комментарий"
        );
        Map<Long, ContingentStudent> sourceByStudent = context.sourceRows().stream()
                .filter(row -> row.getStudentId() != null)
                .collect(Collectors.toMap(ContingentStudent::getStudentId, Function.identity(), (a, b) -> a));
        List<List<Object>> rows = supportStatusRepository.findAllByAcademicYear(academicYear).stream()
                .sorted(Comparator.comparing(StudentSupportStatus::getValidFrom))
                .map(status -> {
                    ContingentStudent source = sourceByStudent.get(status.getStudent().getId());
                    return List.of(
                            "UPSERT", status.getId(), status.getStudent().getId(),
                            nullable(source == null ? status.getStudent().getRecordNumber() : source.getRecordNumber()),
                            status.getStudent().getCurrentFullName(),
                            nullable(status.getStudent().getBirthDate()),
                            nullable(source == null ? "" : source.getClassName()),
                            status.getCategory().name(),
                            nullable(status.getNosologyCodeSnapshot()),
                            nullable(status.getAoopVariantSnapshot()),
                            status.getValidFrom(), nullable(status.getValidTo()), nullable(status.getComment())
                    );
                }).toList();
        writeTable(workbook, SHEET_STATUSES, headers, rows, styles, true);
    }

    private void writeDocuments(Workbook workbook,
                                String academicYear,
                                ExportContext context,
                                WorkbookStyles styles) {
        List<String> headers = List.of(
                "Действие", "Документ ID", "Карточка ID", "Личное дело", "ФИО", "Дата рождения", "Класс",
                "Вид документа", "Форма приёма", "Номер", "Дата выдачи", "Дата с", "Дата по",
                "Кем выдан", "Дата приёма", "Ответственный", "Комментарий", "Прикреплено файлов"
        );
        Map<Long, ContingentStudent> sourceByStudent = context.sourceRows().stream()
                .filter(row -> row.getStudentId() != null)
                .collect(Collectors.toMap(ContingentStudent::getStudentId, Function.identity(), (a, b) -> a));
        List<List<Object>> rows = new ArrayList<>();
        for (StudentSupportDocument document :
                supportDocumentRepository.findAllByAcademicYearOrderByValidToAscStudent_CurrentFullNameAsc(
                        academicYear
                )) {
            ContingentStudent source = sourceByStudent.get(document.getStudent().getId());
            int attachments = supportDocumentAttachmentRepository
                    .findAllByDocument_IdOrderByUploadedAtAsc(document.getId()).size();
            rows.add(List.of(
                    "UPSERT", document.getId(), document.getStudent().getId(),
                    nullable(source == null
                            ? document.getStudent().getRecordNumber()
                            : source.getRecordNumber()),
                    document.getStudent().getCurrentFullName(),
                    nullable(document.getStudent().getBirthDate()),
                    nullable(source == null
                            ? currentClassName(document.getStudent().getId(), academicYear)
                            : source.getClassName()),
                    document.getDocumentType().name(),
                    document.getAcceptedForm().name(),
                    nullable(document.getDocumentNumber()),
                    nullable(document.getIssueDate()),
                    nullable(document.getValidFrom()),
                    nullable(document.getValidTo()),
                    nullable(document.getIssuingOrganization()),
                    document.getReceivedAt(),
                    nullable(document.getResponsibleEmployee()),
                    nullable(document.getComment()),
                    attachments
            ));
        }
        if (rows.isEmpty()) {
            rows.add(List.of(
                    "ПРИМЕР", "", "", "ЛД-1", "Иванов Иван Иванович", "2015-01-01", "5-А",
                    "MSE_CERTIFICATE", "COPY", "МСЭ-001", "2026-09-01", "2026-09-01",
                    "2027-08-31", "Бюро МСЭ", "2026-09-02", "Ответственный сотрудник",
                    "Условный пример — строка не импортируется", 0
            ));
        }
        writeTable(workbook, SHEET_DOCUMENTS, headers, rows, styles, true);
    }

    private void writeIups(Workbook workbook, String academicYear, WorkbookStyles styles) {
        List<IupPlan> plans = iupPlanRepository.findAllByAcademicYear(academicYear).stream()
                .sorted(Comparator.comparing((IupPlan plan) -> plan.getStudent().getCurrentFullName())
                        .thenComparing(IupPlan::getValidFrom))
                .toList();
        List<String> planHeaders = List.of(
                "Действие", "ИУП ID", "Ключ ИУП", "Карточка ID", "ФИО", "Дата рождения", "Статус",
                "Номер приказа", "Дата приказа", "Дата с", "Дата по", "Комментарий"
        );
        List<List<Object>> planRows = plans.stream().map(plan -> List.of(
                "UPSERT", plan.getId(), iupKey(plan), plan.getStudent().getId(),
                plan.getStudent().getCurrentFullName(), nullable(plan.getStudent().getBirthDate()), plan.getStatus().name(),
                nullable(plan.getOrderNumber()), nullable(plan.getOrderDate()), plan.getValidFrom(),
                nullable(plan.getValidTo()), nullable(plan.getComment())
        )).toList();
        writeTable(workbook, SHEET_IUPS, planHeaders, planRows, styles, true);

        List<String> subjectHeaders = List.of(
                "Действие", "ИУП ID", "Ключ ИУП", "Строка ID", "Ключ строки",
                "Строка УП ID", "Предмет УП", "Участие", "Часы с классом",
                "Индивидуальные часы", "Группа УП"
        );
        List<List<Object>> subjectRows = new ArrayList<>();
        List<String> teacherHeaders = List.of(
                "Действие", "ИУП ID", "Ключ ИУП", "Строка ID", "Ключ строки",
                "Педагог ID", "Педагог", "Часы", "Форма", "Дата с", "Дата по"
        );
        List<List<Object>> teacherRows = new ArrayList<>();
        for (IupPlan plan : plans) {
            for (IupSubjectLine line : iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(plan.getId())) {
                subjectRows.add(List.of(
                        "UPSERT", plan.getId(), iupKey(plan), line.getId(), subjectKey(line),
                        nullable(line.getCurriculumEntryId()), line.getSubjectName(),
                        line.getParticipationMode().name(), line.getClassHours(), line.getIndividualHours(),
                        nullable(line.getGroupNameEducationalPlan())
                ));
                for (IupTeacherAssignment assignment :
                        iupTeacherAssignmentRepository.findAllBySubjectLine_IupPlan_Id(plan.getId())) {
                    if (!Objects.equals(assignment.getSubjectLine().getId(), line.getId())) {
                        continue;
                    }
                    teacherRows.add(List.of(
                            "UPSERT", plan.getId(), iupKey(plan), line.getId(), subjectKey(line),
                            nullable(assignment.getTeacherId()), assignment.getTeacherFioSnapshot(),
                            assignment.getHoursPerWeek(), assignment.getDeliveryForm().name(),
                            assignment.getValidFrom(), nullable(assignment.getValidTo())
                    ));
                }
            }
        }
        writeTable(workbook, SHEET_IUP_SUBJECTS, subjectHeaders, subjectRows, styles, true);
        writeTable(workbook, SHEET_IUP_TEACHERS, teacherHeaders, teacherRows, styles, true);
    }

    private void writeMeshNames(Workbook workbook, String academicYear, WorkbookStyles styles) {
        List<String> headers = List.of(
                "Действие", "ID связи", "Строка УП ID", "Предмет УП", "Класс/метагруппа УП",
                "Группа УП", "Предмет МЭШ", "Класс/метагруппа МЭШ", "Группа МЭШ", "Подтверждено"
        );
        Map<String, CurriculumMeshMapping> existing = meshMappingRepository.findAllByAcademicYear(academicYear)
                .stream()
                .collect(Collectors.toMap(this::mappingKey, Function.identity(), (a, b) -> a));
        List<List<Object>> rows = new ArrayList<>();
        for (CurriculumPlanEntry entry : activeCurriculumEntries(academicYear)) {
            List<String> groups = entry.isSubgroupRequired()
                    ? groupNames(entry.getSubgroupCount())
                    : List.of("");
            for (String group : groups) {
                CurriculumMeshMapping mapping = existing.get(mappingKey(entry.getId(), group));
                rows.add(List.of(
                        mapping == null ? "" : "UPSERT",
                        nullable(mapping == null ? null : mapping.getId()),
                        entry.getId(), entry.getSubjectName(), entry.getClassName(), group,
                        mapping == null ? entry.getSubjectName() : mapping.getSubjectNameMesh(),
                        mapping == null ? entry.getClassName() : mapping.getClassNameMesh(),
                        mapping == null ? group : mapping.getGroupNameMesh(),
                        mapping != null && mapping.isConfirmed()
                ));
            }
        }
        writeTable(workbook, SHEET_MESH_NAMES, headers, rows, styles, true);
    }

    private void writeDistribution(Workbook workbook,
                                   String academicYear,
                                   ExportContext context,
                                   WorkbookStyles styles) {
        List<String> headers = List.of(
                "Действие", "Распределение ID", "Карточка ID", "Личное дело", "ФИО", "Дата рождения", "Класс",
                "Тип", "Строка УП ID", "Предмет УП", "Предмет МЭШ",
                "Класс/метагруппа МЭШ", "Группа УП", "Группа МЭШ",
                "Дата с", "Дата по", "Источник"
        );
        LocalDate asOf = context.snapshot().getSnapshotDate();
        Map<Long, IupPlan> activeIups = activeIups(academicYear, asOf);
        List<StudentGroupMembership> memberships = groupMembershipRepository.findAllByAcademicYear(academicYear);
        Map<String, List<StudentGroupMembership>> membershipByScope = memberships.stream()
                .collect(Collectors.groupingBy(this::membershipScopeKey));
        Map<String, List<CurriculumPlanEntry>> entriesByClass = activeCurriculumEntries(academicYear).stream()
                .filter(entry -> entry.isSubgroupRequired() && entry.getMetaGroupId() == null)
                .collect(Collectors.groupingBy(entry -> classKey(entry.getClassName())));
        Map<Long, CurriculumPlanEntry> curriculumById = activeCurriculumEntries(academicYear).stream()
                .collect(Collectors.toMap(CurriculumPlanEntry::getId, Function.identity()));
        Map<String, CurriculumMeshMapping> mappings = meshMappingRepository.findAllByAcademicYear(academicYear)
                .stream()
                .collect(Collectors.toMap(this::mappingKey, Function.identity(), (left, right) -> left));
        List<List<Object>> rows = new ArrayList<>();
        Set<Long> exportedMembershipIds = new HashSet<>();

        for (ContingentStudent source : context.sourceRows()) {
            if (source.getStudentId() == null) {
                continue;
            }
            StudentProfile profile = context.profiles().get(source.getStudentId());
            IupPlan iup = activeIups.get(source.getStudentId());
            List<CurriculumPlanEntry> expectedEntries;
            if (iup == null) {
                expectedEntries = entriesByClass.getOrDefault(classKey(source.getClassName()), List.of());
            } else {
                expectedEntries = iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(iup.getId()).stream()
                        .filter(line -> attendsClass(line.getParticipationMode()))
                        .map(IupSubjectLine::getCurriculumEntryId)
                        .filter(Objects::nonNull)
                        .map(curriculumById::get)
                        .filter(Objects::nonNull)
                        .filter(CurriculumPlanEntry::isSubgroupRequired)
                        .toList();
            }
            for (CurriculumPlanEntry entry : expectedEntries) {
                List<StudentGroupMembership> matches = membershipByScope.getOrDefault(
                        membershipScopeKey(source.getStudentId(), entry.getId(), entry.getMetaGroupId()),
                        List.of()
                );
                StudentGroupMembership membership = matches.isEmpty() ? null : matches.get(0);
                if (membership != null) {
                    exportedMembershipIds.add(membership.getId());
                }
                rows.add(distributionRow(source, profile, entry, membership, mappings));
            }
        }
        for (StudentGroupMembership membership : memberships) {
            if (exportedMembershipIds.contains(membership.getId())) {
                continue;
            }
            ContingentStudent source = context.sourceRows().stream()
                    .filter(row -> Objects.equals(row.getStudentId(), membership.getStudent().getId()))
                    .findFirst()
                    .orElse(null);
            CurriculumPlanEntry entry = membership.getCurriculumEntryId() == null
                    ? null
                    : curriculumById.get(membership.getCurriculumEntryId());
            if (source != null && entry != null) {
                rows.add(distributionRow(source, membership.getStudent(), entry, membership, mappings));
            }
        }
        writeTable(workbook, SHEET_DISTRIBUTION, headers, rows, styles, true);
    }

    private List<Object> distributionRow(ContingentStudent source,
                                         StudentProfile profile,
                                         CurriculumPlanEntry entry,
                                         StudentGroupMembership membership,
                                         Map<String, CurriculumMeshMapping> mappings) {
        String groupUp = membership == null ? "" : membership.getGroupNameEducationalPlan();
        CurriculumMeshMapping mapping = mappings.get(mappingKey(entry.getId(), groupUp));
        String groupMesh = mapping == null ? groupUp : mapping.getGroupNameMesh();
        String subjectMesh = mapping == null ? entry.getSubjectName() : mapping.getSubjectNameMesh();
        String classMesh = mapping == null ? entry.getClassName() : mapping.getClassNameMesh();
        String type = entry.getMetaGroupId() == null ? "ПОДГРУППА" : "МЕТАГРУППА";
        return List.of(
                membership == null ? "" : "UPSERT",
                nullable(membership == null ? null : membership.getId()),
                source.getStudentId(), source.getRecordNumber(), source.getFullName(),
                nullable(profile == null ? parseDate(source.getBirthDate()) : profile.getBirthDate()),
                source.getClassName(),
                type, entry.getId(), entry.getSubjectName(), subjectMesh, classMesh, groupUp, groupMesh,
                nullable(membership == null ? null : membership.getValidFrom()),
                nullable(membership == null ? null : membership.getValidTo()),
                nullable(membership == null ? null : membership.getSource())
        );
    }

    private void writeTeachers(Workbook workbook, WorkbookStyles styles) {
        List<String> headers = List.of("Педагог ID", "ФИО", "Табельный номер", "Архив");
        List<List<Object>> rows = teacherRepository.findAll().stream()
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(teacher -> List.of(
                        teacher.getId(), teacher.getFioTeacher(),
                        nullable(teacher.getPersonnelNumber()), teacher.isArchived()
                ))
                .toList();
        writeTable(workbook, SHEET_TEACHERS, headers, rows, styles, true);
    }

    private void writeReadiness(Workbook workbook,
                                StudentDataExchangeDtos.ReadinessResponse readiness,
                                WorkbookStyles styles) {
        List<String> headers = List.of("Показатель", "Значение");
        List<List<Object>> rows = new ArrayList<>(List.of(
                List.of("Режим расчёта", readiness.getCalculationMode()),
                List.of("Дата контингента", nullable(readiness.getSnapshotDate())),
                List.of("Всего детей", readiness.getTotalStudents()),
                List.of("Связано карточек", readiness.getLinkedStudents()),
                List.of("Не связано", readiness.getUnlinkedStudents()),
                List.of("Активных ИУП", readiness.getActiveIups()),
                List.of("Ожидается распределений", readiness.getExpectedGroupAssignments()),
                List.of("Заполнено распределений", readiness.getCompletedGroupAssignments()),
                List.of("Не заполнено распределений", readiness.getMissingGroupAssignments()),
                List.of("Дубли распределений", readiness.getDuplicateGroupAssignments()),
                List.of("Фактическая численность применяется", readiness.isReadyForStudentCountCutover() ? "Да" : "Нет")
        ));
        for (String blocker : Optional.ofNullable(readiness.getBlockers()).orElse(List.of())) {
            rows.add(List.of("Блокирующая проверка", blocker));
        }
        for (String note : Optional.ofNullable(readiness.getNotes()).orElse(List.of())) {
            rows.add(List.of("Примечание", note));
        }
        writeTable(workbook, SHEET_READINESS, headers, rows, styles, false);
    }

    private void writeProjection(Workbook workbook,
                                 StudentDataExchangeDtos.ReadinessResponse readiness,
                                 WorkbookStyles styles) {
        List<String> headers = List.of(
                "Строка УП ID", "Класс/метагруппа", "Предмет", "Группа", "Количество детей"
        );
        List<List<Object>> rows = Optional.ofNullable(readiness.getGroupProjection()).orElse(List.of()).stream()
                .<List<Object>>map(item -> List.of(
                        item.getCurriculumEntryId(),
                        item.getClassOrMetaGroup(),
                        item.getSubjectName(),
                        item.getGroupName(),
                        item.getStudents()
                ))
                .toList();
        writeTable(workbook, SHEET_PROJECTION, headers, rows, styles, true);
    }

    private List<StudentDataExchangeDtos.GroupProjectionRow> groupProjection(
            String academicYear,
            LocalDate asOf,
            Set<Long> currentStudentIds,
            List<StudentGroupMembership> memberships
    ) {
        Map<String, Set<Long>> studentsByEntryGroup = new HashMap<>();
        memberships.stream()
                .filter(membership -> currentStudentIds.contains(membership.getStudent().getId()))
                .filter(membership -> membership.getCurriculumEntryId() != null)
                .filter(membership -> contains(membership.getValidFrom(), membership.getValidTo(), asOf))
                .forEach(membership -> studentsByEntryGroup
                        .computeIfAbsent(
                                membership.getCurriculumEntryId() + "|" + normalizeGroup(
                                        membership.getGroupNameEducationalPlan()
                                ),
                                ignored -> new HashSet<>()
                        )
                        .add(membership.getStudent().getId()));

        List<StudentDataExchangeDtos.GroupProjectionRow> result = new ArrayList<>();
        for (CurriculumPlanEntry entry : activeCurriculumEntries(academicYear)) {
            if (!entry.isSubgroupRequired() && entry.getMetaGroupId() == null) {
                continue;
            }
            Set<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if (entry.isSubgroupRequired()) {
                groups.addAll(groupNames(entry.getSubgroupCount()));
            }
            String prefix = entry.getId() + "|";
            studentsByEntryGroup.keySet().stream()
                    .filter(key -> key.startsWith(prefix))
                    .map(key -> key.substring(prefix.length()))
                    .filter(group -> !group.isBlank())
                    .forEach(groups::add);
            for (String group : groups) {
                StudentDataExchangeDtos.GroupProjectionRow row =
                        new StudentDataExchangeDtos.GroupProjectionRow();
                row.setCurriculumEntryId(entry.getId());
                row.setClassOrMetaGroup(entry.getClassName());
                row.setSubjectName(entry.getSubjectName());
                row.setGroupName(group);
                row.setStudents(studentsByEntryGroup
                        .getOrDefault(entry.getId() + "|" + normalizeGroup(group), Set.of())
                        .size());
                result.add(row);
            }
        }
        return result;
    }

    private void importNosologies(Workbook workbook, ImportAccumulator accumulator) {
        SheetTable table = table(workbook, SHEET_NOSOLOGIES);
        if (table == null) {
            return;
        }
        SheetCounter counter = accumulator.sheet(SHEET_NOSOLOGIES);
        for (int rowIndex = table.firstDataRow(); rowIndex <= table.lastRow(); rowIndex++) {
            int excelRow = rowIndex + 1;
            try {
                String action = action(table.text(rowIndex, "Действие"));
                if (skipAction(action)) {
                    counter.skipped++;
                    continue;
                }
                Long id = table.longValue(rowIndex, "ID");
                String code = trimToNull(table.text(rowIndex, "Код"));
                if (code == null) {
                    counter.skipped++;
                    continue;
                }
                NosologyCatalogEntry entry = id == null
                        ? nosologyRepository.findByCodeIgnoreCase(code).orElseGet(NosologyCatalogEntry::new)
                        : nosologyRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Нозология не найдена: " + id));
                if ("DELETE".equals(action)) {
                    nosologyRepository.delete(entry);
                    counter.deleted++;
                    continue;
                }
                StudentCategory category = parseCategory(table.text(rowIndex, "Коэффициент"));
                if (category == StudentCategory.NORMAL) {
                    throw new IllegalArgumentException("В справочнике нозологий коэффициент должен быть К2 или К3");
                }
                entry.setCode(code);
                entry.setName(required(table.text(rowIndex, "Наименование"), "Укажите наименование"));
                entry.setOvzCategory(trimToNull(table.text(rowIndex, "Категория ОВЗ")));
                entry.setAoopVariant(trimToNull(table.text(rowIndex, "Вариант АООП")));
                entry.setStudentCategory(category);
                entry.setValidFrom(table.date(rowIndex, "Дата с"));
                entry.setValidTo(table.date(rowIndex, "Дата по"));
                validateDates(entry.getValidFrom(), entry.getValidTo());
                entry.setActive(parseBoolean(table.text(rowIndex, "Активна"), true));
                entry.setComment(trimToNull(table.text(rowIndex, "Комментарий")));
                entry.setUpdatedAt(LocalDateTime.now());
                nosologyRepository.save(entry);
                counter.imported++;
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_NOSOLOGIES, excelRow, exception);
            }
        }
    }

    private void importStatuses(Workbook workbook,
                                String academicYear,
                                ImportAccumulator accumulator) {
        SheetTable table = table(workbook, SHEET_STATUSES);
        if (table == null) {
            return;
        }
        SheetCounter counter = accumulator.sheet(SHEET_STATUSES);
        for (int rowIndex = table.firstDataRow(); rowIndex <= table.lastRow(); rowIndex++) {
            int excelRow = rowIndex + 1;
            try {
                String action = action(table.text(rowIndex, "Действие"));
                if (skipAction(action) || blankRow(table, rowIndex, "Карточка ID", "ФИО", "Категория")) {
                    counter.skipped++;
                    continue;
                }
                Long statusId = table.longValue(rowIndex, "ID статуса");
                if ("DELETE".equals(action)) {
                    if (statusId == null) {
                        throw new IllegalArgumentException("Для удаления укажите ID статуса");
                    }
                    StudentSupportStatus deleted = supportStatusRepository.findById(statusId)
                            .orElseThrow(() -> new IllegalArgumentException("Статус не найден: " + statusId));
                    supportStatusRepository.deleteById(statusId);
                    iupLoadService.refreshStudentCategory(deleted.getStudent().getId(), academicYear);
                    counter.deleted++;
                    continue;
                }
                StudentProfile student = resolveStudent(table, rowIndex);
                StudentSupportDtos.StatusSaveRequest request = new StudentSupportDtos.StatusSaveRequest();
                request.setId(statusId);
                request.setStudentId(student.getId());
                request.setCategory(parseCategory(table.text(rowIndex, "Категория")));
                request.setNosologyCode(trimToNull(table.text(rowIndex, "Код нозологии")));
                request.setValidFrom(requiredDate(table.date(rowIndex, "Дата с"), "Укажите дату начала статуса"));
                request.setValidTo(table.date(rowIndex, "Дата по"));
                request.setComment(trimToNull(table.text(rowIndex, "Комментарий")));
                studentSupportService.saveStatus(academicYear, request);
                counter.imported++;
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_STATUSES, excelRow, exception);
            }
        }
    }

    private void importDocuments(Workbook workbook,
                                 String academicYear,
                                 ImportAccumulator accumulator) {
        SheetTable table = table(workbook, SHEET_DOCUMENTS);
        if (table == null) {
            return;
        }
        SheetCounter counter = accumulator.sheet(SHEET_DOCUMENTS);
        for (int rowIndex = table.firstDataRow(); rowIndex <= table.lastRow(); rowIndex++) {
            int excelRow = rowIndex + 1;
            try {
                String action = action(table.text(rowIndex, "Действие"));
                if (skipAction(action)
                        || blankRow(table, rowIndex, "Документ ID", "Карточка ID", "ФИО", "Вид документа")) {
                    counter.skipped++;
                    continue;
                }
                Long documentId = table.longValue(rowIndex, "Документ ID");
                StudentSupportDocument document = documentId == null
                        ? new StudentSupportDocument()
                        : supportDocumentRepository.findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Документ не найден: " + documentId
                        ));
                if (document.getId() != null && !Objects.equals(document.getAcademicYear(), academicYear)) {
                    throw new IllegalArgumentException("Документ относится к другому учебному году");
                }
                if ("DELETE".equals(action)) {
                    if (documentId == null) {
                        throw new IllegalArgumentException("Для удаления укажите ID документа");
                    }
                    supportDocumentAttachmentRepository.deleteAllByDocument_Id(documentId);
                    supportDocumentRepository.delete(document);
                    counter.deleted++;
                    continue;
                }

                StudentProfile student = resolveStudent(table, rowIndex);
                if (document.getId() != null
                        && !Objects.equals(document.getStudent().getId(), student.getId())) {
                    throw new IllegalArgumentException(
                            "Нельзя перенести существующий документ в карточку другого ребёнка"
                    );
                }
                LocalDate validFrom = table.date(rowIndex, "Дата с");
                LocalDate validTo = table.date(rowIndex, "Дата по");
                validateDates(validFrom, validTo);
                document.setStudent(student);
                document.setAcademicYear(academicYear);
                document.setDocumentType(parseDocumentType(table.text(rowIndex, "Вид документа")));
                document.setAcceptedForm(parseDocumentForm(table.text(rowIndex, "Форма приёма")));
                document.setDocumentNumber(trimToNull(table.text(rowIndex, "Номер")));
                document.setIssueDate(table.date(rowIndex, "Дата выдачи"));
                document.setValidFrom(validFrom);
                document.setValidTo(validTo);
                document.setIssuingOrganization(trimToNull(table.text(rowIndex, "Кем выдан")));
                document.setReceivedAt(Objects.requireNonNullElse(
                        table.date(rowIndex, "Дата приёма"),
                        LocalDate.now()
                ));
                document.setResponsibleEmployee(trimToNull(table.text(rowIndex, "Ответственный")));
                document.setComment(trimToNull(table.text(rowIndex, "Комментарий")));
                document.setUpdatedAt(LocalDateTime.now());
                supportDocumentRepository.save(document);
                counter.imported++;
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_DOCUMENTS, excelRow, exception);
            }
        }
    }

    private void importIups(Workbook workbook,
                            String academicYear,
                            ImportAccumulator accumulator) {
        SheetTable plans = table(workbook, SHEET_IUPS);
        if (plans == null) {
            return;
        }
        SheetTable subjects = table(workbook, SHEET_IUP_SUBJECTS);
        SheetTable teachers = table(workbook, SHEET_IUP_TEACHERS);
        Map<String, List<SubjectImportRow>> subjectsByIup = parseSubjectRows(subjects, accumulator);
        Map<String, List<TeacherImportRow>> teachersBySubject = parseTeacherRows(teachers, accumulator);
        SheetCounter counter = accumulator.sheet(SHEET_IUPS);

        for (int rowIndex = plans.firstDataRow(); rowIndex <= plans.lastRow(); rowIndex++) {
            int excelRow = rowIndex + 1;
            try {
                String action = action(plans.text(rowIndex, "Действие"));
                if (skipAction(action) || blankRow(plans, rowIndex, "Карточка ID", "ФИО", "Статус")) {
                    counter.skipped++;
                    continue;
                }
                Long planId = plans.longValue(rowIndex, "ИУП ID");
                String key = iupImportKey(planId, plans.text(rowIndex, "Ключ ИУП"));
                if ("DELETE".equals(action)) {
                    throw new IllegalArgumentException("ИУП не удаляется: установите статус CANCELLED");
                }
                StudentProfile student = resolveStudent(plans, rowIndex);
                StudentSupportDtos.IupSaveRequest request = new StudentSupportDtos.IupSaveRequest();
                request.setId(planId);
                request.setStudentId(student.getId());
                request.setStatus(parseIupStatus(plans.text(rowIndex, "Статус")));
                request.setOrderNumber(trimToNull(plans.text(rowIndex, "Номер приказа")));
                request.setOrderDate(plans.date(rowIndex, "Дата приказа"));
                request.setValidFrom(requiredDate(plans.date(rowIndex, "Дата с"), "Укажите дату начала ИУП"));
                request.setValidTo(plans.date(rowIndex, "Дата по"));
                request.setComment(trimToNull(plans.text(rowIndex, "Комментарий")));
                request.setSubjects(subjectsByIup.getOrDefault(key, List.of()).stream().map(subject -> {
                    StudentSupportDtos.SubjectLineRequest line = new StudentSupportDtos.SubjectLineRequest();
                    line.setCurriculumEntryId(subject.curriculumEntryId());
                    line.setSubjectName(subject.subjectName());
                    line.setParticipationMode(subject.mode());
                    line.setClassHours(subject.classHours());
                    line.setIndividualHours(subject.individualHours());
                    line.setGroupNameEducationalPlan(subject.groupName());
                    line.setTeachers(teachersBySubject.getOrDefault(
                            subjectTeacherKey(key, subject.subjectKey()),
                            List.of()
                    ).stream().map(teacher -> {
                        StudentSupportDtos.TeacherAssignmentRequest assignment =
                                new StudentSupportDtos.TeacherAssignmentRequest();
                        assignment.setTeacherId(teacher.teacherId());
                        assignment.setHoursPerWeek(teacher.hours());
                        assignment.setDeliveryForm(teacher.form());
                        assignment.setValidFrom(teacher.validFrom());
                        assignment.setValidTo(teacher.validTo());
                        return assignment;
                    }).toList());
                    return line;
                }).toList());
                studentSupportService.saveIup(academicYear, request);
                counter.imported++;
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_IUPS, excelRow, exception);
            }
        }
    }

    private Map<String, List<SubjectImportRow>> parseSubjectRows(
            SheetTable table,
            ImportAccumulator accumulator
    ) {
        if (table == null) {
            return Map.of();
        }
        SheetCounter counter = accumulator.sheet(SHEET_IUP_SUBJECTS);
        Map<String, List<SubjectImportRow>> result = new HashMap<>();
        for (int rowIndex = table.firstDataRow(); rowIndex <= table.lastRow(); rowIndex++) {
            try {
                String action = action(table.text(rowIndex, "Действие"));
                if (skipAction(action) || "DELETE".equals(action)
                        || blankRow(table, rowIndex, "Строка УП ID", "Предмет УП")) {
                    counter.skipped++;
                    continue;
                }
                String iupKey = iupImportKey(
                        table.longValue(rowIndex, "ИУП ID"),
                        table.text(rowIndex, "Ключ ИУП")
                );
                String subjectKey = subjectImportKey(
                        table.longValue(rowIndex, "Строка ID"),
                        table.text(rowIndex, "Ключ строки"),
                        table.text(rowIndex, "Предмет УП")
                );
                SubjectImportRow row = new SubjectImportRow(
                        subjectKey,
                        table.longValue(rowIndex, "Строка УП ID"),
                        trimToNull(table.text(rowIndex, "Предмет УП")),
                        parseParticipationMode(table.text(rowIndex, "Участие")),
                        decimal(table.text(rowIndex, "Часы с классом")),
                        decimal(table.text(rowIndex, "Индивидуальные часы")),
                        trimToNull(table.text(rowIndex, "Группа УП"))
                );
                result.computeIfAbsent(iupKey, ignored -> new ArrayList<>()).add(row);
                counter.imported++;
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_IUP_SUBJECTS, rowIndex + 1, exception);
            }
        }
        return result;
    }

    private Map<String, List<TeacherImportRow>> parseTeacherRows(
            SheetTable table,
            ImportAccumulator accumulator
    ) {
        if (table == null) {
            return Map.of();
        }
        SheetCounter counter = accumulator.sheet(SHEET_IUP_TEACHERS);
        Map<String, List<TeacherImportRow>> result = new HashMap<>();
        for (int rowIndex = table.firstDataRow(); rowIndex <= table.lastRow(); rowIndex++) {
            try {
                String action = action(table.text(rowIndex, "Действие"));
                if (skipAction(action) || "DELETE".equals(action)
                        || blankRow(table, rowIndex, "Педагог ID", "Педагог")) {
                    counter.skipped++;
                    continue;
                }
                String iupKey = iupImportKey(
                        table.longValue(rowIndex, "ИУП ID"),
                        table.text(rowIndex, "Ключ ИУП")
                );
                String subjectKey = subjectImportKey(
                        table.longValue(rowIndex, "Строка ID"),
                        table.text(rowIndex, "Ключ строки"),
                        ""
                );
                TeacherDirectoryEntry teacher = resolveTeacher(table, rowIndex);
                TeacherImportRow row = new TeacherImportRow(
                        teacher.getId(),
                        decimal(table.text(rowIndex, "Часы")),
                        parseDeliveryForm(table.text(rowIndex, "Форма")),
                        table.date(rowIndex, "Дата с"),
                        table.date(rowIndex, "Дата по")
                );
                result.computeIfAbsent(subjectTeacherKey(iupKey, subjectKey), ignored -> new ArrayList<>()).add(row);
                counter.imported++;
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_IUP_TEACHERS, rowIndex + 1, exception);
            }
        }
        return result;
    }

    private void importMeshNames(Workbook workbook,
                                 String academicYear,
                                 ImportAccumulator accumulator) {
        SheetTable table = table(workbook, SHEET_MESH_NAMES);
        if (table == null) {
            return;
        }
        SheetCounter counter = accumulator.sheet(SHEET_MESH_NAMES);
        for (int rowIndex = table.firstDataRow(); rowIndex <= table.lastRow(); rowIndex++) {
            try {
                String action = action(table.text(rowIndex, "Действие"));
                Long mappingId = table.longValue(rowIndex, "ID связи");
                Long curriculumId = table.longValue(rowIndex, "Строка УП ID");
                if (curriculumId == null) {
                    counter.skipped++;
                    continue;
                }
                boolean confirmed = parseBoolean(table.text(rowIndex, "Подтверждено"), false);
                boolean unchangedDefault = equalsNormalized(
                        table.text(rowIndex, "Предмет МЭШ"),
                        table.text(rowIndex, "Предмет УП")
                ) && equalsNormalized(
                        table.text(rowIndex, "Класс/метагруппа МЭШ"),
                        table.text(rowIndex, "Класс/метагруппа УП")
                ) && equalsNormalized(
                        normalizeGroup(table.text(rowIndex, "Группа МЭШ")),
                        normalizeGroup(table.text(rowIndex, "Группа УП"))
                );
                if (mappingId == null && skipAction(action) && unchangedDefault && !confirmed) {
                    counter.skipped++;
                    continue;
                }
                CurriculumPlanEntry entry = requireCurriculum(academicYear, curriculumId);
                String groupUp = normalizeGroup(table.text(rowIndex, "Группа УП"));
                CurriculumMeshMapping existing = mappingId == null
                        ? meshMappingRepository
                        .findByAcademicYearAndCurriculumEntryIdAndGroupNameUp(academicYear, curriculumId, groupUp)
                        .orElse(null)
                        : meshMappingRepository.findById(mappingId)
                        .orElseThrow(() -> new IllegalArgumentException("Связь названий не найдена: " + mappingId));
                if ("DELETE".equals(action)) {
                    if (existing != null) {
                        meshMappingRepository.delete(existing);
                        counter.deleted++;
                    } else {
                        counter.skipped++;
                    }
                    continue;
                }
                String subjectMesh = defaultText(table.text(rowIndex, "Предмет МЭШ"), entry.getSubjectName());
                String classMesh = defaultText(table.text(rowIndex, "Класс/метагруппа МЭШ"), entry.getClassName());
                String groupMesh = defaultText(table.text(rowIndex, "Группа МЭШ"), groupUp);
                boolean sameAsUp = equalsNormalized(subjectMesh, entry.getSubjectName())
                        && equalsNormalized(classMesh, entry.getClassName())
                        && equalsNormalized(groupMesh, groupUp);
                if (skipAction(action) && sameAsUp && !confirmed) {
                    counter.skipped++;
                    continue;
                }
                if (sameAsUp && !confirmed) {
                    if (existing != null) {
                        meshMappingRepository.delete(existing);
                        counter.deleted++;
                    } else {
                        counter.skipped++;
                    }
                    continue;
                }
                CurriculumMeshMapping mapping = existing == null ? new CurriculumMeshMapping() : existing;
                mapping.setAcademicYear(academicYear);
                mapping.setCurriculumEntryId(curriculumId);
                mapping.setSubjectNameUp(entry.getSubjectName());
                mapping.setClassNameUp(entry.getClassName());
                mapping.setGroupNameUp(groupUp);
                mapping.setSubjectNameMesh(subjectMesh);
                mapping.setClassNameMesh(classMesh);
                mapping.setGroupNameMesh(groupMesh);
                mapping.setConfirmed(confirmed);
                mapping.setUpdatedAt(LocalDateTime.now());
                meshMappingRepository.save(mapping);
                counter.imported++;
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_MESH_NAMES, rowIndex + 1, exception);
            }
        }
    }

    private void importDistribution(Workbook workbook,
                                    String academicYear,
                                    ImportAccumulator accumulator) {
        SheetTable table = table(workbook, SHEET_DISTRIBUTION);
        if (table == null) {
            return;
        }
        SheetCounter counter = accumulator.sheet(SHEET_DISTRIBUTION);
        Map<Long, IupPlan> activeIupByStudent = activeIups(
                academicYear,
                snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                        .map(ContingentSnapshot::getSnapshotDate)
                        .orElse(LocalDate.now())
        );
        for (int rowIndex = table.firstDataRow(); rowIndex <= table.lastRow(); rowIndex++) {
            try {
                String action = action(table.text(rowIndex, "Действие"));
                String groupUpRaw = trimToNull(table.text(rowIndex, "Группа УП"));
                String groupMeshRaw = trimToNull(table.text(rowIndex, "Группа МЭШ"));
                if (skipAction(action) && groupUpRaw == null && groupMeshRaw == null) {
                    counter.skipped++;
                    continue;
                }
                Long membershipId = table.longValue(rowIndex, "Распределение ID");
                if ("DELETE".equals(action)) {
                    if (membershipId == null) {
                        throw new IllegalArgumentException("Для удаления укажите ID распределения");
                    }
                    groupMembershipRepository.deleteById(membershipId);
                    counter.deleted++;
                    continue;
                }
                StudentProfile student = resolveStudent(table, rowIndex);
                List<CurriculumPlanEntry> entries = resolveDistributionEntries(
                        academicYear,
                        table,
                        rowIndex,
                        student
                );
                if (membershipId != null && entries.size() != 1) {
                    throw new IllegalArgumentException(
                            "Строка с ID распределения должна однозначно указывать одну строку учебного плана"
                    );
                }
                for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                    CurriculumPlanEntry entry = entries.get(entryIndex);
                    String groupUp = groupUpRaw;
                    if (groupUp == null && groupMeshRaw != null) {
                        groupUp = reverseMeshGroup(academicYear, entry.getId(), groupMeshRaw);
                    }
                    groupUp = normalizeGroup(required(groupUp, "Укажите группу УП или группу МЭШ"));
                    validateGroup(entry, groupUp);
                    validateIupDistribution(activeIupByStudent.get(student.getId()), entry);

                    StudentGroupMembership membership = membershipId == null
                            ? findMembership(academicYear, student.getId(), entry.getId(), entry.getMetaGroupId())
                            .orElseGet(StudentGroupMembership::new)
                            : groupMembershipRepository.findById(membershipId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Распределение не найдено: " + membershipId
                            ));
                    membership.setStudent(student);
                    membership.setAcademicYear(academicYear);
                    membership.setCurriculumEntryId(entry.getId());
                    membership.setMetaGroupId(entry.getMetaGroupId());
                    membership.setGroupNameEducationalPlan(groupUp);
                    membership.setValidFrom(Optional.ofNullable(table.date(rowIndex, "Дата с"))
                            .orElseGet(() -> academicYearStart(academicYear)));
                    membership.setValidTo(table.date(rowIndex, "Дата по"));
                    validateDates(membership.getValidFrom(), membership.getValidTo());
                    membership.setSource(StudentGroupMembershipSource.MESH_IMPORT);
                    membership.setIupSubjectLineId(null);
                    groupMembershipRepository.save(membership);
                    counter.imported++;
                }
            } catch (RuntimeException exception) {
                accumulator.error(SHEET_DISTRIBUTION, rowIndex + 1, exception);
            }
        }
    }

    private ExportContext exportContext(String academicYear) {
        ContingentSnapshot snapshot = snapshotRepository
                .findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                .orElseThrow(() -> new IllegalStateException("Сначала загрузите контингент на учебный год"));
        List<ContingentStudent> sourceRows = contingentStudentRepository.findAllBySnapshotId(snapshot.getId());
        Map<Long, StudentProfile> profiles = studentProfileRepository.findAllById(
                        sourceRows.stream()
                                .map(ContingentStudent::getStudentId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(StudentProfile::getId, Function.identity()));
        return new ExportContext(snapshot, sourceRows, profiles);
    }

    private Map<Long, IupPlan> activeIups(String academicYear, LocalDate date) {
        Map<Long, IupPlan> result = new HashMap<>();
        iupPlanRepository.findAllByAcademicYear(academicYear).stream()
                .filter(plan -> plan.getStatus().affectsHeadcount())
                .filter(plan -> contains(plan.getValidFrom(), plan.getValidTo(), date))
                .sorted(Comparator.comparing(IupPlan::getVersionNumber, Comparator.nullsFirst(Integer::compareTo)))
                .forEach(plan -> result.put(plan.getStudent().getId(), plan));
        return result;
    }

    private StudentProfile resolveStudent(SheetTable table, int rowIndex) {
        Long studentId = table.longValue(rowIndex, "Карточка ID");
        if (studentId != null) {
            return studentProfileRepository.findById(studentId)
                    .orElseThrow(() -> new IllegalArgumentException("Карточка ребёнка не найдена: " + studentId));
        }
        String record = normalizeRecord(table.text(rowIndex, "Личное дело"));
        if (!record.isBlank()) {
            List<StudentProfile> candidates = studentProfileRepository.findAllByNormalizedRecordNumber(record);
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
            if (candidates.size() > 1) {
                throw new IllegalArgumentException("Личное дело найдено у нескольких карточек");
            }
        }
        String name = normalizeName(table.text(rowIndex, "ФИО"));
        LocalDate birthDate = table.date(rowIndex, "Дата рождения");
        if (!name.isBlank() && birthDate != null) {
            List<StudentProfile> candidates =
                    studentProfileRepository.findAllByNormalizedFullNameAndBirthDate(name, birthDate);
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
        }
        throw new IllegalArgumentException("Не удалось однозначно определить ребёнка");
    }

    private TeacherDirectoryEntry resolveTeacher(SheetTable table, int rowIndex) {
        Long teacherId = table.longValue(rowIndex, "Педагог ID");
        if (teacherId != null) {
            return teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Педагог не найден: " + teacherId));
        }
        String fio = trimToNull(table.text(rowIndex, "Педагог"));
        if (fio == null) {
            throw new IllegalArgumentException("Укажите педагога");
        }
        return teacherRepository.findByFioTeacherIgnoreCase(fio)
                .orElseThrow(() -> new IllegalArgumentException("Педагог не найден: " + fio));
    }

    private CurriculumPlanEntry requireCurriculum(String academicYear, Long curriculumId) {
        if (curriculumId == null) {
            throw new IllegalArgumentException("Укажите ID строки учебного плана");
        }
        CurriculumPlanEntry entry = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new IllegalArgumentException("Строка учебного плана не найдена: " + curriculumId));
        if (!Objects.equals(entry.getAcademicYear(), academicYear) || entry.isDeprecated()) {
            throw new IllegalArgumentException("Строка учебного плана не относится к выбранному учебному году");
        }
        return entry;
    }

    private List<CurriculumPlanEntry> resolveDistributionEntries(String academicYear,
                                                                 SheetTable table,
                                                                 int rowIndex,
                                                                 StudentProfile student) {
        Long curriculumId = table.longValue(rowIndex, "Строка УП ID");
        if (curriculumId != null) {
            return List.of(requireCurriculum(academicYear, curriculumId));
        }
        String subjectUp = trimToNull(table.text(rowIndex, "Предмет УП"));
        String subjectMesh = trimToNull(table.text(rowIndex, "Предмет МЭШ"));
        String classMesh = trimToNull(table.text(rowIndex, "Класс/метагруппа МЭШ"));
        String sourceClass = trimToNull(table.text(rowIndex, "Класс"));
        String effectiveSubject = subjectMesh == null ? subjectUp : subjectMesh;
        if (effectiveSubject == null) {
            throw new IllegalArgumentException("Укажите строку УП ID или предмет МЭШ");
        }

        List<Long> mappedIds = meshMappingRepository.findAllByAcademicYear(academicYear).stream()
                .filter(mapping -> equalsNormalized(mapping.getSubjectNameMesh(), effectiveSubject))
                .filter(mapping -> classMesh == null || equalsNormalized(mapping.getClassNameMesh(), classMesh))
                .map(CurriculumMeshMapping::getCurriculumEntryId)
                .distinct()
                .toList();
        if (!mappedIds.isEmpty()) {
            List<CurriculumPlanEntry> mapped = mappedIds.stream()
                    .map(id -> requireCurriculum(academicYear, id))
                    .filter(entry -> entry.isSubgroupRequired() || entry.getMetaGroupId() != null)
                    .toList();
            if (!mapped.isEmpty()) {
                return mapped;
            }
        }

        String effectiveClass = classMesh == null ? sourceClass : classMesh;
        List<CurriculumPlanEntry> matches = activeCurriculumEntries(academicYear).stream()
                .filter(entry -> entry.isSubgroupRequired() || entry.getMetaGroupId() != null)
                .filter(entry -> equalsNormalized(entry.getSubjectName(), effectiveSubject))
                .filter(entry -> {
                    if (entry.getMetaGroupId() != null) {
                        return effectiveClass != null && equalsNormalized(entry.getClassName(), effectiveClass);
                    }
                    String enrolledClass = sourceClass == null
                            ? currentClassName(student.getId(), academicYear)
                            : sourceClass;
                    return equalsNormalized(entry.getClassName(), enrolledClass);
                })
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "Не найдена строка УП по названиям. Укажите «Строка УП ID» из выгруженного пакета"
            );
        }
        return matches;
    }

    private String currentClassName(Long studentId, String academicYear) {
        return enrollmentRepository
                .findFirstByStudent_IdAndAcademicYearAndValidToIsNullOrderByValidFromDesc(studentId, academicYear)
                .map(StudentClassEnrollment::getClassName)
                .orElse("");
    }

    private void validateIupDistribution(IupPlan plan, CurriculumPlanEntry entry) {
        if (plan == null) {
            return;
        }
        boolean permitted = iupSubjectLineRepository.findAllByIupPlan_IdOrderBySubjectNameAsc(plan.getId()).stream()
                .anyMatch(line -> Objects.equals(line.getCurriculumEntryId(), entry.getId())
                        && attendsClass(line.getParticipationMode()));
        if (!permitted) {
            throw new IllegalArgumentException(
                    "Ребёнок на ИУП не посещает этот предмет с классом и не должен распределяться в подгруппу"
            );
        }
    }

    private void validateGroup(CurriculumPlanEntry entry, String groupName) {
        if (entry.isSubgroupRequired()) {
            int count = entry.getSubgroupCount() == null || entry.getSubgroupCount() < 1
                    ? 2
                    : entry.getSubgroupCount();
            int number = groupNumber(groupName);
            if (number < 1 || number > count) {
                throw new IllegalArgumentException("Для предмета доступны группы с 1 по " + count);
            }
        } else if (entry.getMetaGroupId() == null) {
            throw new IllegalArgumentException("Строка учебного плана не является подгруппой или метагруппой");
        }
    }

    private Optional<StudentGroupMembership> findMembership(
            String academicYear,
            Long studentId,
            Long curriculumEntryId,
            Long metaGroupId
    ) {
        return groupMembershipRepository.findAllByStudent_IdAndAcademicYear(studentId, academicYear).stream()
                .filter(membership -> Objects.equals(membership.getCurriculumEntryId(), curriculumEntryId))
                .filter(membership -> Objects.equals(membership.getMetaGroupId(), metaGroupId))
                .findFirst();
    }

    private String reverseMeshGroup(String academicYear, Long curriculumId, String meshGroup) {
        return meshMappingRepository.findAllByAcademicYear(academicYear).stream()
                .filter(mapping -> Objects.equals(mapping.getCurriculumEntryId(), curriculumId))
                .filter(mapping -> equalsNormalized(mapping.getGroupNameMesh(), meshGroup))
                .map(CurriculumMeshMapping::getGroupNameUp)
                .findFirst()
                .orElse(meshGroup);
    }

    private List<CurriculumPlanEntry> activeCurriculumEntries(String academicYear) {
        return curriculumRepository.findAllByAcademicYear(academicYear).stream()
                .filter(entry -> !entry.isDeprecated())
                .sorted(Comparator.comparing(CurriculumPlanEntry::getClassName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CurriculumPlanEntry::getSubjectName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CurriculumPlanEntry::getId))
                .toList();
    }

    private List<CurriculumPlanEntry> matchingCurriculumEntries(
            ManualLoadEntry row,
            List<CurriculumPlanEntry> curriculum
    ) {
        List<CurriculumPlanEntry> matches = curriculum.stream()
                .filter(entry -> {
                    if (row.getMetaGroupId() != null) {
                        return Objects.equals(row.getMetaGroupId(), entry.getMetaGroupId());
                    }
                    if (row.getClassId() != null && entry.getClassId() != null) {
                        return Objects.equals(row.getClassId(), entry.getClassId());
                    }
                    return classKey(row.getClassName()).equals(classKey(entry.getClassName()));
                })
                .filter(entry -> row.getSubjectId() != null && entry.getSubjectId() != null
                        ? Objects.equals(row.getSubjectId(), entry.getSubjectId())
                        : equalsNormalized(row.getSubjectName(), entry.getSubjectName()))
                .toList();
        matches = narrowCurriculumMatches(
                matches,
                entry -> row.getCurriculumPart() == null
                        || Objects.equals(row.getCurriculumPart(), entry.getCurriculumPart())
        );
        matches = narrowCurriculumMatches(
                matches,
                entry -> row.getStudyPeriod() == null
                        || Objects.equals(row.getStudyPeriod(), entry.getStudyPeriod())
        );
        return narrowCurriculumMatches(
                matches,
                entry -> row.getEducationLevel() == null
                        || Objects.equals(row.getEducationLevel(), entry.getEducationLevel())
        );
    }

    private List<CurriculumPlanEntry> narrowCurriculumMatches(
            List<CurriculumPlanEntry> source,
            java.util.function.Predicate<CurriculumPlanEntry> predicate
    ) {
        List<CurriculumPlanEntry> narrowed = source.stream().filter(predicate).toList();
        return narrowed.isEmpty() ? source : narrowed;
    }

    private void writeTable(Workbook workbook,
                            String sheetName,
                            List<String> headers,
                            List<List<Object>> rows,
                            WorkbookStyles styles,
                            boolean filter) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setDisplayGridlines(false);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(30);
        for (int column = 0; column < headers.size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(headers.get(column));
            cell.setCellStyle(styles.header());
        }
        int rowIndex = 1;
        for (List<Object> values : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int column = 0; column < headers.size(); column++) {
                Object value = column < values.size() ? values.get(column) : null;
                Cell cell = row.createCell(column);
                writeCell(cell, value, styles);
            }
        }
        sheet.createFreezePane(0, 1);
        if (filter) {
            sheet.setAutoFilter(new CellRangeAddress(
                    0,
                    Math.max(0, rowIndex - 1),
                    0,
                    Math.max(0, headers.size() - 1)
            ));
        }
        addValidations(sheetName, sheet, headers, Math.max(2000, rowIndex + 200));
        for (int column = 0; column < headers.size(); column++) {
            sheet.autoSizeColumn(column);
            int width = Math.max(12 * 256, Math.min(45 * 256, sheet.getColumnWidth(column) + 512));
            sheet.setColumnWidth(column, width);
        }
    }

    private void addValidations(String sheetName,
                                Sheet sheet,
                                List<String> headers,
                                int lastRow) {
        addListValidation(sheet, headers, "Действие", new String[]{"UPSERT", "DELETE"}, lastRow);
        if (SHEET_STATUSES.equals(sheetName)) {
            addListValidation(sheet, headers, "Категория", new String[]{"NORMAL", "K2", "K3"}, lastRow);
        } else if (SHEET_DOCUMENTS.equals(sheetName)) {
            addListValidation(sheet, headers, "Вид документа", Arrays.stream(StudentSupportDocumentType.values())
                    .map(Enum::name)
                    .toArray(String[]::new), lastRow);
            addListValidation(sheet, headers, "Форма приёма", Arrays.stream(StudentSupportDocumentForm.values())
                    .map(Enum::name)
                    .toArray(String[]::new), lastRow);
        } else if (SHEET_NOSOLOGIES.equals(sheetName)) {
            addListValidation(sheet, headers, "Коэффициент", new String[]{"K2", "K3"}, lastRow);
            addListValidation(sheet, headers, "Активна", new String[]{"TRUE", "FALSE"}, lastRow);
        } else if (SHEET_IUPS.equals(sheetName)) {
            addListValidation(sheet, headers, "Статус", Arrays.stream(IupStatus.values())
                    .map(Enum::name)
                    .toArray(String[]::new), lastRow);
        } else if (SHEET_IUP_SUBJECTS.equals(sheetName)) {
            addListValidation(sheet, headers, "Участие", Arrays.stream(IupParticipationMode.values())
                    .map(Enum::name)
                    .toArray(String[]::new), lastRow);
        } else if (SHEET_IUP_TEACHERS.equals(sheetName)) {
            addListValidation(sheet, headers, "Форма", Arrays.stream(IupDeliveryForm.values())
                    .map(Enum::name)
                    .toArray(String[]::new), lastRow);
        } else if (SHEET_DISTRIBUTION.equals(sheetName)) {
            addListValidation(sheet, headers, "Тип", new String[]{"ПОДГРУППА", "МЕТАГРУППА"}, lastRow);
        }
    }

    private void addListValidation(Sheet sheet,
                                   List<String> headers,
                                   String header,
                                   String[] values,
                                   int lastRow) {
        int column = headers.indexOf(header);
        if (column < 0) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        CellRangeAddressList addresses = new CellRangeAddressList(1, lastRow, column, column);
        DataValidation validation = helper.createValidation(constraint, addresses);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(false);
        sheet.addValidationData(validation);
    }

    private void writeCell(Cell cell, Object value, WorkbookStyles styles) {
        if (value == null || value instanceof String string && string.isBlank()) {
            cell.setBlank();
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(java.sql.Date.valueOf(date));
            cell.setCellStyle(styles.date());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(styles.number());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private WorkbookStyles styles(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 15);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle number = workbook.createCellStyle();
        number.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.00"));
        CellStyle wrap = workbook.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        return new WorkbookStyles(header, title, date, number, wrap);
    }

    private SheetTable table(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        return sheet == null ? null : SheetTable.of(sheet);
    }

    private String membershipScopeKey(StudentGroupMembership membership) {
        return membershipScopeKey(
                membership.getStudent().getId(),
                membership.getCurriculumEntryId(),
                membership.getMetaGroupId()
        );
    }

    private String membershipScopeKey(Long studentId, Long curriculumEntryId, Long metaGroupId) {
        return studentId + "|" + curriculumEntryId + "|" + metaGroupId;
    }

    private String mappingKey(CurriculumMeshMapping mapping) {
        return mappingKey(mapping.getCurriculumEntryId(), mapping.getGroupNameUp());
    }

    private String mappingKey(Long curriculumEntryId, String groupName) {
        return curriculumEntryId + "|" + normalizeGroup(groupName);
    }

    private String iupKey(IupPlan plan) {
        return "IUP-ID-" + plan.getId();
    }

    private String subjectKey(IupSubjectLine line) {
        return "LINE-ID-" + line.getId();
    }

    private String iupImportKey(Long id, String rawKey) {
        if (id != null) {
            return "IUP-ID-" + id;
        }
        return required(trimToNull(rawKey), "Для нового ИУП укажите ключ ИУП");
    }

    private String subjectImportKey(Long id, String rawKey, String subjectName) {
        if (id != null) {
            return "LINE-ID-" + id;
        }
        String key = trimToNull(rawKey);
        if (key != null) {
            return key;
        }
        return required(trimToNull(subjectName), "Для новой строки укажите ключ строки");
    }

    private String subjectTeacherKey(String iupKey, String subjectKey) {
        return iupKey + "|" + subjectKey;
    }

    private List<String> groupNames(Integer count) {
        int effectiveCount = count == null || count < 1 ? 2 : count;
        List<String> result = new ArrayList<>();
        for (int index = 1; index <= effectiveCount; index++) {
            result.add("Группа " + index);
        }
        return result;
    }

    private int groupNumber(String groupName) {
        try {
            return Integer.parseInt(Objects.toString(groupName, "").replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private boolean attendsClass(IupParticipationMode mode) {
        return mode == IupParticipationMode.WITH_CLASS || mode == IupParticipationMode.PARTIAL;
    }

    private String classKey(String value) {
        return ClassNameNormalizer.normalize(value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", "")
                .replace('–', '-')
                .replace('—', '-');
    }

    private String normalizeName(String value) {
        return Objects.toString(value, "").trim()
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ");
    }

    private String normalizeRecord(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String normalizeGroup(String value) {
        return Objects.toString(value, "").trim().replaceAll("\\s+", " ");
    }

    private String action(String value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private boolean skipAction(String action) {
        return action.isBlank() || "ПРИМЕР".equals(action) || "EXAMPLE".equals(action);
    }

    private StudentCategory parseCategory(String value) {
        String normalized = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT)
                .replace('К', 'K');
        return switch (normalized) {
            case "K2", "2" -> StudentCategory.K2;
            case "K3", "3" -> StudentCategory.K3;
            case "NORMAL", "НОРМА", "" -> StudentCategory.NORMAL;
            default -> throw new IllegalArgumentException("Неизвестная категория: " + value);
        };
    }

    private StudentSupportDocumentType parseDocumentType(String value) {
        String normalized = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "СПРАВКА МСЭ", "МСЭ", "MSE_CERTIFICATE" ->
                    StudentSupportDocumentType.MSE_CERTIFICATE;
            case "ИПР", "ИПРА", "ИПР/ИПРА", "IPR_IPRA" ->
                    StudentSupportDocumentType.IPR_IPRA;
            case "ЗАКЛЮЧЕНИЕ ЦПМПК", "ЦПМПК", "CPMPC_CONCLUSION" ->
                    StudentSupportDocumentType.CPMPC_CONCLUSION;
            case "ПРОТОКОЛ ППК", "ППК", "INTERNAL_PPK_PROTOCOL" ->
                    StudentSupportDocumentType.INTERNAL_PPK_PROTOCOL;
            case "ИОМ", "IOM" -> StudentSupportDocumentType.IOM;
            case "ДРУГОЙ ДОКУМЕНТ", "ДРУГОЙ", "OTHER" -> StudentSupportDocumentType.OTHER;
            default -> throw new IllegalArgumentException("Неизвестный вид документа: " + value);
        };
    }

    private StudentSupportDocumentForm parseDocumentForm(String value) {
        String normalized = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "", "КОПИЯ", "COPY" -> StudentSupportDocumentForm.COPY;
            case "ОРИГИНАЛ", "ORIGINAL" -> StudentSupportDocumentForm.ORIGINAL;
            case "ЭЛЕКТРОННАЯ КОПИЯ", "ЭЛЕКТРОННЫЙ", "ELECTRONIC_COPY" ->
                    StudentSupportDocumentForm.ELECTRONIC_COPY;
            default -> throw new IllegalArgumentException("Неизвестная форма приёма: " + value);
        };
    }

    private IupStatus parseIupStatus(String value) {
        String normalized = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ЧЕРНОВИК", "DRAFT" -> IupStatus.DRAFT;
            case "НА СОГЛАСОВАНИИ", "REVIEW" -> IupStatus.REVIEW;
            case "УТВЕРЖДЁН", "УТВЕРЖДЕН", "APPROVED" -> IupStatus.APPROVED;
            case "ДЕЙСТВУЕТ", "ACTIVE" -> IupStatus.ACTIVE;
            case "ИЗМЕНЁН", "ИЗМЕНЕН", "CHANGED" -> IupStatus.CHANGED;
            case "ЗАВЕРШЁН", "ЗАВЕРШЕН", "COMPLETED" -> IupStatus.COMPLETED;
            case "ОТМЕНЁН", "ОТМЕНЕН", "CANCELLED" -> IupStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Неизвестный статус ИУП: " + value);
        };
    }

    private IupParticipationMode parseParticipationMode(String value) {
        String normalized = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "С КЛАССОМ", "WITH_CLASS" -> IupParticipationMode.WITH_CLASS;
            case "ИНДИВИДУАЛЬНО", "INDIVIDUAL" -> IupParticipationMode.INDIVIDUAL;
            case "ЧАСТИЧНО С КЛАССОМ", "PARTIAL" -> IupParticipationMode.PARTIAL;
            case "НЕ ИЗУЧАЕТ", "NOT_STUDIED" -> IupParticipationMode.NOT_STUDIED;
            default -> throw new IllegalArgumentException("Неизвестный способ участия: " + value);
        };
    }

    private IupDeliveryForm parseDeliveryForm(String value) {
        String normalized = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ОЧНО", "FACE_TO_FACE" -> IupDeliveryForm.FACE_TO_FACE;
            case "ЭЛЕКТРОННО", "ELECTRONIC" -> IupDeliveryForm.ELECTRONIC;
            case "ДИСТАНЦИОННО", "DISTANCE" -> IupDeliveryForm.DISTANCE;
            case "СМЕШАННО", "MIXED" -> IupDeliveryForm.MIXED;
            default -> throw new IllegalArgumentException("Неизвестная форма занятия: " + value);
        };
    }

    private BigDecimal decimal(String value) {
        String normalized = Objects.toString(value, "").trim().replace(" ", "").replace(',', '.');
        if (normalized.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректное число: " + value);
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        String normalized = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return defaultValue;
        }
        return Set.of("true", "да", "1", "yes").contains(normalized);
    }

    private LocalDate academicYearStart(String academicYear) {
        try {
            int year = Integer.parseInt(academicYear.substring(0, 4));
            return LocalDate.of(year, 9, 1);
        } catch (RuntimeException exception) {
            return LocalDate.now();
        }
    }

    private boolean contains(LocalDate from, LocalDate to, LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
    }

    private LocalDate requiredDate(LocalDate value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private boolean blankRow(SheetTable table, int rowIndex, String... headers) {
        return Arrays.stream(headers).allMatch(header -> table.text(rowIndex, header).isBlank());
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultText(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? Objects.toString(fallback, "") : normalized;
    }

    private boolean equalsNormalized(String first, String second) {
        return Objects.toString(first, "").trim()
                .equalsIgnoreCase(Objects.toString(second, "").trim());
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private LocalDate parseDate(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted input format.
            }
        }
        return null;
    }

    private record ExportContext(
            ContingentSnapshot snapshot,
            List<ContingentStudent> sourceRows,
            Map<Long, StudentProfile> profiles
    ) {
    }

    private record SubjectImportRow(
            String subjectKey,
            Long curriculumEntryId,
            String subjectName,
            IupParticipationMode mode,
            BigDecimal classHours,
            BigDecimal individualHours,
            String groupName
    ) {
    }

    private record TeacherImportRow(
            Long teacherId,
            BigDecimal hours,
            IupDeliveryForm form,
            LocalDate validFrom,
            LocalDate validTo
    ) {
    }

    private record WorkbookStyles(
            CellStyle header,
            CellStyle title,
            CellStyle date,
            CellStyle number,
            CellStyle wrap
    ) {
    }

    private static final class ImportAccumulator {
        private final LinkedHashMap<String, SheetCounter> sheets = new LinkedHashMap<>();
        private final List<StudentDataExchangeDtos.ImportError> errors = new ArrayList<>();

        private SheetCounter sheet(String name) {
            return sheets.computeIfAbsent(name, SheetCounter::new);
        }

        private void error(String sheet, int row, RuntimeException exception) {
            StudentDataExchangeDtos.ImportError error = new StudentDataExchangeDtos.ImportError();
            error.setSheetName(sheet);
            error.setRowNumber(row);
            error.setMessage(exception.getMessage());
            errors.add(error);
        }

        private StudentDataExchangeDtos.ImportResult toResponse() {
            StudentDataExchangeDtos.ImportResult response = new StudentDataExchangeDtos.ImportResult();
            List<StudentDataExchangeDtos.SheetImportResult> sheetResults = sheets.values().stream()
                    .map(SheetCounter::toDto)
                    .toList();
            response.setSheets(sheetResults);
            response.setErrors(errors);
            response.setImported(sheetResults.stream().mapToInt(StudentDataExchangeDtos.SheetImportResult::getImported).sum());
            response.setDeleted(sheetResults.stream().mapToInt(StudentDataExchangeDtos.SheetImportResult::getDeleted).sum());
            response.setSkipped(sheetResults.stream().mapToInt(StudentDataExchangeDtos.SheetImportResult::getSkipped).sum());
            return response;
        }
    }

    private static final class SheetCounter {
        private final String name;
        private int imported;
        private int deleted;
        private int skipped;

        private SheetCounter(String name) {
            this.name = name;
        }

        private StudentDataExchangeDtos.SheetImportResult toDto() {
            StudentDataExchangeDtos.SheetImportResult result = new StudentDataExchangeDtos.SheetImportResult();
            result.setSheetName(name);
            result.setImported(imported);
            result.setDeleted(deleted);
            result.setSkipped(skipped);
            return result;
        }
    }

    private static final class SheetTable {
        private final Sheet sheet;
        private final DataFormatter formatter;
        private final Map<String, Integer> headers;

        private SheetTable(Sheet sheet, Map<String, Integer> headers) {
            this.sheet = sheet;
            this.headers = headers;
            this.formatter = new DataFormatter(Locale.forLanguageTag("ru"));
        }

        private static SheetTable of(Sheet sheet) {
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("ru"));
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("На листе «" + sheet.getSheetName() + "» нет заголовков");
            }
            Map<String, Integer> headers = new HashMap<>();
            for (Cell cell : header) {
                headers.put(normalizeHeader(formatter.formatCellValue(cell)), cell.getColumnIndex());
            }
            return new SheetTable(sheet, headers);
        }

        private int firstDataRow() {
            return 1;
        }

        private int lastRow() {
            return sheet.getLastRowNum();
        }

        private String text(int rowIndex, String header) {
            Integer column = headers.get(normalizeHeader(header));
            if (column == null) {
                return "";
            }
            Row row = sheet.getRow(rowIndex);
            Cell cell = row == null ? null : row.getCell(column);
            return cell == null ? "" : formatter.formatCellValue(cell).trim();
        }

        private Long longValue(int rowIndex, String header) {
            String value = text(rowIndex, header).replace(" ", "").replace(',', '.');
            if (value.isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(value).longValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException("В колонке «" + header + "» ожидается целое число");
            }
        }

        private LocalDate date(int rowIndex, String header) {
            Integer column = headers.get(normalizeHeader(header));
            if (column == null) {
                return null;
            }
            Row row = sheet.getRow(rowIndex);
            Cell cell = row == null ? null : row.getCell(column);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                return null;
            }
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            String value = formatter.formatCellValue(cell).trim();
            for (DateTimeFormatter dateFormat : DATE_FORMATS) {
                try {
                    return LocalDate.parse(value, dateFormat);
                } catch (DateTimeParseException ignored) {
                    // Try the next accepted input format.
                }
            }
            if (!value.isBlank()) {
                throw new IllegalArgumentException("В колонке «" + header + "» некорректная дата: " + value);
            }
            return null;
        }

        private static String normalizeHeader(String value) {
            return Objects.toString(value, "").trim()
                    .toLowerCase(Locale.ROOT)
                    .replace('ё', 'е')
                    .replaceAll("\\s+", " ");
        }
    }
}
