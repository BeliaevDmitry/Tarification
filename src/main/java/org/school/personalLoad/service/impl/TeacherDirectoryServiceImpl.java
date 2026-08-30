package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherOneCImportDtos;
import org.school.personalLoad.dto.TeacherUpdateRequest;
import org.school.personalLoad.dto.PersonnelDtos.NameCases;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.TeacherDirectoryService;
import org.school.personalLoad.service.RussianNameCases;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherDirectoryServiceImpl implements TeacherDirectoryService {
    private static final String VACANCY_TEACHER = "Вакансия";
    private static final int EXCEL_CELL_TEXT_LIMIT = 32_767;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVALID_EXCEL_TEXT_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final SchoolBuildingRepository schoolBuildingRepository;

    @Override
    @Transactional
    public Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        Set<String> seen = new HashSet<>();
        List<SchoolBuilding> availableSites = schoolBuildingRepository.findAll();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = findTeachersSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("Лист 'Педагоги' не найден");
            }

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String fio = getCellStringValue(row.getCell(0));
                String initials = normalizeOptional(getCellStringValue(row.getCell(1)));
                String fioDative = normalizeOptional(getCellStringValue(row.getCell(2)));
                String phone;
                String email;
                try {
                    phone = normalizePhone(getCellStringValue(row.getCell(3)));
                    email = normalizeEmail(getCellStringValue(row.getCell(4)));
                } catch (IllegalArgumentException ex) {
                    skipped++;
                    continue;
                }
                String additionalDuties = normalizeOptional(getCellStringValue(row.getCell(5)));
                String building = normalizeOptional(getCellStringValue(row.getCell(6)));
                SchoolBuilding selectedSite = resolveSchoolBuilding(building, availableSites);
                if (fio.isBlank()) {
                    skipped++;
                    continue;
                }

                String normalized = fio.trim();
                if (normalized.equalsIgnoreCase("фио")
                        || normalized.equalsIgnoreCase("педагог")
                        || normalized.equalsIgnoreCase("сотрудник")) {
                    skipped++;
                    continue;
                }

                if (!seen.add(normalized.toLowerCase(Locale.ROOT))) {
                    skipped++;
                    continue;
                }

                Optional<TeacherDirectoryEntry> existing = teacherDirectoryRepository.findByFioTeacherIgnoreCase(normalized);
                if (existing.isPresent()) {
                    TeacherDirectoryEntry teacher = existing.get();
                    boolean changed = false;
                    if (fioDative != null && !Objects.equals(fioDative, teacher.getFioTeacherDative())) { teacher.setFioTeacherDative(fioDative); changed = true; }
                    if (initials != null && !Objects.equals(initials, teacher.getInitials())) { teacher.setInitials(initials); changed = true; }
                    changed |= fillMissingNameCases(teacher);
                    if (!Objects.equals(phone, teacher.getPhone())) { teacher.setPhone(phone); changed = true; }
                    if (!Objects.equals(email, teacher.getEmail())) { ensureUniqueTeacherEmail(email, teacher.getId()); teacher.setEmail(email); changed = true; }
                    if (!Objects.equals(additionalDuties, teacher.getAdditionalDuties())) { teacher.setAdditionalDuties(additionalDuties); changed = true; }
                    String previousBuilding = teacher.getNumberSchoolBuilding();
                    Long previousSiteId = teacher.getSchoolBuildingId();
                    applyBuildingSelection(teacher, building, selectedSite);
                    if (!Objects.equals(previousBuilding, teacher.getNumberSchoolBuilding())
                            || !Objects.equals(previousSiteId, teacher.getSchoolBuildingId())) changed = true;
                    if (changed) { teacherDirectoryRepository.save(teacher); updated++; } else { skipped++; }
                    continue;
                }

                TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
                applyNameCases(entry, RussianNameCases.derive(normalized));
                if (fioDative != null) entry.setFioTeacherDative(fioDative);
                if (initials != null) entry.setInitials(initials);
                entry.setPhone(phone);
                ensureUniqueTeacherEmail(email, null);
                entry.setEmail(email);
                entry.setAdditionalDuties(additionalDuties);
                applyBuildingSelection(entry, building, selectedSite);
                teacherDirectoryRepository.save(entry);
                imported++;
            }

            log.info("Импорт педагогов завершен: imported={}, updated={}, skipped={}", imported, updated, skipped);
            return Map.of(
                    "status", "ok",
                    "imported", imported,
                    "updated", updated,
                    "skipped", skipped,
                    "sheet", sheet.getSheetName()
            );
        } catch (Exception e) {
            log.error("Ошибка импорта педагогов", e);
            throw new RuntimeException("Не удалось импортировать педагогов из Excel", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource buildImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Педагоги");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ФИО");
            header.createCell(1).setCellValue("ФИО (инициалы)");
            header.createCell(2).setCellValue("Дательный падеж");
            header.createCell(3).setCellValue("Телефон");
            header.createCell(4).setCellValue("Email");
            header.createCell(5).setCellValue("Дополнительные обязанности");
            header.createCell(6).setCellValue("Площадка (адрес)");

            Map<Long, String> siteAddressById = schoolBuildingRepository.findAll().stream()
                    .filter(site -> site.getId() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            SchoolBuilding::getId,
                            site -> Objects.toString(site.getAddress(), "").trim(),
                            (first, second) -> first
                    ));

            List<TeacherDirectoryEntry> rows = activeTeachers();
            if (rows.isEmpty()) {
                Row example = sheet.createRow(1);
                example.createCell(0).setCellValue("Иванов Иван Иванович");
                example.createCell(1).setCellValue("Иванов И.И.");
                example.createCell(2).setCellValue("Иванову И.И.");
                example.createCell(3).setCellValue("+7 900 000-00-00");
                example.createCell(4).setCellValue("teacher@example.com");
                example.createCell(5).setCellValue("Классное руководство");
                example.createCell(6).setCellValue("ул. Примерная, д. 1");
            } else {
                int rowIndex = 1;
                for (TeacherDirectoryEntry entry : rows) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(excelText(entry.getFioTeacher()));
                    row.createCell(1).setCellValue(excelText(entry.getInitials()));
                    row.createCell(2).setCellValue(excelText(entry.getFioTeacherDative()));
                    row.createCell(3).setCellValue(excelText(entry.getPhone()));
                    row.createCell(4).setCellValue(excelText(entry.getEmail()));
                    row.createCell(5).setCellValue(excelText(entry.getAdditionalDuties()));
                    row.createCell(6).setCellValue(excelText(siteAddressById.getOrDefault(
                            entry.getSchoolBuildingId(), entry.getNumberSchoolBuilding())));
                }
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            sheet.autoSizeColumn(3);
            sheet.autoSizeColumn(4);
            sheet.autoSizeColumn(5);
            sheet.autoSizeColumn(6);
            workbook.write(out);
            return new ByteArrayResource(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать файл педагогов", e);
        }
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry create(TeacherCreateRequest request) {
        if (request == null || request.getFioTeacher() == null || request.getFioTeacher().isBlank()) {
            throw new IllegalArgumentException("fioTeacher is required");
        }

        String normalized = request.getFioTeacher().trim();
        var cases = RussianNameCases.derive(normalized);
        String phone = normalizePhone(request.getPhone());
        String email = normalizeEmail(request.getEmail());
        String additionalDuties = normalizeOptional(request.getAdditionalDuties());
        return teacherDirectoryRepository.findByFioTeacherIgnoreCase(normalized)
                .orElseGet(() -> {
                    TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
                    applyNameCases(entry, cases);
                    entry.setFioTeacherGenitive(first(request.getFioTeacherGenitive(), cases.genitive()));
                    entry.setFioTeacherDative(first(request.getFioTeacherDative(), cases.dative()));
                    entry.setFioTeacherAccusative(first(request.getFioTeacherAccusative(), cases.accusative()));
                    entry.setFioTeacherInstrumental(first(request.getFioTeacherInstrumental(), cases.instrumental()));
                    entry.setFioTeacherPrepositional(first(request.getFioTeacherPrepositional(), cases.prepositional()));
                    entry.setInitials(first(request.getInitials(), cases.initials()));
                    entry.setInitialsGenitive(first(request.getInitialsGenitive(), cases.initialsGenitive()));
                    entry.setInitialsDative(first(request.getInitialsDative(), cases.initialsDative()));
                    entry.setInitialsAccusative(first(request.getInitialsAccusative(), cases.initialsAccusative()));
                    entry.setInitialsInstrumental(first(request.getInitialsInstrumental(), cases.initialsInstrumental()));
                    entry.setInitialsPrepositional(first(request.getInitialsPrepositional(), cases.initialsPrepositional()));
                    entry.setPhone(phone);
                    ensureUniqueTeacherEmail(email, null);
                    entry.setEmail(email);
                    entry.setAdditionalDuties(additionalDuties);
                    applyBuildingSelection(entry, request.getNumberSchoolBuilding(), request.getSchoolBuildingId());
                    entry.setPrimaryPosition(normalizeOptional(request.getPrimaryPosition()));
                    entry.setEmploymentType(normalizeOptional(request.getEmploymentType()));
                    entry.setEmploymentDate(request.getEmploymentDate());
                    return teacherDirectoryRepository.save(entry);
                });
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry update(Long teacherId, TeacherUpdateRequest request) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        if (request != null) {
            String previousFio = entry.getFioTeacher();
            if (request.getFioTeacher() != null && !request.getFioTeacher().isBlank()) {
                entry.setFioTeacher(request.getFioTeacher().trim());
            }
            var cases = RussianNameCases.derive(entry.getFioTeacher());
            boolean fioChanged = !Objects.equals(previousFio, entry.getFioTeacher());
            entry.setFioTeacherGenitive(nameForm(request.getFioTeacherGenitive(), entry.getFioTeacherGenitive(), cases.genitive(), fioChanged));
            entry.setFioTeacherDative(nameForm(request.getFioTeacherDative(), entry.getFioTeacherDative(), cases.dative(), fioChanged));
            entry.setFioTeacherAccusative(nameForm(request.getFioTeacherAccusative(), entry.getFioTeacherAccusative(), cases.accusative(), fioChanged));
            entry.setFioTeacherInstrumental(nameForm(request.getFioTeacherInstrumental(), entry.getFioTeacherInstrumental(), cases.instrumental(), fioChanged));
            entry.setFioTeacherPrepositional(nameForm(request.getFioTeacherPrepositional(), entry.getFioTeacherPrepositional(), cases.prepositional(), fioChanged));
            entry.setInitials(nameForm(request.getInitials(), entry.getInitials(), cases.initials(), fioChanged));
            entry.setInitialsGenitive(nameForm(request.getInitialsGenitive(), entry.getInitialsGenitive(), cases.initialsGenitive(), fioChanged));
            entry.setInitialsDative(nameForm(request.getInitialsDative(), entry.getInitialsDative(), cases.initialsDative(), fioChanged));
            entry.setInitialsAccusative(nameForm(request.getInitialsAccusative(), entry.getInitialsAccusative(), cases.initialsAccusative(), fioChanged));
            entry.setInitialsInstrumental(nameForm(request.getInitialsInstrumental(), entry.getInitialsInstrumental(), cases.initialsInstrumental(), fioChanged));
            entry.setInitialsPrepositional(nameForm(request.getInitialsPrepositional(), entry.getInitialsPrepositional(), cases.initialsPrepositional(), fioChanged));
            entry.setPhone(normalizePhone(request.getPhone()));
            String email = normalizeEmail(request.getEmail());
            ensureUniqueTeacherEmail(email, teacherId);
            entry.setEmail(email);
            entry.setAdditionalDuties(normalizeOptional(request.getAdditionalDuties()));
            if (request.getSchoolBuildingId() != null || request.getNumberSchoolBuilding() != null) {
                applyBuildingSelection(entry, request.getNumberSchoolBuilding(), request.getSchoolBuildingId());
            }
            if (request.getPrimaryPosition() != null) entry.setPrimaryPosition(normalizeOptional(request.getPrimaryPosition()));
            if (request.getEmploymentType() != null) entry.setEmploymentType(normalizeOptional(request.getEmploymentType()));
            if (request.getEmploymentDate() != null) entry.setEmploymentDate(request.getEmploymentDate());
        }
        return teacherDirectoryRepository.save(entry);
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry markForDismissal(Long teacherId, LocalDate dismissalDate, String markedBy) {
        if (dismissalDate == null) {
            throw new IllegalArgumentException("dismissalDate is required");
        }

        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        entry.setDismissalDate(dismissalDate);
        entry.setPlannedDismissalMarkedBy(normalizeOptional(markedBy));

        TeacherDirectoryEntry vacancyTeacher = ensureVacancyTeacher();
        manualLoadEntryRepository.findByTeacherId(entry.getId()).forEach(loadEntry -> {
            if (loadEntry.getLoadToDate() == null) {
                return;
            }
            if (loadEntry.getLoadToDate().isAfter(dismissalDate)) {
                LocalDate originalLoadToDate = loadEntry.getLoadToDate();
                loadEntry.setBackupLoadToDate(loadEntry.getLoadToDate());
                loadEntry.setLoadToDate(dismissalDate);
                loadEntry.setDismissalAdjusted(true);
                manualLoadEntryRepository.save(loadEntry);

                LocalDate vacancyFrom = dismissalDate.plusDays(1);
                if (!vacancyFrom.isAfter(originalLoadToDate) && !isLoadAlreadyAssigned(loadEntry, vacancyFrom, originalLoadToDate)) {
                    var vacancyEntry = new org.school.personalLoad.model.ManualLoadEntry();
                    vacancyEntry.setAcademicYear(loadEntry.getAcademicYear());
                    vacancyEntry.setTeacherId(vacancyTeacher.getId());
                    vacancyEntry.setFioTeacher(vacancyTeacher.getFioTeacher());
                    vacancyEntry.setNumberSchoolBuilding(loadEntry.getNumberSchoolBuilding());
                    vacancyEntry.setSchoolBuildingId(loadEntry.getSchoolBuildingId());
                    vacancyEntry.setSubject(loadEntry.getSubject());
                    vacancyEntry.setSubjectName(loadEntry.getSubjectName());
                    vacancyEntry.setClassName(loadEntry.getClassName());
                    vacancyEntry.setClassId(loadEntry.getClassId());
                    vacancyEntry.setMetaGroupId(loadEntry.getMetaGroupId());
                    vacancyEntry.setLoad(loadEntry.getLoad());
                    vacancyEntry.setGroupNameEducationalPlan(loadEntry.getGroupNameEducationalPlan());
                    vacancyEntry.setGroupLoad(loadEntry.getGroupLoad());
                    vacancyEntry.setCurriculumModuleId(loadEntry.getCurriculumModuleId());
                    vacancyEntry.setCurriculumPart(loadEntry.getCurriculumPart());
                    vacancyEntry.setEducationLevel(loadEntry.getEducationLevel());
                    vacancyEntry.setStudyPeriod(loadEntry.getStudyPeriod());
                    vacancyEntry.setLoadFromDate(vacancyFrom);
                    vacancyEntry.setLoadToDate(originalLoadToDate);
                    manualLoadEntryRepository.save(vacancyEntry);
                }
            }
        });

        return teacherDirectoryRepository.save(entry);
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry markPlannedDismissal(Long teacherId, LocalDate plannedDismissalDate, String comment, String markedBy) {
        if (plannedDismissalDate == null) {
            throw new IllegalArgumentException("plannedDismissalDate is required");
        }
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        entry.setPlannedDismissalDate(plannedDismissalDate);
        entry.setPlannedDismissalComment(normalizeOptional(comment));
        entry.setPlannedDismissalMarkedBy(normalizeOptional(markedBy));
        return teacherDirectoryRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherOneCImportDtos.Preview previewOneCImport(MultipartFile file) {
        OneCWorkbookData workbookData = parseOneCWorkbook(file);
        LocalDate effectiveDate = LocalDate.now();
        Map<String, TeacherDirectoryEntry> teachersByFio = teacherDirectoryRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        teacher -> normalizeFioKey(teacher.getFioTeacher()),
                        teacher -> teacher,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<TeacherOneCImportDtos.PreviewRow> changes = new ArrayList<>();
        for (Map.Entry<String, List<OneCEmploymentRow>> group : workbookData.rowsByFio().entrySet()) {
            List<OneCEmploymentRow> rows = group.getValue();
            OneCEmploymentRow activePrimary = rows.stream()
                    .filter(row -> row.isActiveOn(effectiveDate) && row.isPrimaryEmployment())
                    .max(Comparator.comparing(OneCEmploymentRow::employmentDate,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            OneCEmploymentRow activeAdditional = rows.stream()
                    .filter(row -> row.isActiveOn(effectiveDate) && !row.isPrimaryEmployment())
                    .max(Comparator
                            .comparingInt(OneCEmploymentRow::additionalPriority)
                            .thenComparing(OneCEmploymentRow::employmentDate,
                                    Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            OneCEmploymentRow endedPrimary = rows.stream()
                    .filter(OneCEmploymentRow::isPrimaryEmployment)
                    .filter(row -> row.dismissalDate() != null && !row.dismissalDate().isAfter(effectiveDate))
                    .max(Comparator.comparing(OneCEmploymentRow::dismissalDate))
                    .orElse(null);
            TeacherDirectoryEntry teacher = teachersByFio.get(group.getKey());
            TeacherOneCImportDtos.PreviewRow previewRow = buildOneCPreviewRow(
                    teacher,
                    activePrimary,
                    activeAdditional,
                    endedPrimary
            );
            if (previewRow != null) {
                changes.add(previewRow);
            }
        }
        changes.sort(Comparator.comparing(TeacherOneCImportDtos.PreviewRow::fio, String.CASE_INSENSITIVE_ORDER));
        return new TeacherOneCImportDtos.Preview(workbookData.sourceRowCount(), effectiveDate, changes);
    }

    @Override
    @Transactional
    public Map<String, Object> applyOneCImport(MultipartFile file,
                                               TeacherOneCImportDtos.ApplyRequest request,
                                               String processedBy) {
        TeacherOneCImportDtos.Preview preview = previewOneCImport(file);
        Map<String, TeacherOneCImportDtos.PreviewRow> previewByFio = preview.rows().stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> normalizeFioKey(row.fio()),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, String> decisions = Optional.ofNullable(request)
                .map(TeacherOneCImportDtos.ApplyRequest::decisions)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .filter(decision -> decision.fio() != null && decision.action() != null)
                .collect(java.util.stream.Collectors.toMap(
                        decision -> normalizeFioKey(decision.fio()),
                        decision -> decision.action().trim().toUpperCase(Locale.ROOT),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        int added = 0;
        int updated = 0;
        int dismissed = 0;
        int restored = 0;
        int ignored = 0;
        for (TeacherOneCImportDtos.PreviewRow row : preview.rows()) {
            String action = decisions.getOrDefault(normalizeFioKey(row.fio()), "IGNORE");
            if (!row.allowedActions().contains(action)) {
                throw new IllegalArgumentException("Действие «" + action + "» недоступно для " + row.fio());
            }
            if ("IGNORE".equals(action)) {
                ignored++;
                continue;
            }
            TeacherDirectoryEntry teacher = row.teacherId() == null
                    ? null
                    : teacherDirectoryRepository.findById(row.teacherId()).orElse(null);
            switch (action) {
                case "ADD", "ACCEPT_ADDITIONAL" -> {
                    if (teacher == null) {
                        teacher = new TeacherDirectoryEntry();
                        teacher.setFioTeacher(row.fio());
                        teacher.setInitials(shortFioFromFull(row.fio()));
                        added++;
                    } else {
                        if (teacher.isArchived()) {
                            teacher.setArchived(false);
                            teacher.setArchivedAt(null);
                        }
                        if (teacher.getDismissalDate() != null) {
                            restore(teacher.getId());
                            teacher = teacherDirectoryRepository.findById(teacher.getId()).orElseThrow();
                            restored++;
                        } else {
                            updated++;
                        }
                    }
                    applyOneCEmploymentSnapshot(teacher, row);
                    teacherDirectoryRepository.save(teacher);
                }
                case "UPDATE" -> {
                    if (teacher == null) {
                        throw new IllegalArgumentException("Сотрудник " + row.fio() + " не найден для обновления");
                    }
                    applyOneCEmploymentSnapshot(teacher, row);
                    teacherDirectoryRepository.save(teacher);
                    updated++;
                }
                case "RESTORE" -> {
                    if (teacher == null) {
                        throw new IllegalArgumentException("Сотрудник " + row.fio() + " не найден для восстановления");
                    }
                    if (teacher.isArchived()) {
                        teacher.setArchived(false);
                        teacher.setArchivedAt(null);
                        teacherDirectoryRepository.save(teacher);
                    }
                    if (teacher.getDismissalDate() != null) {
                        restore(teacher.getId());
                    }
                    teacher = teacherDirectoryRepository.findById(teacher.getId()).orElseThrow();
                    applyOneCEmploymentSnapshot(teacher, row);
                    teacherDirectoryRepository.save(teacher);
                    restored++;
                }
                case "DISMISS" -> {
                    if (teacher == null) {
                        throw new IllegalArgumentException("Сотрудник " + row.fio() + " не найден для увольнения");
                    }
                    LocalDate dismissalDate = Optional.ofNullable(row.dismissalDate())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "В выгрузке 1С не указана дата увольнения для " + row.fio()));
                    markForDismissal(teacher.getId(), dismissalDate, processedBy);
                    dismissed++;
                }
                default -> throw new IllegalArgumentException("Неизвестное действие импорта: " + action);
            }
        }
        return Map.of(
                "status", "ok",
                "sourceRows", preview.sourceRowCount(),
                "added", added,
                "updated", updated,
                "dismissed", dismissed,
                "restored", restored,
                "ignored", ignored
        );
    }

    private TeacherOneCImportDtos.PreviewRow buildOneCPreviewRow(TeacherDirectoryEntry teacher,
                                                                 OneCEmploymentRow activePrimary,
                                                                 OneCEmploymentRow activeAdditional,
                                                                 OneCEmploymentRow endedPrimary) {
        OneCEmploymentRow proposed = activePrimary != null ? activePrimary
                : activeAdditional != null ? activeAdditional : endedPrimary;
        if (proposed == null) {
            return null;
        }
        List<String> allowedActions;
        String recommendedAction;
        String message;
        if (teacher == null) {
            if (activePrimary != null) {
                allowedActions = List.of("ADD", "IGNORE");
                recommendedAction = "ADD";
                message = "Новый сотрудник с действующим основным местом работы.";
            } else if (activeAdditional != null) {
                allowedActions = List.of("ACCEPT_ADDITIONAL", "IGNORE");
                recommendedAction = "IGNORE";
                message = "В 1С есть только действующее совместительство. Требуется подтверждение приёма.";
            } else {
                return null;
            }
        } else if (activePrimary != null) {
            boolean requiresRestore = teacher.isArchived() || teacher.getDismissalDate() != null;
            if (requiresRestore) {
                allowedActions = List.of("RESTORE", "IGNORE");
                recommendedAction = "IGNORE";
                message = "В 1С основное место действует, а в программе сотрудник уволен или находится в архиве.";
            } else if (oneCEmploymentDiffers(teacher, activePrimary)) {
                allowedActions = List.of("UPDATE", "IGNORE");
                recommendedAction = "UPDATE";
                message = "Основная должность или кадровые реквизиты отличаются от выгрузки 1С.";
            } else {
                return null;
            }
        } else if (activeAdditional != null) {
            allowedActions = teacher.getDismissalDate() == null && !teacher.isArchived()
                    ? List.of("DISMISS", "ACCEPT_ADDITIONAL", "IGNORE")
                    : List.of("ACCEPT_ADDITIONAL", "IGNORE");
            recommendedAction = "IGNORE";
            message = "Основное место завершено, но найдено действующее совместительство. Подтвердите увольнение или продолжение работы.";
        } else {
            if (teacher.getDismissalDate() != null || teacher.isArchived()) {
                return null;
            }
            allowedActions = List.of("DISMISS", "IGNORE");
            recommendedAction = "IGNORE";
            message = "В 1С основное место работы завершено. Увольнение требует подтверждения.";
        }
        return new TeacherOneCImportDtos.PreviewRow(
                proposed.fio(),
                teacher == null ? null : teacher.getId(),
                teacher == null ? null : teacher.getPrimaryPosition(),
                proposed.position(),
                proposed.personnelNumber(),
                proposed.employmentType(),
                proposed.employmentDate(),
                endedPrimary == null ? proposed.dismissalDate() : endedPrimary.dismissalDate(),
                activePrimary != null,
                activeAdditional != null,
                message,
                allowedActions,
                recommendedAction
        );
    }

    private boolean oneCEmploymentDiffers(TeacherDirectoryEntry teacher, OneCEmploymentRow row) {
        return !Objects.equals(normalizeOptional(teacher.getPrimaryPosition()), normalizeOptional(row.position()))
                || !Objects.equals(normalizeOptional(teacher.getPersonnelNumber()), normalizeOptional(row.personnelNumber()))
                || !Objects.equals(normalizeOptional(teacher.getEmploymentType()), normalizeOptional(row.employmentType()))
                || !Objects.equals(teacher.getEmploymentDate(), row.employmentDate());
    }

    private void applyOneCEmploymentSnapshot(TeacherDirectoryEntry teacher,
                                             TeacherOneCImportDtos.PreviewRow row) {
        teacher.setPrimaryPosition(normalizeOptional(row.proposedPosition()));
        teacher.setPersonnelNumber(normalizeOptional(row.personnelNumber()));
        teacher.setEmploymentType(normalizeOptional(row.employmentType()));
        teacher.setEmploymentDate(row.employmentDate());
        teacher.setLastOneCSyncAt(LocalDateTime.now());
        if (isBlank(teacher.getInitials())) {
            teacher.setInitials(shortFioFromFull(teacher.getFioTeacher()));
        }
        fillMissingNameCases(teacher);
    }

    private OneCWorkbookData parseOneCWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите выгрузку 1С в формате .xls или .xlsx");
        }
        String filename = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xls") && !filename.endsWith(".xlsx")) {
            throw new IllegalArgumentException("Выгрузка 1С должна быть файлом .xls или .xlsx");
        }
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("В выгрузке 1С нет листов");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(new Locale("ru", "RU"));
            HeaderMapping header = findOneCHeader(sheet, formatter);
            Map<String, List<OneCEmploymentRow>> rowsByFio = new LinkedHashMap<>();
            int sourceRows = 0;
            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String fio = formattedCell(row.getCell(header.fio()), formatter);
                if (fio.isBlank()) continue;
                sourceRows++;
                OneCEmploymentRow employment = new OneCEmploymentRow(
                        fio.trim(),
                        formattedCell(row.getCell(header.personnelNumber()), formatter),
                        cleanOneCPosition(formattedCell(row.getCell(header.position()), formatter)),
                        formattedCell(row.getCell(header.employmentType()), formatter),
                        readOneCDate(row.getCell(header.employmentDate()), formatter),
                        readOneCDate(row.getCell(header.dismissalDate()), formatter)
                );
                rowsByFio.computeIfAbsent(normalizeFioKey(fio), ignored -> new ArrayList<>()).add(employment);
            }
            if (sourceRows == 0) {
                throw new IllegalArgumentException("В выгрузке 1С не найдено ни одной кадровой строки");
            }
            return new OneCWorkbookData(sourceRows, rowsByFio);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка чтения выгрузки 1С", e);
            throw new IllegalArgumentException("Не удалось прочитать выгрузку 1С: " + e.getMessage(), e);
        }
    }

    private HeaderMapping findOneCHeader(Sheet sheet, DataFormatter formatter) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 20); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < row.getLastCellNum(); columnIndex++) {
                String header = normalizeOneCHeader(formattedCell(row.getCell(columnIndex), formatter));
                if (!header.isBlank()) columns.put(header, columnIndex);
            }
            Integer fio = firstHeader(columns, "имя", "фио");
            Integer personnelNumber = firstHeader(columns, "табномер", "табельныйномер");
            Integer position = firstHeader(columns, "должностьпоштатномурасписанию", "должность");
            Integer employmentDate = firstHeader(columns, "датаприема", "датаприёма");
            Integer dismissalDate = firstHeader(columns, "датаувольнения");
            Integer employmentType = firstHeader(columns, "видзанятости");
            if (fio != null && personnelNumber != null && position != null
                    && employmentDate != null && dismissalDate != null && employmentType != null) {
                return new HeaderMapping(
                        rowIndex, fio, personnelNumber, position, employmentDate, dismissalDate, employmentType);
            }
        }
        throw new IllegalArgumentException(
                "Не найдены обязательные колонки выгрузки 1С: Имя, Таб. номер, Должность, даты приёма/увольнения, Вид занятости");
    }

    private Integer firstHeader(Map<String, Integer> columns, String... aliases) {
        for (String alias : aliases) {
            Integer index = columns.get(alias);
            if (index != null) return index;
        }
        return null;
    }

    private String formattedCell(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalizeOneCHeader(String value) {
        return Objects.toString(value, "")
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^а-яa-z0-9]+", "");
    }

    private String cleanOneCPosition(String value) {
        return normalizeOptional(Objects.toString(value, "")
                .replaceFirst("\\s*/[^/]+/\\s*$", "")
                .trim());
    }

    private LocalDate readOneCDate(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String value = formattedCell(cell, formatter);
        if (value.isBlank()) return null;
        for (DateTimeFormatter dateFormat : List.of(
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        )) {
            try {
                return LocalDate.parse(value, dateFormat);
            } catch (DateTimeParseException ignored) {
                // try the next supported format
            }
        }
        throw new IllegalArgumentException("Не удалось распознать дату в выгрузке 1С: " + value);
    }

    private String normalizeFioKey(String fio) {
        return Objects.toString(fio, "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String shortFioFromFull(String fio) {
        String[] parts = Objects.toString(fio, "").trim().split("\\s+");
        if (parts.length < 2) return Objects.toString(fio, "").trim();
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < Math.min(parts.length, 3); i++) {
            if (!parts[i].isBlank()) {
                result.append(' ').append(Character.toUpperCase(parts[i].charAt(0))).append('.');
            }
        }
        return result.toString();
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry cancelPlannedDismissal(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        if (entry.getDismissalDate() != null) {
            throw new IllegalStateException("Фактическое увольнение отменяется действием «Восстановить»");
        }
        entry.setPlannedDismissalDate(null);
        entry.setPlannedDismissalComment(null);
        entry.setPlannedDismissalMarkedBy(null);
        return teacherDirectoryRepository.save(entry);
    }


    @Override
    @Transactional
    public TeacherDirectoryEntry restore(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        TeacherDirectoryEntry vacancyTeacher = teacherDirectoryRepository.findByFioTeacherIgnoreCase(VACANCY_TEACHER).orElse(null);
        List<org.school.personalLoad.model.ManualLoadEntry> vacancyRows = vacancyTeacher == null
                ? List.of()
                : manualLoadEntryRepository.findByTeacherId(vacancyTeacher.getId());
        List<org.school.personalLoad.model.ManualLoadEntry> generatedVacancies = new ArrayList<>();

        manualLoadEntryRepository.findByTeacherId(entry.getId()).forEach(loadEntry -> {
            if (!loadEntry.isDismissalAdjusted() || loadEntry.getBackupLoadToDate() == null) {
                return;
            }
            LocalDate vacancyFrom = loadEntry.getLoadToDate() == null ? null : loadEntry.getLoadToDate().plusDays(1);
            vacancyRows.stream()
                    .filter(vacancy -> sameGeneratedVacancy(loadEntry, vacancy, vacancyFrom, loadEntry.getBackupLoadToDate()))
                    .findFirst()
                    .ifPresent(generatedVacancies::add);
            loadEntry.setLoadToDate(loadEntry.getBackupLoadToDate());
            loadEntry.setBackupLoadToDate(null);
            loadEntry.setDismissalAdjusted(false);
            manualLoadEntryRepository.save(loadEntry);
        });
        if (!generatedVacancies.isEmpty()) {
            manualLoadEntryRepository.deleteAll(generatedVacancies);
        }

        entry.setDismissalDate(null);
        entry.setPlannedDismissalDate(null);
        entry.setPlannedDismissalComment(null);
        entry.setPlannedDismissalMarkedBy(null);
        return teacherDirectoryRepository.save(entry);
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry archive(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        if (VACANCY_TEACHER.equalsIgnoreCase(entry.getFioTeacher())) {
            throw new IllegalStateException("Системную запись «Вакансия» нельзя перенести в архив");
        }
        entry.setArchived(true);
        entry.setArchivedAt(LocalDateTime.now());
        return teacherDirectoryRepository.save(entry);
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry unarchive(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        entry.setArchived(false);
        entry.setArchivedAt(null);
        return teacherDirectoryRepository.save(entry);
    }

    @Override
    public void deleteById(Long teacherId) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        if (manualLoadEntryRepository.existsByTeacherId(entry.getId())) {
            throw new IllegalStateException("Педагог назначен на нагрузку. Сначала снимите нагрузку, затем удаляйте из справочника.");
        }

        teacherDirectoryRepository.delete(entry);
    }

    @Override
    public List<TeacherDirectoryEntry> findAll() {
        ensureVacancyTeacher();
        return activeTeachers();
    }

    @Override
    public List<TeacherDirectoryEntry> findArchived() {
        return teacherDirectoryRepository.findAll().stream()
                .filter(TeacherDirectoryEntry::isArchived)
                .sorted(Comparator.comparing(
                        teacher -> Objects.toString(teacher.getFioTeacher(), ""),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    @Override
    public void clearAll() {
        teacherDirectoryRepository.deleteAll();
    }

    private Sheet findTeachersSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName().toLowerCase();
            if (name.contains("педагог")) {
                return sheet;
            }
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue()).trim();
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        yield String.valueOf((int) cell.getNumericCellValue()).trim();
                    } catch (Exception ignored) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private void applyNameCases(TeacherDirectoryEntry entry, NameCases cases) {
        entry.setFioTeacher(cases.nominative());
        entry.setFioTeacherGenitive(cases.genitive());
        entry.setFioTeacherDative(cases.dative());
        entry.setFioTeacherAccusative(cases.accusative());
        entry.setFioTeacherInstrumental(cases.instrumental());
        entry.setFioTeacherPrepositional(cases.prepositional());
        entry.setInitials(cases.initials());
        entry.setInitialsGenitive(cases.initialsGenitive());
        entry.setInitialsDative(cases.initialsDative());
        entry.setInitialsAccusative(cases.initialsAccusative());
        entry.setInitialsInstrumental(cases.initialsInstrumental());
        entry.setInitialsPrepositional(cases.initialsPrepositional());
    }

    private boolean fillMissingNameCases(TeacherDirectoryEntry entry) {
        NameCases cases = RussianNameCases.derive(entry.getFioTeacher());
        boolean changed = false;
        if (isBlank(entry.getFioTeacherGenitive())) { entry.setFioTeacherGenitive(cases.genitive()); changed = true; }
        if (isBlank(entry.getFioTeacherDative())) { entry.setFioTeacherDative(cases.dative()); changed = true; }
        if (isBlank(entry.getFioTeacherAccusative())) { entry.setFioTeacherAccusative(cases.accusative()); changed = true; }
        if (isBlank(entry.getFioTeacherInstrumental())) { entry.setFioTeacherInstrumental(cases.instrumental()); changed = true; }
        if (isBlank(entry.getFioTeacherPrepositional())) { entry.setFioTeacherPrepositional(cases.prepositional()); changed = true; }
        if (isBlank(entry.getInitials())) { entry.setInitials(cases.initials()); changed = true; }
        if (isBlank(entry.getInitialsGenitive())) { entry.setInitialsGenitive(cases.initialsGenitive()); changed = true; }
        if (isBlank(entry.getInitialsDative())) { entry.setInitialsDative(cases.initialsDative()); changed = true; }
        if (isBlank(entry.getInitialsAccusative())) { entry.setInitialsAccusative(cases.initialsAccusative()); changed = true; }
        if (isBlank(entry.getInitialsInstrumental())) { entry.setInitialsInstrumental(cases.initialsInstrumental()); changed = true; }
        if (isBlank(entry.getInitialsPrepositional())) { entry.setInitialsPrepositional(cases.initialsPrepositional()); changed = true; }
        return changed;
    }

    private String nameForm(String requested, String existing, String generated, boolean fioChanged) {
        if (requested != null) return first(requested, generated);
        if (fioChanged || isBlank(existing)) return generated;
        return existing.trim();
    }

    private String first(String value, String fallback) {
        String normalized = normalizeOptional(value);
        return normalized == null ? fallback : normalized;
    }

    private void applyBuildingSelection(TeacherDirectoryEntry teacher,
                                        String legacyBuilding,
                                        Long schoolBuildingId) {
        SchoolBuilding selectedSite = schoolBuildingId == null
                ? resolveSchoolBuilding(legacyBuilding, schoolBuildingRepository.findAll())
                : schoolBuildingRepository.findById(schoolBuildingId)
                .orElseThrow(() -> new IllegalArgumentException("Физическая площадка не найдена: " + schoolBuildingId));
        applyBuildingSelection(teacher, legacyBuilding, selectedSite);
    }

    private void applyBuildingSelection(TeacherDirectoryEntry teacher,
                                        String legacyBuilding,
                                        SchoolBuilding selectedSite) {
        if (selectedSite == null) {
            teacher.setSchoolBuildingId(null);
            teacher.setNumberSchoolBuilding(normalizeOptional(legacyBuilding));
            return;
        }
        teacher.setSchoolBuildingId(selectedSite.getId());
        teacher.setNumberSchoolBuilding(organizationalBuildingCode(selectedSite));
    }

    private SchoolBuilding resolveSchoolBuilding(String value, List<SchoolBuilding> sites) {
        String token = normalizeBuildingToken(value);
        if (token.isBlank() || sites == null || sites.isEmpty()) return null;
        List<SchoolBuilding> directMatches = sites.stream()
                .filter(site -> token.equals(normalizeBuildingToken(site.getAddress()))
                        || token.equals(normalizeBuildingToken(site.getCode()))
                        || token.equals(normalizeBuildingToken(
                        organizationalBuildingCode(site) + " — " + Objects.toString(site.getAddress(), ""))))
                .toList();
        if (directMatches.size() == 1) return directMatches.get(0);
        List<SchoolBuilding> groupMatches = sites.stream()
                .filter(site -> token.equals(normalizeBuildingToken(organizationalBuildingCode(site))))
                .toList();
        return groupMatches.size() == 1 ? groupMatches.get(0) : null;
    }

    private String organizationalBuildingCode(SchoolBuilding site) {
        if (site != null && site.getBuildingGroup() != null && site.getBuildingGroup().getCode() != null) {
            return site.getBuildingGroup().getCode().trim();
        }
        return site == null ? "" : Objects.toString(site.getCode(), "").trim();
    }

    private String normalizeBuildingToken(String value) {
        return Objects.toString(value, "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String excelText(String value) {
        String normalized = INVALID_EXCEL_TEXT_CHARS.matcher(Objects.toString(value, "")).replaceAll(" ");
        return normalized.length() > EXCEL_CELL_TEXT_LIMIT
                ? normalized.substring(0, EXCEL_CELL_TEXT_LIMIT)
                : normalized;
    }

    private TeacherDirectoryEntry ensureVacancyTeacher() {
        TeacherDirectoryEntry vacancy = teacherDirectoryRepository.findByFioTeacherIgnoreCase(VACANCY_TEACHER).orElseGet(() -> {
            TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
            entry.setFioTeacher(VACANCY_TEACHER);
            entry.setFioTeacherDative("Вакансии");
            return teacherDirectoryRepository.save(entry);
        });
        if (vacancy.isArchived()) {
            vacancy.setArchived(false);
            vacancy.setArchivedAt(null);
            return teacherDirectoryRepository.save(vacancy);
        }
        return vacancy;
    }

    private List<TeacherDirectoryEntry> activeTeachers() {
        return teacherDirectoryRepository.findAll().stream()
                .filter(teacher -> !teacher.isArchived())
                .sorted(Comparator.comparing(
                        teacher -> Objects.toString(teacher.getFioTeacher(), ""),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    private boolean isLoadAlreadyAssigned(org.school.personalLoad.model.ManualLoadEntry source,
                                          LocalDate fromDate,
                                          LocalDate toDate) {
        return manualLoadEntryRepository.findAll().stream().anyMatch(existing -> {
            if (existing.getId() != null && existing.getId().equals(source.getId())) {
                return false;
            }
            if (existing.getFioTeacher() == null) return false;
            if (!Objects.equals(existing.getAcademicYear(), source.getAcademicYear())) return false;
            if (!Objects.equals(existing.getNumberSchoolBuilding(), source.getNumberSchoolBuilding())) return false;
            if (!Objects.equals(existing.getSchoolBuildingId(), source.getSchoolBuildingId())) return false;
            if (!Objects.equals(existing.getSubjectName(), source.getSubjectName())) return false;
            if (!Objects.equals(existing.getClassName(), source.getClassName())) return false;
            if (!Objects.equals(existing.getClassId(), source.getClassId())) return false;
            if (!Objects.equals(existing.getMetaGroupId(), source.getMetaGroupId())) return false;
            if (!Objects.equals(existing.getGroupNameEducationalPlan(), source.getGroupNameEducationalPlan())) return false;
            if (!Objects.equals(existing.getCurriculumPart(), source.getCurriculumPart())) return false;
            if (!Objects.equals(existing.getStudyPeriod(), source.getStudyPeriod())) return false;
            if (existing.getLoadFromDate() == null || existing.getLoadToDate() == null) return false;
            return !existing.getLoadFromDate().isAfter(toDate) && !existing.getLoadToDate().isBefore(fromDate);
        });
    }

    private boolean sameGeneratedVacancy(org.school.personalLoad.model.ManualLoadEntry source,
                                         org.school.personalLoad.model.ManualLoadEntry vacancy,
                                         LocalDate vacancyFrom,
                                         LocalDate vacancyTo) {
        return vacancyFrom != null
                && Objects.equals(vacancy.getAcademicYear(), source.getAcademicYear())
                && Objects.equals(vacancy.getNumberSchoolBuilding(), source.getNumberSchoolBuilding())
                && Objects.equals(vacancy.getSchoolBuildingId(), source.getSchoolBuildingId())
                && Objects.equals(vacancy.getSubjectName(), source.getSubjectName())
                && Objects.equals(vacancy.getClassName(), source.getClassName())
                && Objects.equals(vacancy.getClassId(), source.getClassId())
                && Objects.equals(vacancy.getMetaGroupId(), source.getMetaGroupId())
                && Objects.equals(vacancy.getGroupNameEducationalPlan(), source.getGroupNameEducationalPlan())
                && Objects.equals(vacancy.getCurriculumModuleId(), source.getCurriculumModuleId())
                && Objects.equals(vacancy.getCurriculumPart(), source.getCurriculumPart())
                && Objects.equals(vacancy.getStudyPeriod(), source.getStudyPeriod())
                && Objects.equals(vacancy.getLoadFromDate(), vacancyFrom)
                && Objects.equals(vacancy.getLoadToDate(), vacancyTo);
    }

    private String normalizePhone(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) return null;
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("8")) digits = "7" + digits.substring(1);
        if (digits.length() != 11 || !digits.startsWith("7")) {
            throw new IllegalArgumentException("Телефон должен содержать 11 цифр в формате +7...");
        }
        return "+" + digits;
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) return null;
        String email = normalized.toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Некорректный email");
        }
        return email;
    }

    private void ensureUniqueTeacherEmail(String email, Long selfId) {
        if (email == null) return;
        for (TeacherDirectoryEntry row : teacherDirectoryRepository.findAll()) {
            if (row.getEmail() == null) continue;
            if (row.getEmail().equalsIgnoreCase(email) && !Objects.equals(row.getId(), selfId)) {
                throw new IllegalStateException("Педагог с таким email уже существует");
            }
        }
    }

    private record HeaderMapping(
            int rowIndex,
            int fio,
            int personnelNumber,
            int position,
            int employmentDate,
            int dismissalDate,
            int employmentType
    ) {
    }

    private record OneCEmploymentRow(
            String fio,
            String personnelNumber,
            String position,
            String employmentType,
            LocalDate employmentDate,
            LocalDate dismissalDate
    ) {
        private boolean isActiveOn(LocalDate date) {
            return (employmentDate == null || !employmentDate.isAfter(date))
                    && (dismissalDate == null || dismissalDate.isAfter(date));
        }

        private boolean isPrimaryEmployment() {
            return Objects.toString(employmentType, "")
                    .toLowerCase(Locale.ROOT)
                    .contains("основное место");
        }

        private int additionalPriority() {
            String normalized = Objects.toString(employmentType, "").toLowerCase(Locale.ROOT);
            if (normalized.contains("внутреннее")) return 2;
            if (normalized.contains("внешнее")) return 1;
            return 0;
        }
    }

    private record OneCWorkbookData(
            int sourceRowCount,
            Map<String, List<OneCEmploymentRow>> rowsByFio
    ) {
    }

}
