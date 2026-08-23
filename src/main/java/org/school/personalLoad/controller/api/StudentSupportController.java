package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.contingent.StudentSupportDtos;
import org.school.personalLoad.dto.contingent.StudentDataExchangeDtos;
import org.school.personalLoad.dto.contingent.IupOrderDocumentDtos;
import org.school.personalLoad.dto.contingent.StudentSupportDocumentDtos;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.service.StudentIdentityService;
import org.school.personalLoad.service.StudentDataExchangeService;
import org.school.personalLoad.service.StudentSupportService;
import org.school.personalLoad.service.StudentSupportDocumentService;
import org.school.personalLoad.service.IupOrderDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/contingent/special-support")
@RequiredArgsConstructor
public class StudentSupportController {

    private final StudentSupportService studentSupportService;
    private final StudentIdentityService studentIdentityService;
    private final AcademicYearService academicYearService;
    private final StudentDataExchangeService studentDataExchangeService;
    private final StudentSupportDocumentService studentSupportDocumentService;
    private final IupOrderDocumentService iupOrderDocumentService;

    @GetMapping("/summary")
    public ResponseEntity<StudentSupportDtos.SummaryResponse> summary(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String snapshotDate,
            @RequestParam(required = false) String asOfDate
    ) {
        return ResponseEntity.ok(studentSupportService.getSummary(
                effectiveYear(academicYear),
                parseDate(snapshotDate),
                parseDate(asOfDate)
        ));
    }

    @GetMapping("/references")
    public ResponseEntity<StudentSupportDtos.ReferenceDataResponse> references(
            @RequestParam(required = false) String academicYear
    ) {
        return ResponseEntity.ok(studentSupportService.getReferenceData(effectiveYear(academicYear)));
    }

    @PutMapping("/statuses")
    public ResponseEntity<StudentSupportDtos.RegisterRow> saveStatus(
            @RequestParam(required = false) String academicYear,
            @RequestBody StudentSupportDtos.StatusSaveRequest request,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(studentSupportService.saveStatus(effectiveYear(academicYear), request));
    }

    @PostMapping("/iups")
    public ResponseEntity<StudentSupportDtos.IupPlanView> saveIup(
            @RequestParam(required = false) String academicYear,
            @RequestBody StudentSupportDtos.IupSaveRequest request,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(studentSupportService.saveIup(effectiveYear(academicYear), request));
    }

    @GetMapping("/iups/{iupPlanId}")
    public ResponseEntity<StudentSupportDtos.IupPlanView> iup(
            @RequestParam(required = false) String academicYear,
            @PathVariable Long iupPlanId
    ) {
        return ResponseEntity.ok(studentSupportService.getIup(effectiveYear(academicYear), iupPlanId));
    }

