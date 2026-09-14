package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.contingent.StudentClassTransferDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.StudentClassTransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/contingent/transfers")
@RequiredArgsConstructor
public class StudentClassTransferController {

    private final StudentClassTransferService service;
    private final AcademicYearService academicYearService;

    @GetMapping
    public ResponseEntity<StudentClassTransferDtos.Overview> overview(
            @RequestParam(required = false) String academicYear,
            HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        StudentClassTransferDtos.Access access = requireView(user);
        return ResponseEntity.ok(service.overview(
                academicYearService.resolveRequestedOrDefault(academicYear), access));
    }

    @PostMapping
    public ResponseEntity<StudentClassTransferDtos.Overview> create(
            @RequestParam(required = false) String academicYear,
            @RequestBody StudentClassTransferDtos.SaveRequest body,
            HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        StudentClassTransferDtos.Access access = requireEdit(user);
        return ResponseEntity.ok(service.create(
                academicYearService.resolveRequestedOrDefault(academicYear), body, user.getFullName(), access));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentClassTransferDtos.Overview> update(
            @RequestParam(required = false) String academicYear,
            @PathVariable Long id,
            @RequestBody StudentClassTransferDtos.SaveRequest body,
            HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        StudentClassTransferDtos.Access access = requireEdit(user);
        return ResponseEntity.ok(service.update(
                academicYearService.resolveRequestedOrDefault(academicYear), id, body, user.getFullName(), access));
    }

    private StudentClassTransferDtos.Access requireView(SessionUser user) {
        StudentClassTransferDtos.Access access = service.access(user);
        if (!access.isCanView()) {
            throw new AuthExceptions.ForbiddenException("Нет прав на просмотр переводов между классами");
        }
        return access;
    }

    private StudentClassTransferDtos.Access requireEdit(SessionUser user) {
        StudentClassTransferDtos.Access access = requireView(user);
        if (!access.isCanEdit()) {
            throw new AuthExceptions.ForbiddenException("Нет прав на изменение переводов между классами");
        }
        return access;
    }
}
