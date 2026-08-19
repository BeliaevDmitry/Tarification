package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.OvzDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.OvzDossierService;
import org.school.personalLoad.service.PpkProtocolService;
import org.school.personalLoad.service.PpkProtocolSettingsService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ovz")
@RequiredArgsConstructor
public class OvzController {
    private final AcademicYearService academicYearService;
    private final OvzDossierService dossierService;
    private final PpkProtocolService ppkService;
    private final PpkProtocolSettingsService ppkSettingsService;

    @GetMapping("/registry")
    public List<OvzDtos.DossierSummary> registry(@RequestParam(required = false) String academicYear,
                                                  @RequestParam(required = false) LocalDate asOfDate) {
        return dossierService.registry(year(academicYear), asOfDate);
    }

    @GetMapping("/registry/export")
    public ResponseEntity<byte[]> exportRegistry(@RequestParam(required = false) String academicYear) {
        String year = year(academicYear);
        return file(dossierService.exportRegistry(year), "Реестр_ОВЗ_" + year + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/registry/export")
    public ResponseEntity<byte[]> exportRegistryView(@RequestParam(required = false) String academicYear,
                                                      @RequestBody List<Long> studentIds) {
        String year = year(academicYear);
        return file(dossierService.exportRegistry(year, studentIds), "Реестр_ОВЗ_" + year + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/dossiers/{studentId}")
    public OvzDtos.DossierDetail detail(@RequestParam(required = false) String academicYear, @PathVariable Long studentId) {
        return dossierService.detail(year(academicYear), studentId);
    }

    @DeleteMapping("/dossiers/{studentId}")
    public ResponseEntity<Void> delete(@RequestParam(required = false) String academicYear, @PathVariable Long studentId) {
        dossierService.deleteDossier(year(academicYear), studentId); return ResponseEntity.noContent().build();
    }

    @PutMapping("/dossiers/{studentId}/stages")
    public OvzDtos.StageView updateStage(@RequestParam(required = false) String academicYear, @PathVariable Long studentId,
                                         @RequestBody OvzDtos.StageUpdateRequest request) {
        return dossierService.updateStage(year(academicYear), studentId, request);
    }

    @PutMapping("/dossiers/{studentId}/application")
    public List<OvzDtos.ApplicationChoiceView> updateApplication(
            @RequestParam(required = false) String academicYear, @PathVariable Long studentId,
            @RequestBody List<OvzDtos.ApplicationChoiceRequest> requests) {
        return dossierService.saveApplicationChoices(year(academicYear), studentId, requests);
    }

    @PostMapping("/dossiers/{studentId}/consent")
    public ResponseEntity<byte[]> consent(@RequestParam(required = false) String academicYear, @PathVariable Long studentId) {
        OvzDossierService.GeneratedDocument generated = dossierService.consentTemplate(year(academicYear), studentId);
        return file(generated.content(), generated.fileName(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @GetMapping("/ppk")
    public List<OvzDtos.PpkProtocolView> ppk(@RequestParam(required = false) String academicYear) {
        return ppkService.findAll(year(academicYear));
    }

    @GetMapping("/ppk/defaults")
    public OvzDtos.PpkProtocolDefaults ppkDefaults(@RequestParam(required = false) String academicYear,
                                                    @RequestParam(required = false) Long studentId) {
        return ppkService.defaults(year(academicYear), studentId);
    }

    @GetMapping("/ppk/settings")
    public OvzDtos.PpkProtocolSettingsView ppkSettings() {
        return ppkSettingsService.get();
    }

    @GetMapping("/ppk/settings/employees")
    public List<OvzDtos.PpkEmployeeOption> ppkSettingsEmployees() {
        return ppkSettingsService.employees();
    }

    @PutMapping("/ppk/settings")
    public OvzDtos.PpkProtocolSettingsView updatePpkSettings(
            @RequestBody OvzDtos.PpkProtocolSettingsRequest request) {
        return ppkSettingsService.update(request);
    }

    @PutMapping("/ppk")
    public OvzDtos.PpkProtocolView savePpk(@RequestParam(required = false) String academicYear,
                                           @RequestBody OvzDtos.PpkProtocolSaveRequest request) {
        return ppkService.save(year(academicYear), request);
    }

    @DeleteMapping("/ppk/{id}")
    public ResponseEntity<Void> deletePpk(@RequestParam(required = false) String academicYear, @PathVariable Long id) {
        ppkService.delete(year(academicYear), id); return ResponseEntity.noContent().build();
    }

    @GetMapping("/ppk/{id}/document")
    public ResponseEntity<byte[]> ppkDocument(@RequestParam(required = false) String academicYear, @PathVariable Long id) {
        PpkProtocolService.GeneratedDocument generated = ppkService.generate(year(academicYear), id);
        return file(generated.content(), generated.fileName(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private String year(String requested) { return academicYearService.resolveRequestedOrDefault(requested); }
    private ResponseEntity<byte[]> file(byte[] content, String name, String contentType) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType)).body(content);
    }
}
