package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.PaLevel;
import org.school.personalLoad.pa.model.PaScopeType;
import org.school.personalLoad.pa.model.PaWorkType;
import org.school.personalLoad.pa.service.PaWorkMaterialService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/pa/materials")
@RequiredArgsConstructor
public class PaWorkMaterialController {

    private final PaWorkMaterialService materialService;
    private final AcademicYearService academicYearService;

    @GetMapping
    public List<PaDtos.WorkMaterialRow> materials(@RequestParam(required = false) String academicYear) {
        return materialService.materials(resolveYear(academicYear));
    }

    @GetMapping("/references")
    public List<PaDtos.WorkMaterialReferenceRow> references(@RequestParam(required = false) String academicYear) {
        return materialService.references(resolveYear(academicYear));
    }

    @PostMapping
    public PaDtos.WorkMaterialUploadResponse upload(@RequestParam(required = false) String academicYear,
                                                    @RequestParam String subjectName,
                                                    @RequestParam PaScopeType scopeType,
                                                    @RequestParam String scopeValue,
                                                    @RequestParam PaLevel level,
                                                    @RequestParam PaWorkType workType,
                                                    @RequestParam(required = false) List<MultipartFile> textFiles,
                                                    @RequestParam(required = false) List<MultipartFile> answerFiles,
                                                    HttpSession session) throws Exception {
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        String username = user == null ? "unknown" : user.getUsername();
        String fullName = user == null ? username : user.getFullName();
        return materialService.upload(resolveYear(academicYear), subjectName, scopeType, scopeValue, level,
                workType, textFiles, answerFiles, username, fullName);
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) throws Exception {
        return attachment(materialService.loadAttachment(fileId), materialService.attachmentFileName(fileId),
                MediaType.APPLICATION_OCTET_STREAM);
    }

    @GetMapping("/{materialId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long materialId,
                                           @RequestParam PaWorkMaterialService.FileKind kind) throws Exception {
        return attachment(materialService.loadFile(materialId, kind), materialService.fileName(materialId, kind),
                MediaType.APPLICATION_OCTET_STREAM);
    }

    private String resolveYear(String academicYear) {
        return academicYearService.resolveRequestedOrDefault(academicYear);
    }

    static ResponseEntity<byte[]> attachment(byte[] body, String fileName, MediaType mediaType) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(mediaType)
                .body(body);
    }
}
