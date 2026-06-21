package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherUpdateRequest;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.TeacherDirectoryService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherDirectoryServiceImpl implements TeacherDirectoryService {
    private static final String VACANCY_TEACHER = "Вакансия";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен");
        }

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        Set<String> seen = new HashSet<>();

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
                    if (!Objects.equals(fioDative, teacher.getFioTeacherDative())) { teacher.setFioTeacherDative(fioDative); changed = true; }
                    if (!Objects.equals(initials, teacher.getInitials())) { teacher.setInitials(initials); changed = true; }
                    if (!Objects.equals(phone, teacher.getPhone())) { teacher.setPhone(phone); changed = true; }
                    if (!Objects.equals(email, teacher.getEmail())) { ensureUniqueTeacherEmail(email, teacher.getId()); teacher.setEmail(email); changed = true; }
                    if (!Objects.equals(additionalDuties, teacher.getAdditionalDuties())) { teacher.setAdditionalDuties(additionalDuties); changed = true; }
                    if (!Objects.equals(building, teacher.getNumberSchoolBuilding())) { teacher.setNumberSchoolBuilding(building); changed = true; }
                    if (changed) { teacherDirectoryRepository.save(teacher); updated++; } else { skipped++; }
                    continue;
                }

                TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
                entry.setFioTeacher(normalized);
                entry.setFioTeacherDative(fioDative);
                entry.setInitials(initials);
                entry.setPhone(phone);
                ensureUniqueTeacherEmail(email, null);
                entry.setEmail(email);
                entry.setAdditionalDuties(additionalDuties);
                entry.setNumberSchoolBuilding(building);
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
            header.createCell(6).setCellValue("Корпус");

            List<TeacherDirectoryEntry> rows = activeTeachers();
            if (rows.isEmpty()) {
                Row example = sheet.createRow(1);
                example.createCell(0).setCellValue("Иванов Иван Иванович");
                example.createCell(1).setCellValue("Иванов И.И.");
                example.createCell(2).setCellValue("Иванову И.И.");
                example.createCell(3).setCellValue("+7 900 000-00-00");
                example.createCell(4).setCellValue("teacher@example.com");
                example.createCell(5).setCellValue("Классное руководство");
                example.createCell(6).setCellValue("СП1");
            } else {
                rows.sort(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER));
                int rowIndex = 1;
                for (TeacherDirectoryEntry entry : rows) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(entry.getFioTeacher());
                    row.createCell(1).setCellValue(Objects.toString(entry.getInitials(), ""));
                    row.createCell(2).setCellValue(Objects.toString(entry.getFioTeacherDative(), ""));
                    row.createCell(3).setCellValue(Objects.toString(entry.getPhone(), ""));
                    row.createCell(4).setCellValue(Objects.toString(entry.getEmail(), ""));
                    row.createCell(5).setCellValue(Objects.toString(entry.getAdditionalDuties(), ""));
                    row.createCell(6).setCellValue(Objects.toString(entry.getNumberSchoolBuilding(), ""));
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
    public TeacherDirectoryEntry create(TeacherCreateRequest request) {
        if (request == null || request.getFioTeacher() == null || request.getFioTeacher().isBlank()) {
            throw new IllegalArgumentException("fioTeacher is required");
        }

        String normalized = request.getFioTeacher().trim();
        String dative = normalizeOptional(request.getFioTeacherDative());
        String initials = normalizeOptional(request.getInitials());
        String phone = normalizePhone(request.getPhone());
        String email = normalizeEmail(request.getEmail());
        String additionalDuties = normalizeOptional(request.getAdditionalDuties());
        String building = normalizeOptional(request.getNumberSchoolBuilding());
        return teacherDirectoryRepository.findByFioTeacherIgnoreCase(normalized)
                .orElseGet(() -> {
                    TeacherDirectoryEntry entry = new TeacherDirectoryEntry();
                    entry.setFioTeacher(normalized);
                    entry.setFioTeacherDative(dative);
                    entry.setInitials(initials);
                    entry.setPhone(phone);
                    ensureUniqueTeacherEmail(email, null);
                    entry.setEmail(email);
                    entry.setAdditionalDuties(additionalDuties);
                    entry.setNumberSchoolBuilding(building);
                    return teacherDirectoryRepository.save(entry);
                });
    }

    @Override
    @Transactional
    public TeacherDirectoryEntry update(Long teacherId, TeacherUpdateRequest request) {
        TeacherDirectoryEntry entry = teacherDirectoryRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        if (request != null) {
            if (request.getFioTeacher() != null && !request.getFioTeacher().isBlank()) {
                entry.setFioTeacher(request.getFioTeacher().trim());
            }
            entry.setFioTeacherDative(normalizeOptional(request.getFioTeacherDative()));
            entry.setInitials(normalizeOptional(request.getInitials()));
            entry.setPhone(normalizePhone(request.getPhone()));
            String email = normalizeEmail(request.getEmail());
            ensureUniqueTeacherEmail(email, teacherId);
            entry.setEmail(email);
            entry.setAdditionalDuties(normalizeOptional(request.getAdditionalDuties()));
            entry.setNumberSchoolBuilding(normalizeOptional(request.getNumberSchoolBuilding()));
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
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
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

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
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
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
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

}
