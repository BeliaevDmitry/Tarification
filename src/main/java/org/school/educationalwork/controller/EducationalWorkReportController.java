package org.school.educationalwork.controller;

import lombok.RequiredArgsConstructor;
import org.school.educationalwork.dto.EducationalWorkDtos;
import org.school.educationalwork.service.EducationalWorkReportService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/educational-work")
@RequiredArgsConstructor
public class EducationalWorkReportController {
    private final EducationalWorkReportService service;
    private final AcademicYearService academicYearService;

    @PostMapping(value = "/reports/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EducationalWorkDtos.UploadResponse validate(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) String academicYear) throws IOException {
        return service.validate(academicYearService.resolveRequestedOrDefault(academicYear), file);
    }

    @PostMapping(value = "/reports/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EducationalWorkDtos.UploadResponse submit(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(required = false) String academicYear) throws IOException {
        return service.submit(academicYearService.resolveRequestedOrDefault(academicYear), file);
    }

    @GetMapping("/summary")
    public EducationalWorkDtos.SchoolSummary summary(@RequestParam(required = false) String academicYear) {
        return service.summary(academicYearService.resolveRequestedOrDefault(academicYear));
    }

    @GetMapping("/indicators/export")
    public ResponseEntity<byte[]> exportIndicators(@RequestParam(required = false) String academicYear) throws IOException {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = service.exportIndicators(effectiveYear);
        String fileName = "Воспитательная_работа_" + effectiveYear + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/reports/{academicYear}/{schoolClass}/file")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String academicYear,
                                                       @PathVariable String schoolClass) {
        return service.file(academicYear, schoolClass)
                .map(stored -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(stored.fileName(), StandardCharsets.UTF_8).build().toString())
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                        .body(new ByteArrayResource(stored.bytes())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
