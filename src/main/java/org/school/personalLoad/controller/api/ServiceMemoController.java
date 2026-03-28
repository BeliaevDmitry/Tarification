package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ServiceMemoDtos;
import org.school.personalLoad.model.ServiceMemo;
import org.school.personalLoad.service.ServiceMemoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/service-memos")
@RequiredArgsConstructor
public class ServiceMemoController {

    private final ServiceMemoService serviceMemoService;

    @GetMapping("/pending")
    public ResponseEntity<List<ServiceMemoDtos.PendingTeacher>> pending() {
        return ResponseEntity.ok(serviceMemoService.findPendingTeachers());
    }

    @GetMapping("/processed")
    public ResponseEntity<List<ServiceMemoDtos.ProcessedMemo>> processed() {
        return ResponseEntity.ok(serviceMemoService.findProcessed());
    }

    @GetMapping("/archived")
    public ResponseEntity<List<ServiceMemoDtos.ProcessedMemo>> archived() {
        return ResponseEntity.ok(serviceMemoService.findArchived());
    }

    @PostMapping("/generate")
    public ResponseEntity<List<ServiceMemoDtos.ProcessedMemo>> generate(@RequestBody ServiceMemoDtos.GenerateRequest request,
                                                                        HttpServletRequest httpServletRequest) {
        SessionUser user = AuthSessionUtils.requiredUser(httpServletRequest);
        return ResponseEntity.ok(serviceMemoService.generateForTeachers(request.getFioTeachers(), user.getFullName()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        ServiceMemo memo = serviceMemoService.getById(id);
        byte[] payload = memo.getCorrectedDocument() != null ? memo.getCorrectedDocument() : memo.getGeneratedDocument();
        String filename = memo.getCorrectedDocument() != null && memo.getCorrectedFilename() != null
                ? memo.getCorrectedFilename()
                : memo.getGeneratedFilename();
        String encodedFileName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(payload);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ServiceMemoDtos.ProcessedMemo> archive(@PathVariable Long id) {
        ServiceMemo memo = serviceMemoService.archive(id);
        return ResponseEntity.ok(ServiceMemoDtos.ProcessedMemo.builder()
                .id(memo.getId())
                .fioTeacher(memo.getFioTeacher())
                .status(memo.getStatus().name())
                .createdBy(memo.getCreatedBy())
                .startDate(memo.getChangeStartDate())
                .createdAt(memo.getCreatedAt())
                .generatedFilename(memo.getGeneratedFilename())
                .correctedFilename(memo.getCorrectedFilename())
                .build());
    }

    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceMemoDtos.ProcessedMemo> upload(@PathVariable Long id,
                                                                 @RequestPart("file") MultipartFile file) throws IOException {
        ServiceMemo memo = serviceMemoService.uploadCorrected(id, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.ok(ServiceMemoDtos.ProcessedMemo.builder()
                .id(memo.getId())
                .fioTeacher(memo.getFioTeacher())
                .status(memo.getStatus().name())
                .createdBy(memo.getCreatedBy())
                .startDate(memo.getChangeStartDate())
                .createdAt(memo.getCreatedAt())
                .generatedFilename(memo.getGeneratedFilename())
                .correctedFilename(memo.getCorrectedFilename())
                .build());
    }
}
