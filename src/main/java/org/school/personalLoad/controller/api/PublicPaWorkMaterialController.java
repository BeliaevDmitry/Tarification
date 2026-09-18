package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.service.PaWorkMaterialService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/pa/materials")
@RequiredArgsConstructor
public class PublicPaWorkMaterialController {

    private final PaWorkMaterialService materialService;
    private final AcademicYearService academicYearService;

    @GetMapping
    public List<PaDtos.PublicWorkMaterialRow> materials(@RequestParam(required = false) String academicYear) {
        return materialService.publicMaterials(resolveYear(academicYear));
    }

    @GetMapping("/years")
    public List<String> years() {
        return materialService.academicYears();
    }

    @GetMapping("/download-all")
    public ResponseEntity<byte[]> downloadAll(@RequestParam(required = false) String academicYear) throws Exception {
        String year = resolveYear(academicYear);
        return PaWorkMaterialController.attachment(materialService.downloadAll(year),
                "Тексты_работ_ПА_" + year.replace('/', '-') + ".zip", MediaType.parseMediaType("application/zip"));
    }

    @GetMapping("/{materialId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long materialId,
                                           @RequestParam PaWorkMaterialService.FileKind kind) throws Exception {
        return PaWorkMaterialController.attachment(materialService.loadFile(materialId, kind),
                materialService.fileName(materialId, kind), MediaType.APPLICATION_OCTET_STREAM);
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) throws Exception {
        return PaWorkMaterialController.attachment(materialService.loadAttachment(fileId),
                materialService.attachmentFileName(fileId), MediaType.APPLICATION_OCTET_STREAM);
    }

    private String resolveYear(String academicYear) {
        return academicYearService.resolveRequestedOrDefault(academicYear);
    }
}
