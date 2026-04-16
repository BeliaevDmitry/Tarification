package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.service.SchoolBuildingService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class SchoolBuildingController {

    private final SchoolBuildingService schoolBuildingService;

    @PostMapping
    public ResponseEntity<SchoolBuilding> upsert(@RequestBody SchoolBuildingRequest request) {
        return ResponseEntity.ok(schoolBuildingService.upsert(request));
    }

    @GetMapping
    public ResponseEntity<List<SchoolBuilding>> findAll() {
        return ResponseEntity.ok(schoolBuildingService.findAll());
    }


    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] body = schoolBuildingService.exportToExcel();
        String fileName = "Корпуса.xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping("/import")
    public ResponseEntity<java.util.Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(schoolBuildingService.importFromExcel(file));
    }
    @DeleteMapping("/one")
    public ResponseEntity<Void> deleteOne(@RequestParam String code) {
        schoolBuildingService.deleteByCode(code);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        schoolBuildingService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
