package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ProbeOrderDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.ProbeOrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/probe-orders")
@RequiredArgsConstructor
public class ProbeOrderController {

    private final ProbeOrderService service;
    private final AcademicYearService academicYearService;

    @GetMapping
    public List<ProbeOrderDtos.OrderView> list(@RequestParam(required = false) String academicYear,
                                               HttpServletRequest request) {
        return service.list(academicYearService.resolveRequestedOrDefault(academicYear), user(request));
    }

    @GetMapping("/references")
    public ProbeOrderDtos.ReferenceData references(@RequestParam(required = false) String academicYear,
                                                    HttpServletRequest request) {
        return service.references(academicYearService.resolveRequestedOrDefault(academicYear), user(request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProbeOrderDtos.ImportResponse importRegistration(@RequestParam(required = false) String academicYear,
                                                             @RequestPart("file") MultipartFile file,
                                                             HttpServletRequest request) throws IOException {
        return service.importRegistration(academicYearService.resolveRequestedOrDefault(academicYear), file, user(request));
    }

    @PatchMapping("/{id}")
    public ProbeOrderDtos.OrderView update(@PathVariable Long id,
                                            @RequestBody ProbeOrderDtos.EditRequest body,
                                            HttpServletRequest request) {
        return service.update(id, body, user(request));
    }

    @PatchMapping("/{id}/companions")
    public ProbeOrderDtos.OrderView companions(@PathVariable Long id,
                                                @RequestBody ProbeOrderDtos.CompanionRequest body,
                                                HttpServletRequest request) {
        return service.assignCompanions(id, body, user(request));
    }

    @PostMapping("/{id}/acknowledge")
    public ProbeOrderDtos.OrderView acknowledge(@PathVariable Long id, HttpServletRequest request) {
        return service.acknowledge(id, user(request));
    }

    @PostMapping("/{id}/generate")
    public ProbeOrderDtos.OrderView generate(@PathVariable Long id,
                                              @RequestBody ProbeOrderDtos.GenerateRequest body,
                                              HttpServletRequest request) {
        return service.generate(id, body, user(request));
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Long id, HttpServletRequest request) {
        return file(service.generatedDocument(id, user(request)));
    }

    @PostMapping("/{id}/release")
    public ProbeOrderDtos.OrderView release(@PathVariable Long id, HttpServletRequest request) {
        return service.release(id, user(request));
    }

    @PostMapping(value = "/{id}/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProbeOrderDtos.OrderView uploadScan(@PathVariable Long id,
                                                @RequestPart("file") MultipartFile file,
                                                HttpServletRequest request) throws IOException {
        return service.uploadScan(id, file, user(request));
    }

    @GetMapping("/{id}/scan")
    public ResponseEntity<byte[]> scan(@PathVariable Long id, HttpServletRequest request) {
        return file(service.signedScan(id, user(request)));
    }

    @GetMapping("/calendar")
    public List<ProbeOrderDtos.CalendarEvent> calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(from, to);
    }

    private SessionUser user(HttpServletRequest request) {
        return AuthSessionUtils.requiredUser(request);
    }

    private ResponseEntity<byte[]> file(ProbeOrderDtos.FilePayload payload) {
        String encoded = URLEncoder.encode(payload.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType type;
        try {
            type = MediaType.parseMediaType(payload.contentType());
        } catch (Exception ignored) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header("X-File-Name", encoded)
                .body(payload.content());
    }
}
