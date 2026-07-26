package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.PedagogicalCouncilDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.PedagogicalCouncilService;
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
@RequestMapping("/api/pedagogical-councils")
@RequiredArgsConstructor
public class PedagogicalCouncilController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final PedagogicalCouncilService service;
    private final AcademicYearService academicYearService;

    @GetMapping
    public List<PedagogicalCouncilDtos.ProtocolSummary> list(@RequestParam(required = false) String academicYear) {
        return service.list(academicYearService.resolveRequestedOrDefault(academicYear));
    }

    @GetMapping("/staff")
    public List<PedagogicalCouncilDtos.StaffView> staff() {
        return service.staff();
    }

    @GetMapping("/certifiers")
    public List<PedagogicalCouncilDtos.CertifierView> certifiers() {
        return service.certifiers();
    }

    @GetMapping("/{id}")
    public PedagogicalCouncilDtos.ProtocolDetails get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public PedagogicalCouncilDtos.ProtocolDetails create(@RequestBody PedagogicalCouncilDtos.CreateProtocolRequest request,
                                                         HttpServletRequest httpRequest) {
        return service.create(request, AuthSessionUtils.requiredUser(httpRequest));
    }

    @PutMapping("/{id}")
    public PedagogicalCouncilDtos.ProtocolDetails update(@PathVariable Long id,
                                                         @RequestBody PedagogicalCouncilDtos.UpdateProtocolRequest request,
                                                         HttpServletRequest httpRequest) {
        return service.update(id, request, AuthSessionUtils.requiredUser(httpRequest));
    }

    @PostMapping(value = "/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PedagogicalCouncilDtos.ProtocolDetails uploadArchive(@RequestParam String academicYear,
                                                                @RequestParam String protocolNumber,
                                                                @RequestParam LocalDate meetingDate,
                                                                @RequestPart("file") MultipartFile file,
                                                                HttpServletRequest request) throws IOException {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureImport(user);
        return service.uploadArchive(academicYear, protocolNumber, meetingDate, file, user);
    }

    @PostMapping(value = "/{protocolId}/items/{itemId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PedagogicalCouncilDtos.AttachmentView addAttachment(@PathVariable Long protocolId,
                                                               @PathVariable Long itemId,
                                                               @RequestPart("file") MultipartFile file,
                                                               HttpServletRequest request) throws IOException {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureImport(user);
        return service.addAttachment(protocolId, itemId, file, user);
    }

    @DeleteMapping("/{protocolId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long protocolId,
                                                 @PathVariable Long attachmentId) {
        service.deleteAttachment(protocolId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{protocolId}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long protocolId,
                                                     @PathVariable Long attachmentId,
                                                     HttpServletRequest request) {
        ensureExport(AuthSessionUtils.requiredUser(request));
        PedagogicalCouncilDtos.FilePayload attachment = service.getAttachment(protocolId, attachmentId);
        return wordResponse(attachment.filename(), attachment.content());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, HttpServletRequest request) {
        ensureExport(AuthSessionUtils.requiredUser(request));
        PedagogicalCouncilDtos.FilePayload file = service.buildFullProtocol(id);
        return wordResponse(file.filename(), file.content());
    }

    @PostMapping("/{id}/extract")
    public ResponseEntity<byte[]> extract(@PathVariable Long id,
                                          @RequestBody PedagogicalCouncilDtos.ExtractRequest extractRequest,
                                          HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureExport(user);
        PedagogicalCouncilDtos.FilePayload file = service.buildExtract(id, extractRequest, user);
        return wordResponse(file.filename(), file.content());
    }

    private void ensureImport(SessionUser user) {
        if (!user.canImportTab(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)) {
            throw new AuthExceptions.ForbiddenException("Нет права загружать Word-файлы педагогических советов");
        }
    }

    private void ensureExport(SessionUser user) {
        if (!user.canExportTab(AppTab.DOCUMENTS_PEDAGOGICAL_COUNCILS)) {
            throw new AuthExceptions.ForbiddenException("Нет права скачивать протоколы и выписки");
        }
    }

    private ResponseEntity<byte[]> wordResponse(String filename, byte[] content) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header("X-File-Name", encoded)
                .body(content);
    }
}
