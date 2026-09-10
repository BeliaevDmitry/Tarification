package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
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
        SessionUser user = AuthSessionUtils.requiredUser(request);
        AdmissionDtos.Access access = requireView(user);
        return ResponseEntity.ok(withAccess(
                service.overview(academicYearService.resolveRequestedOrDefault(academicYear)), access));
    }

    @GetMapping("/access")
    public ResponseEntity<AdmissionDtos.Access> access(HttpServletRequest request) {
        return ResponseEntity.ok(service.access(AuthSessionUtils.requiredUser(request)));
    }

    @GetMapping("/roles")
    public ResponseEntity<AdmissionDtos.RolesOverview> roles(HttpServletRequest request) {
        AdmissionDtos.Access access = service.access(AuthSessionUtils.requiredUser(request));
        if (!access.isCanManageRoles()) {
            throw new AuthExceptions.ForbiddenException("Нет прав на просмотр ролей приёма");
        }
        return ResponseEntity.ok(service.rolesOverview(access.isCanEditRoles()));
    }

    @PutMapping("/roles")
    public ResponseEntity<AdmissionDtos.RolesOverview> updateRoles(@RequestBody AdmissionDtos.RolesUpdateRequest body,
                                                                  HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        AdmissionDtos.Access access = service.access(user);
        if (!access.isCanEditRoles()) {
            throw new AuthExceptions.ForbiddenException("Нет прав на изменение ролей приёма");
        }
        return ResponseEntity.ok(service.updateRoles(body, user.getFullName(), true));
    }

    @PostMapping
    public ResponseEntity<AdmissionDtos.Overview> create(@RequestParam(required = false) String academicYear,
                                                        @RequestBody AdmissionDtos.SaveRequest body,
                                                        HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        AdmissionDtos.Access access = requireEdit(user);
        return ResponseEntity.ok(withAccess(service.create(
                academicYearService.resolveRequestedOrDefault(academicYear), body, user.getFullName()), access));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdmissionDtos.Overview> update(@RequestParam(required = false) String academicYear,
                                                        @PathVariable Long id,
                                                        @RequestBody AdmissionDtos.SaveRequest body,
                                                        HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        AdmissionDtos.Access access = requireEdit(user);
        return ResponseEntity.ok(withAccess(service.update(
                academicYearService.resolveRequestedOrDefault(academicYear), id, body, user.getFullName()), access));
    }

    @PatchMapping("/{id}/action")
    public ResponseEntity<AdmissionDtos.Overview> action(@RequestParam(required = false) String academicYear,
                                                        @PathVariable Long id,
                                                        @RequestBody AdmissionDtos.ActionRequest body,
                                                        HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        AdmissionDtos.Access access = service.access(user);
        String action = body == null || body.getAction() == null ? "" : body.getAction().trim().toUpperCase();
        if ("AGREE".equals(action) || "REFUSE".equals(action)) {
            if (!access.isCanDecide()) throw new AuthExceptions.ForbiddenException("Нет права согласовывать или отклонять приём");
        } else if (!access.isCanEdit()) {
            throw new AuthExceptions.ForbiddenException("Нет прав на изменение приёма детей");
        }
        return ResponseEntity.ok(withAccess(service.action(
                academicYearService.resolveRequestedOrDefault(academicYear), id, body, user.getFullName()), access));
    }

    private AdmissionDtos.Access requireView(SessionUser user) {
        AdmissionDtos.Access access = service.access(user);
        if (!access.isCanView()) {
            throw new AuthExceptions.ForbiddenException("Нет прав на просмотр приёма детей");
        }
        return access;
    }

    private AdmissionDtos.Access requireEdit(SessionUser user) {
        AdmissionDtos.Access access = service.access(user);
        if (!access.isCanEdit()) {
            throw new AuthExceptions.ForbiddenException("Нет прав на изменение приёма детей");
        }
        return access;
    }

    private AdmissionDtos.Overview withAccess(AdmissionDtos.Overview overview, AdmissionDtos.Access access) {
        overview.setAccess(access);
        return overview;
    }
}
