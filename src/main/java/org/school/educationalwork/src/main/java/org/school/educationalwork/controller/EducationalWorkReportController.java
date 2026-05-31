package org.school.educationalwork.src.main.java.org.school.educationalwork.controller;

import org.school.educationalwork.dto.EducationalWorkDtos;
import org.school.educationalwork.service.EducationalWorkReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/educational-work")
public class EducationalWorkReportController {
    private final EducationalWorkReportService service;

    public EducationalWorkReportController(EducationalWorkReportService service) {
        this.service = service;
    }

    @PostMapping(value = "/reports/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EducationalWorkDtos.UploadResponse validate(@RequestParam("file") MultipartFile file) throws IOException {
        return service.validate(file);
    }

    @PostMapping(value = "/reports/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EducationalWorkDtos.UploadResponse submit(@RequestParam("file") MultipartFile file) throws IOException {
        return service.submit(file);
    }

    @PostMapping("/summary")
    public EducationalWorkDtos.SchoolSummary summary(@RequestParam String academicYear,
                                                      @RequestBody List<EducationalWorkDtos.ExpectedClass> expectedClasses) {
        return service.summary(academicYear, expectedClasses);
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
