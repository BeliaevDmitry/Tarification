package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.dto.CurriculumPlanEntryResponse;
import org.school.personalLoad.dto.CurriculumImportResult;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.MetaGroup;
import org.school.personalLoad.service.CurriculumImportService;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.repository.MetaGroupRepository;
import org.school.personalLoad.util.CurriculumLoadStandard;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumPlanController {

    private final CurriculumPlanService curriculumPlanService;
    private final CurriculumImportService curriculumImportService;
    private final AcademicYearService academicYearService;
    private final MetaGroupRepository metaGroupRepository;

    @PostMapping
    public ResponseEntity<CurriculumPlanEntry> upsert(@RequestParam(required = false) String academicYear, @RequestBody CurriculumPlanEntryRequest request) {
        request.setAcademicYear(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.ok(curriculumPlanService.upsert(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<CurriculumPlanEntry>> upsertBulk(@RequestParam(required = false) String academicYear, @RequestBody List<CurriculumPlanEntryRequest> requests) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        requests.forEach(req -> req.setAcademicYear(effectiveYear));
        return ResponseEntity.ok(curriculumPlanService.upsertBulk(requests));
    }


    @PostMapping("/import")
    public ResponseEntity<CurriculumImportResult> importCurriculum(@RequestParam("file") MultipartFile file, @RequestParam(required = false) String academicYear) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(curriculumImportService.importFile(file, effectiveYear));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCurriculum(@RequestParam(required = false) String academicYear) throws Exception {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = curriculumImportService.exportEditableWorkbook(effectiveYear);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String fileName = "УП ГБОУ 7 " + effectiveYear + " от " + date + ".xlsx";
        return workbookResponse(body, fileName);
    }

    @GetMapping("/export-parallels")
    public ResponseEntity<byte[]> exportCurriculumByParallels(@RequestParam(required = false) String academicYear) throws Exception {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = curriculumImportService.exportParallelWorkbook(effectiveYear);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String fileName = "УП по параллелям ГБОУ 7 " + effectiveYear + " от " + date + ".xlsx";
        return workbookResponse(body, fileName);
    }

    @GetMapping("/export-department")
    public ResponseEntity<byte[]> exportCurriculumForDepartment(@RequestParam(required = false) String academicYear) throws Exception {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = curriculumImportService.exportDepartmentWorkbook(effectiveYear);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        return workbookResponse(body, "УП для департамента " + effectiveYear + " от " + date + ".xlsx");
    }

    @GetMapping("/export-addresses")
    public ResponseEntity<byte[]> exportCurriculumByAddresses(@RequestParam(required = false) String academicYear) throws Exception {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body = curriculumImportService.exportAddressWorkbook(effectiveYear);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        return workbookResponse(body, "УП по адресам ГБОУ 7 " + effectiveYear + " от " + date + ".xlsx");
    }

    private ResponseEntity<byte[]> workbookResponse(byte[] body, String fileName) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping
    public ResponseEntity<List<CurriculumPlanEntryResponse>> findAll(@RequestParam(required = false) String academicYear,
                                                                     @RequestParam(required = false) String building) {
        String effectiveYear = academicYearService.resolveRequestedOrDefault(academicYear);
        List<CurriculumPlanEntry> entries = curriculumPlanService.findAll(effectiveYear, building);
        return ResponseEntity.ok(toResponses(entries));
    }

    @GetMapping("/max-load-limits")
    public ResponseEntity<Map<Integer, java.math.BigDecimal>> maxLoadLimits() {
        return ResponseEntity.ok(CurriculumLoadStandard.maxHoursByParallel());
    }

    private List<CurriculumPlanEntryResponse> toResponses(List<CurriculumPlanEntry> entries) {
        List<Long> metaGroupIds = entries.stream()
                .map(CurriculumPlanEntry::getMetaGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MetaGroup> metaGroupsById = metaGroupIds.isEmpty()
                ? Map.of()
                : metaGroupRepository.findAllById(metaGroupIds).stream()
                .collect(Collectors.toMap(MetaGroup::getId, Function.identity()));
        return entries.stream()
                .map(entry -> CurriculumPlanEntryResponse.from(entry, resolvedSchoolBuildingId(entry, metaGroupsById)))
                .toList();
    }

    private Long resolvedSchoolBuildingId(CurriculumPlanEntry entry, Map<Long, MetaGroup> metaGroupsById) {
        if (entry.getMetaGroupId() == null) {
            return null;
        }
        MetaGroup metaGroup = metaGroupsById.get(entry.getMetaGroupId());
        if (metaGroup == null) {
            throw new IllegalStateException("Метагруппа учебного плана не найдена: " + entry.getMetaGroupId());
        }
        if (metaGroup.getSchoolBuildingId() == null) {
            throw new IllegalStateException("Для метагруппы не выбрана физическая площадка проведения: " + entry.getClassName());
        }
        return metaGroup.getSchoolBuildingId();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CurriculumPlanEntry> updateById(@PathVariable Long id, @RequestParam(required = false) String academicYear, @RequestBody CurriculumPlanEntryRequest request) {
        request.setAcademicYear(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.ok(curriculumPlanService.updateById(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        curriculumPlanService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(@RequestParam(required = false) String academicYear) {
        curriculumPlanService.clearAll(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.noContent().build();
    }
}
