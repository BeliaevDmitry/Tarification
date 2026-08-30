package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.TeacherCreateRequest;
import org.school.personalLoad.dto.TeacherDismissRequest;
import org.school.personalLoad.dto.TeacherPlannedDismissRequest;
import org.school.personalLoad.dto.TeacherOneCImportDtos;
import org.school.personalLoad.dto.TeacherUpdateRequest;
import org.school.personalLoad.dto.PersonnelDtos.AcceptEmployeeRequest;
import org.school.personalLoad.dto.PersonnelDtos.AcceptEmployeeResult;
import org.school.personalLoad.dto.PersonnelDtos.AutoBuildingResult;
import org.school.personalLoad.dto.PersonnelDtos.PersonnelRow;
import org.school.personalLoad.model.TeacherDirectoryEntry;
import org.school.personalLoad.model.UserActionLog;
import org.school.personalLoad.service.TeacherDirectoryService;
import org.school.personalLoad.service.PersonnelService;
import org.school.personalLoad.service.ProbeOrderService;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.UserActionLogService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherDirectoryController {

    private final TeacherDirectoryService teacherDirectoryService;
    private final PersonnelService personnelService;
    private final AcademicYearService academicYearService;
    private final UserActionLogService audit;
    private final ProbeOrderService probeOrderService;

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(teacherDirectoryService.importFromExcel(file));
    }

    @PostMapping(value = "/import-1c/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TeacherOneCImportDtos.Preview> previewOneCImport(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(teacherDirectoryService.previewOneCImport(file));
    }

    @PostMapping(value = "/import-1c/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> applyOneCImport(
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") TeacherOneCImportDtos.ApplyRequest request,
            HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        return ResponseEntity.ok(teacherDirectoryService.applyOneCImport(file, request, user.getFullName()));
    }

    @GetMapping({"/template", "/export"})
    public ResponseEntity<Resource> downloadTemplate() {
        Resource resource = teacherDirectoryService.buildImportTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teachers-template.xlsx")
                .body(resource);
    }

    @PostMapping
    public ResponseEntity<TeacherDirectoryEntry> create(@RequestBody TeacherCreateRequest request) {
        return ResponseEntity.ok(teacherDirectoryService.create(request));
    }

    @PatchMapping("/{teacherId}")
    public ResponseEntity<TeacherDirectoryEntry> update(@PathVariable Long teacherId,
                                                        @RequestBody TeacherUpdateRequest request) {
        return ResponseEntity.ok(teacherDirectoryService.update(teacherId, request));
    }

    @PatchMapping("/{teacherId}/dismiss")
    public ResponseEntity<TeacherDirectoryEntry> markForDismissal(@PathVariable Long teacherId,
                                                                  @RequestBody TeacherDismissRequest request,
                                                                  HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        return ResponseEntity.ok(teacherDirectoryService.markForDismissal(
                teacherId,
                request.getDismissalDate(),
                user.getFullName()
        ));
    }

    @PatchMapping("/{teacherId}/plan-dismiss")
    public ResponseEntity<TeacherDirectoryEntry> markTeacherPlannedDismissal(@PathVariable Long teacherId,
                                                                              @RequestBody TeacherPlannedDismissRequest request,
                                                                              HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        return ResponseEntity.ok(teacherDirectoryService.markPlannedDismissal(
                teacherId,
                request.getPlannedDismissalDate(),
                request.getComment(),
                user.getFullName()
        ));
    }

    @PatchMapping("/{teacherId}/cancel-plan-dismiss")
    public ResponseEntity<TeacherDirectoryEntry> cancelTeacherPlannedDismissal(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherDirectoryService.cancelPlannedDismissal(teacherId));
    }


    @PatchMapping("/{teacherId}/restore")
    public ResponseEntity<TeacherDirectoryEntry> restore(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherDirectoryService.restore(teacherId));
    }

    @PatchMapping("/{teacherId}/archive")
    public ResponseEntity<TeacherDirectoryEntry> archive(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherDirectoryService.archive(teacherId));
    }

    @PatchMapping("/{teacherId}/unarchive")
    public ResponseEntity<TeacherDirectoryEntry> unarchive(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherDirectoryService.unarchive(teacherId));
    }

    @DeleteMapping("/{teacherId}")
    public ResponseEntity<Void> deleteById(@PathVariable Long teacherId) {
        teacherDirectoryService.deleteById(teacherId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<PersonnelRow>> findAll(
            @RequestParam(required = false) String academicYear) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(personnelService.personnel(year));
    }

    @GetMapping("/vacancies")
    public ResponseEntity<List<TeacherDirectoryEntry>> vacancies() {
        return ResponseEntity.ok(teacherDirectoryService.findAll().stream()
                .filter(row -> row.getFioTeacher() != null
                        && row.getFioTeacher().trim().toLowerCase(java.util.Locale.ROOT).startsWith("вакансия"))
                .toList());
    }

    @GetMapping("/positions")
    public ResponseEntity<List<String>> positions() {
        return ResponseEntity.ok(teacherDirectoryService.findAll().stream()
                .map(TeacherDirectoryEntry::getPrimaryPosition)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptEmployeeResult> accept(@RequestBody AcceptEmployeeRequest request,
                                                        HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        AcceptEmployeeResult result = personnelService.acceptEmployee(request, user.getUsername());
        log(httpServletRequest, "ACCEPT", "TEACHER",
                "Принят сотрудник teacher_id=" + result.teacherId()
                        + (result.linkedToVacancy() ? ", связан с вакансией «" + result.previousName() + "»" : ""));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/auto-assign-buildings")
    public ResponseEntity<AutoBuildingResult> autoAssignBuildings(
            @RequestParam(required = false) String academicYear,
            HttpServletRequest request) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        AutoBuildingResult result = personnelService.autoAssignBuildings(year);
        log(request, "AUTO_ASSIGN", "TEACHER_BUILDING",
                "Учебный год " + year + ": назначено " + result.assigned()
                        + ", без изменений " + result.unchanged()
                        + ", одинаковая нагрузка на площадках " + result.skippedTies());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{teacherId}/name-cases")
    public Object nameCases(@PathVariable Long teacherId) {
        return personnelService.nameCases(teacherId);
    }

    @GetMapping("/{teacherId}/probe-events")
    public Object probeEvents(@PathVariable Long teacherId) {
        return probeOrderService.teacherHistory(teacherId);
    }

    @GetMapping("/name-cases/derive")
    public Object deriveNameCases(@RequestParam String fio) {
        return personnelService.deriveNameCases(fio);
    }

    @GetMapping("/{teacherId}/data-sheet")
    public ResponseEntity<byte[]> dataSheet(@PathVariable Long teacherId,
                                            HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        if (!user.canViewTab(AppTab.HR_PERSONAL_DATA)) {
            throw new org.school.personalLoad.auth.AuthExceptions.ForbiddenException(
                    "Нет прав на просмотр персональных данных");
        }
        TeacherDirectoryEntry teacher = teacherDirectoryService.findAll().stream()
                .filter(row -> java.util.Objects.equals(row.getId(), teacherId)).findFirst().orElseThrow();
        String filename = "Лист проверки данных " + teacher.getFioTeacher() + ".docx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(personnelService.employeeDataSheet(teacherId));
    }

    private void log(HttpServletRequest request, String action, String entity, String details) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        UserActionLog item = new UserActionLog();
        item.setUserId(user.getId());
        item.setUsername(user.getUsername());
        item.setFullName(user.getFullName());
        item.setRole(user.getRole().name());
        item.setActionType(action);
        item.setEntityType(entity);
        item.setDetails(details);
        item.setIp(request.getRemoteAddr());
        item.setUserAgent(request.getHeader("User-Agent"));
        item.setStatusCode(200);
        item.setSuccess(true);
        item.setCreatedAt(Instant.now());
        audit.save(item);
    }

    @GetMapping("/archive")
    public ResponseEntity<List<TeacherDirectoryEntry>> findArchived() {
        return ResponseEntity.ok(teacherDirectoryService.findArchived());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        teacherDirectoryService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
