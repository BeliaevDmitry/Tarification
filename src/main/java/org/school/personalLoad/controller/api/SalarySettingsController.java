package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.dto.SalarySettingsRequest;
import org.school.personalLoad.model.SalarySettings;
import org.school.personalLoad.repository.SalarySettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/salary-settings")
@RequiredArgsConstructor
public class SalarySettingsController {

    private final SalarySettingsRepository salarySettingsRepository;

    @GetMapping
    public ResponseEntity<SalarySettings> get(HttpServletRequest httpServletRequest) {
        requireSettingsAccess(httpServletRequest, false);
        return ResponseEntity.ok(currentSettings());
    }

    @PutMapping
    public ResponseEntity<SalarySettings> update(@RequestBody SalarySettingsRequest request,
                                                 HttpServletRequest httpServletRequest) {
        requireSettingsAccess(httpServletRequest, true);
        if (request == null || request.getStudentHourRate() == null || request.getStudentHourRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Человеко-час должен быть больше 0");
        }
        SalarySettings settings = currentSettings();
        settings.setStudentHourRate(request.getStudentHourRate());
        return ResponseEntity.ok(salarySettingsRepository.save(settings));
    }

    private SalarySettings currentSettings() {
        return salarySettingsRepository.findById(SalarySettings.DEFAULT_ID).orElseGet(() -> {
            SalarySettings settings = new SalarySettings();
            settings.setId(SalarySettings.DEFAULT_ID);
            settings.setStudentHourRate(SalarySettings.DEFAULT_STUDENT_HOUR_RATE);
            return salarySettingsRepository.save(settings);
        });
    }

    private void requireSettingsAccess(HttpServletRequest request, boolean edit) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        boolean allowed = edit
                ? user.canEditTab(AppTab.TEACHERS_SETTINGS)
                : user.canViewTab(AppTab.TEACHERS_SETTINGS) || user.canViewSalary();
        if (!allowed) {
            throw new ForbiddenException("Нет прав на настройки кадров");
        }
    }
}