    @PostMapping("/iup-orders/generate")
    public ResponseEntity<byte[]> generateIupOrder(
            @RequestParam(required = false) String academicYear,
            @RequestBody IupOrderDocumentDtos.GenerateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        IupOrderDocumentService.GeneratedDocument generated =
                iupOrderDocumentService.generate(effectiveYear(academicYear), request);
        String encodedFileName = URLEncoder.encode(generated.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ))
                .body(generated.content());
    }

    @PostMapping("/reconcile/{snapshotId}")
    public ResponseEntity<StudentIdentityService.LinkResult> reconcile(
            @PathVariable Long snapshotId,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(studentIdentityService.reconcileSnapshot(snapshotId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String snapshotDate,
            @RequestParam(required = false) String asOfDate
    ) {
        String year = effectiveYear(academicYear);
        byte[] body = studentSupportService.exportSummary(
                year,
                parseDate(snapshotDate),
                parseDate(asOfDate)
        );
        return workbookResponse(body, "Численность_статусы_ИУП_" + year + ".xlsx");
    }

    @GetMapping("/data-package/export")
    public ResponseEntity<byte[]> exportDataPackage(
            @RequestParam(required = false) String academicYear
    ) {
        String year = effectiveYear(academicYear);
        return workbookResponse(
                studentDataExchangeService.exportPackage(year),
                "Пакет_данных_дети_ИУП_группы_" + year + ".xlsx"
        );
    }

    @PostMapping("/data-package/import")
    public ResponseEntity<StudentDataExchangeDtos.ImportResult> importDataPackage(
            @RequestParam(required = false) String academicYear,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        StudentDataExchangeDtos.ImportResult result =
                studentDataExchangeService.importPackage(effectiveYear(academicYear), file);
        studentSupportDocumentService.synchronizeMseStatuses();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/data-package/readiness")
    public ResponseEntity<StudentDataExchangeDtos.ReadinessResponse> readiness(
            @RequestParam(required = false) String academicYear
    ) {
        return ResponseEntity.ok(studentDataExchangeService.readiness(effectiveYear(academicYear)));
    }

    @GetMapping("/documents")
    public ResponseEntity<java.util.List<StudentSupportDocumentDtos.View>> documents(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String asOfDate
    ) {
        return ResponseEntity.ok(studentSupportDocumentService.findAll(
                effectiveYear(academicYear),
                parseDate(asOfDate)
        ));
    }

    @PutMapping("/documents")
    public ResponseEntity<StudentSupportDocumentDtos.View> saveDocument(
            @RequestParam(required = false) String academicYear,
            @RequestBody StudentSupportDocumentDtos.SaveRequest request,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(studentSupportDocumentService.save(effectiveYear(academicYear), request));
    }

    @GetMapping("/documents/education-defaults")
    public ResponseEntity<StudentSupportDocumentDtos.EducationDefaultsView> documentEducationDefaults(
            @RequestParam(required = false) String academicYear,
            @RequestParam Long studentId,
            @RequestParam org.school.personalLoad.model.StudentSupportDocumentType documentType,
            @RequestParam(defaultValue = "false") boolean prolongationAvailable,
            @RequestParam(defaultValue = "false") boolean prolongationUsed,
            @RequestParam(required = false) String nosologyCode
    ) {
        return ResponseEntity.ok(studentSupportDocumentService.educationDefaults(
                effectiveYear(academicYear),
                studentId,
                documentType,
                prolongationAvailable,
                prolongationUsed,
                nosologyCode
        ));
    }

    @GetMapping("/nosologies")
    public ResponseEntity<java.util.List<StudentSupportDocumentDtos.NosologyView>> nosologies() {
        return ResponseEntity.ok(studentSupportDocumentService.findNosologies());
    }

    @PutMapping("/nosologies")
    public ResponseEntity<StudentSupportDocumentDtos.NosologyView> saveNosology(
            @RequestBody StudentSupportDocumentDtos.NosologySaveRequest request,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(studentSupportDocumentService.saveNosology(request));
    }

    @GetMapping("/correction-specialists")
    public ResponseEntity<java.util.List<StudentSupportDocumentDtos.SpecialistView>> correctionSpecialists() {
        return ResponseEntity.ok(studentSupportDocumentService.findSpecialists());
    }

    @PostMapping("/correction-specialists")
    public ResponseEntity<StudentSupportDocumentDtos.SpecialistView> saveCorrectionSpecialist(
            @RequestBody StudentSupportDocumentDtos.SpecialistSaveRequest request,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        return ResponseEntity.ok(studentSupportDocumentService.saveSpecialist(request));
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @RequestParam(required = false) String academicYear,
            @PathVariable Long documentId,
            HttpServletRequest httpServletRequest
    ) {
        validateEdit(AuthSessionUtils.requiredUser(httpServletRequest));
        studentSupportDocumentService.delete(effectiveYear(academicYear), documentId);
        return ResponseEntity.noContent().build();
    }

    private String effectiveYear(String academicYear) {
        return academicYearService.resolveRequestedOrDefault(academicYear);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private ResponseEntity<byte[]> workbookResponse(byte[] body, String fileName) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    private void validateEdit(SessionUser user) {
        if (!user.canEditTab(AppTab.OVZ)
                && !user.canEditTab(AppTab.CONTINGENT_IMPORT)
                && !user.canEditTab(AppTab.CONTINGENT_STATS)) {
            throw new AuthExceptions.ForbiddenException("Нет прав на редактирование статусов детей и ИУП");
        }
    }
}
