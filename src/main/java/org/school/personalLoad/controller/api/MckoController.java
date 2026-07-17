package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.MckoDtos;
import org.school.personalLoad.model.MckoCertificate;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.MckoService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcko")
@RequiredArgsConstructor
public class MckoController {
    private final MckoService mckoService;
    private final AcademicYearService academicYearService;

    @GetMapping("/certificates")
    public List<MckoDtos.CertificateRow> certificates(@RequestParam(required = false) String academicYear,
                                                      @RequestParam(defaultValue = "all") String mode) {
        return mckoService.certificates(academicYearService.resolveRequestedOrDefault(academicYear), mode);
    }

    @PostMapping("/certificates/import")
    public MckoDtos.ImportResult importCertificates(@RequestParam("file") MultipartFile file) {
        return mckoService.importCertificates(file);
    }

    @PostMapping(value = "/certificates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MckoDtos.CertificateRow createCertificate(@RequestParam Long teacherId,
                                                     @RequestParam String mckoSubject,
                                                     @RequestParam String examType,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate diagnosticDate,
                                                     @RequestParam String level,
                                                     @RequestParam(defaultValue = "false") boolean published,
                                                     @RequestParam(required = false) String comment,
                                                     @RequestPart(required = false) MultipartFile scan) throws Exception {
        return mckoService.createManualCertificate(teacherId, mckoSubject, examType, diagnosticDate, level, published, comment, scan);
    }

    @PutMapping(value = "/certificates/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MckoDtos.CertificateRow updateCertificate(@PathVariable Long id,
                                                     @RequestParam Long teacherId,
                                                     @RequestParam String mckoSubject,
                                                     @RequestParam String examType,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate diagnosticDate,
                                                     @RequestParam String level,
                                                     @RequestParam(defaultValue = "false") boolean published,
                                                     @RequestParam(required = false) String comment,
                                                     @RequestPart(required = false) MultipartFile scan,
                                                     @RequestParam(defaultValue = "false") boolean removeScan) throws Exception {
        return mckoService.updateCertificate(id, teacherId, mckoSubject, examType, diagnosticDate, level,
                published, comment, scan, removeScan);
    }

    @DeleteMapping("/certificates/{id}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable Long id) {
        mckoService.deleteCertificate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/certificates/{id}/scan")
    public ResponseEntity<byte[]> scan(@PathVariable Long id) {
        MckoCertificate cert = mckoService.certificate(id);
        if (cert.getScanContent() == null) return ResponseEntity.notFound().build();
        String fileName = URLEncoder.encode(cert.getScanFileName() == null ? "mcko-scan" : cert.getScanFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(cert.getScanContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : cert.getScanContentType()))
                .body(cert.getScanContent());
    }

    @GetMapping("/certificates/export")
    public ResponseEntity<Resource> export(@RequestParam(required = false) String academicYear,
                                           @RequestParam(defaultValue = "all") String mode) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        Resource resource = mckoService.exportCertificates(effectiveYear, mode);
        String fileName = URLEncoder.encode("МЦКО " + effectiveYear + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @GetMapping("/subjects")
    public List<MckoDtos.SubjectMappingRow> mappings() {
        return mckoService.mappings();
    }

    @PostMapping("/subjects")
    public MckoDtos.SubjectMappingRow createMapping(@RequestBody Map<String, Object> body) {
        String mckoSubject = String.valueOf(body.getOrDefault("mckoSubject", ""));
        if (Boolean.parseBoolean(String.valueOf(body.getOrDefault("ignored", "false")))) {
            return mckoService.ignoreSubject(mckoSubject);
        }
        Long subjectId = Long.valueOf(String.valueOf(body.get("subjectId")));
        String gradeBand = String.valueOf(body.getOrDefault("gradeBand", "ALL"));
        return mckoService.createMapping(mckoSubject, subjectId, gradeBand);
    }

    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
        mckoService.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/eligibility")
    public List<MckoDtos.EligibilityRow> eligibility(@RequestParam(required = false) String academicYear) {
        return mckoService.eligibility(academicYearService.resolveRequestedOrDefault(academicYear));
    }
}
