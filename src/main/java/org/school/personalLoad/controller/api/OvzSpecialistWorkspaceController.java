package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.dto.contingent.OvzSpecialistWorkspaceDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.OvzSpecialistWorkspaceService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ovz/specialist-workspace")
@RequiredArgsConstructor
public class OvzSpecialistWorkspaceController {

    private final AcademicYearService academicYearService;
    private final OvzSpecialistWorkspaceService service;

    @GetMapping
    public OvzSpecialistWorkspaceDtos.Overview overview(
            @RequestParam(required = false) String academicYear, HttpServletRequest request) {
        return service.overview(year(academicYear), AuthSessionUtils.requiredUser(request));
    }

    @GetMapping("/students/{studentId}")
    public OvzSpecialistWorkspaceDtos.ChildDetail child(
            @RequestParam(required = false) String academicYear,
            @PathVariable Long studentId,
            HttpServletRequest request) {
        return service.child(year(academicYear), studentId, AuthSessionUtils.requiredUser(request));
    }

    @PutMapping("/students/{studentId}/entries")
    public OvzSpecialistWorkspaceDtos.SupportEntry saveEntry(
            @RequestParam(required = false) String academicYear,
            @PathVariable Long studentId,
            @RequestBody OvzSpecialistWorkspaceDtos.SupportEntryRequest body,
            HttpServletRequest request) {
        return service.saveEntry(year(academicYear), studentId, body, AuthSessionUtils.requiredUser(request));
    }

    @GetMapping("/settings")
    public OvzSpecialistWorkspaceDtos.SettingsView settings(HttpServletRequest request) {
        return service.settings(AuthSessionUtils.requiredUser(request));
    }

    @PutMapping("/settings")
    public OvzSpecialistWorkspaceDtos.SettingsView updateSettings(
            @RequestBody OvzSpecialistWorkspaceDtos.SettingsRequest body,
            HttpServletRequest request) {
        return service.updateSettings(body, AuthSessionUtils.requiredUser(request));
    }

    private String year(String requested) {
        return academicYearService.resolveRequestedOrDefault(requested);
    }
}
