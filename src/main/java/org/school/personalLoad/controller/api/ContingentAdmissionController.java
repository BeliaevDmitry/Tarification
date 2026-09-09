package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.contingent.AdmissionDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.AdmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/contingent/admissions")
@RequiredArgsConstructor
public class ContingentAdmissionController {

    private final AdmissionService service;
    private final AcademicYearService academicYearService;

    @GetMapping
    public ResponseEntity<AdmissionDtos.Overview> overview(@RequestParam(required = false) String academicYear,
                                                          HttpServletRequest request) {
        requireView(AuthSessionUtils.requiredUser(request));
        return ResponseEntity.ok(service.overview(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @PostMapping
    public ResponseEntity<AdmissionDtos.Overview> create(@RequestParam(required = false) String academicYear,
                                                        @RequestBody AdmissionDtos.SaveRequest body,
                                                        HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        requireEdit(user);
        return ResponseEntity.ok(service.create(
                academicYearService.resolveRequestedOrDefault(academicYear), body, user.getFullName()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdmissionDtos.Overview> update(@RequestParam(required = false) String academicYear,
                                                        @PathVariable Long id,
                                                        @RequestBody AdmissionDtos.SaveRequest body,
                                                        HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        requireEdit(user);
        return ResponseEntity.ok(service.update(
                academicYearService.resolveRequestedOrDefault(academicYear), id, body, user.getFullName()
        ));
    }

    @PatchMapping("/{id}/action")
    public ResponseEntity<AdmissionDtos.Overview> action(@RequestParam(required = false) String academicYear,
                                                        @PathVariable Long id,
                                                        @RequestBody AdmissionDtos.ActionRequest body,
                                                        HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        requireEdit(user);
        return ResponseEntity.ok(service.action(
                academicYearService.resolveRequestedOrDefault(academicYear), id, body, user.getFullName()
        ));
    }

    private void requireView(SessionUser user) {
        if (!user.canViewTab(AppTab.CONTINGENT_STATS) && !user.canViewTab(AppTab.CONTINGENT_IMPORT)) {
            throw new AuthExceptions.ForbiddenException("Нет прав на просмотр приёма детей");
        }
    }

    private void requireEdit(SessionUser user) {
        if (!user.canEditTab(AppTab.CONTINGENT_STATS) && !user.canEditTab(AppTab.CONTINGENT_IMPORT)) {
            throw new AuthExceptions.ForbiddenException("Нет прав на изменение приёма детей");
        }
    }
}
