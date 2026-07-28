package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.LoadInRateDtos.*;
import org.school.personalLoad.model.UserActionLog;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.LoadInRateService;
import org.school.personalLoad.service.UserActionLogService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/manual-load/in-rate")
@RequiredArgsConstructor
public class LoadInRateController {
    private final LoadInRateService service;
    private final AcademicYearService academicYearService;
    private final UserActionLogService audit;

    @GetMapping
    public Overview overview(@RequestParam(required = false) String academicYear,
                             HttpServletRequest request) {
        requireView(request);
        return service.overview(academicYearService.resolveRequestedOrDefault(academicYear));
    }

    @PutMapping
    public SaveResult save(@RequestParam(required = false) String academicYear,
                           @RequestBody AllocationBatchRequest body,
                           HttpServletRequest request) {
        SessionUser user = requireEdit(request);
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        SaveResult result = service.save(year, body, user.getUsername());
        log(request, "UPDATE", "LOAD_IN_RATE_ALLOCATION",
                "Учебный год " + year + ": обновлено строк " + result.updated()
                        + ", осталось нераспределённых " + result.unresolved());
        return result;
    }

    @GetMapping("/rules")
    public List<RuleView> rules(HttpServletRequest request) {
        requireView(request);
        return service.rules();
    }

    @PostMapping("/rules")
    public RuleView createRule(@RequestBody RuleRequest body, HttpServletRequest request) {
        requireEdit(request);
        RuleView saved = service.saveRule(null, body);
        log(request, "CREATE", "LOAD_IN_RATE_RULE", "Создано правило ID " + saved.id() + ": " + saved.name());
        return saved;
    }

    @PutMapping("/rules/{id}")
    public RuleView updateRule(@PathVariable Long id, @RequestBody RuleRequest body,
                               HttpServletRequest request) {
        requireEdit(request);
        RuleView saved = service.saveRule(id, body);
        log(request, "UPDATE", "LOAD_IN_RATE_RULE", "Изменено правило ID " + id + ": " + saved.name());
        return saved;
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id, HttpServletRequest request) {
        requireEdit(request);
        service.deleteRule(id);
        log(request, "DELETE", "LOAD_IN_RATE_RULE", "Удалено правило ID " + id);
    }

    private SessionUser requireView(HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        if (!user.canViewSalary()) throw new ForbiddenException("Нет прав на просмотр расчёта зарплаты");
        return user;
    }

    private SessionUser requireEdit(HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        if (!user.canEditSalary()) throw new ForbiddenException("Нет прав на распределение часов внутри ставки");
        return user;
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
}
