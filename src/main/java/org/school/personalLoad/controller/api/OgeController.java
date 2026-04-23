package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AppTab;
import org.school.personalLoad.auth.AuthExceptions;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.oge.dto.OgeDtos;
import org.school.personalLoad.oge.service.OgeService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/oge")
@RequiredArgsConstructor
public class OgeController {
    private final OgeService ogeService;
    private final AcademicYearService academicYearService;

    @PostMapping("/gia/import")
    public ResponseEntity<List<OgeDtos.ImportFileResult>> importGia(@RequestParam("files") List<MultipartFile> files,
                                                                    @RequestParam(required = false) String academicYear,
                                                                    HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureCanViewTab(user, AppTab.OGE_UPLOAD_VIEW, "ОГЭ / Выгрузка");
        ensureCanEditTab(user, AppTab.OGE_GIA_UPLOAD, "Загрузка выгрузок ГИА");
        return ResponseEntity.ok(ogeService.importGia(academicYearService.resolveRequestedOrDefault(academicYear), files));
    }

    @GetMapping("/gia/versions")
    public ResponseEntity<List<OgeDtos.GiaVersionView>> versions(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ogeService.versions(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/gia/participants")
    public ResponseEntity<List<OgeDtos.GiaParticipantView>> participants(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ogeService.latestParticipants(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/gia/changes")
    public ResponseEntity<OgeDtos.GiaChangesResponse> changes(@RequestParam(required = false) String academicYear, HttpServletRequest request) {
        ensureCanViewTab(AuthSessionUtils.requiredUser(request), AppTab.OGE_UPLOAD_VIEW, "ОГЭ / Выгрузка");
        return ResponseEntity.ok(ogeService.changesBetweenLastTwo(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/gia/stats")
    public ResponseEntity<OgeDtos.GiaStatsResponse> giaStats(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ogeService.giaStats(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/mismatches")
    public ResponseEntity<OgeDtos.GiaMismatchResponse> mismatches(@RequestParam(required = false) String academicYear,
                                                                  HttpServletRequest request) {
        ensureCanViewTab(AuthSessionUtils.requiredUser(request), AppTab.OGE_MISMATCH_VIEW, "ОГЭ / Нестыковки");
        return ResponseEntity.ok(ogeService.giaMismatches(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/mismatches/export")
    public ResponseEntity<byte[]> exportMismatches(@RequestParam(required = false) String academicYear,
                                                   HttpServletRequest request) throws Exception {
        ensureCanViewTab(AuthSessionUtils.requiredUser(request), AppTab.OGE_MISMATCH_VIEW, "ОГЭ / Нестыковки");
        byte[] body = ogeService.exportMismatchesWorkbook(academicYearService.resolveRequestedOrDefault(academicYear));
        String fileName = "ОГЭ_нестыковки_" + LocalDate.now() + ".xlsx";
        return excel(fileName, body);
    }

    @GetMapping("/gia/export")
    public ResponseEntity<byte[]> exportGia(@RequestParam(required = false) String academicYear) throws Exception {
        byte[] body = ogeService.exportGiaWorkbook(academicYearService.resolveRequestedOrDefault(academicYear));
        String fileName = "ОГЭ_выгрузка_" + LocalDate.now() + ".xlsx";
        return excel(fileName, body);
    }

    @GetMapping("/scores")
    public ResponseEntity<List<OgeDtos.ScoreScaleRow>> scores(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ogeService.scoreScale(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/evaluation")
    public ResponseEntity<List<OgeDtos.EvaluationRow>> evaluation(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ogeService.evaluationRows(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @PutMapping("/evaluation")
    public ResponseEntity<Void> updateEvaluation(@RequestParam(required = false) String academicYear,
                                                 @RequestBody List<OgeDtos.EvaluationRow> rows,
                                                 HttpServletRequest request) {
        ensureCanEditTab(AuthSessionUtils.requiredUser(request), AppTab.VSOKO_EDIT, "Редактирование оценивания ОГЭ");
        ogeService.upsertEvaluationRows(academicYearService.resolveRequestedOrDefault(academicYear), rows);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/scores")
    public ResponseEntity<Void> updateScores(@RequestParam(required = false) String academicYear,
                                             @RequestBody List<OgeDtos.ScoreScaleRow> rows,
                                             HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureCanEditTab(user, AppTab.VSOKO_EDIT, "Редактирование шкалы баллов ОГЭ");
        ogeService.upsertScoreScale(academicYearService.resolveRequestedOrDefault(academicYear), rows);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/works/import")
    public ResponseEntity<List<OgeDtos.ImportFileResult>> importWorks(@RequestParam("files") List<MultipartFile> files,
                                                                      @RequestParam(required = false) String academicYear,
                                                                      HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureCanEditTab(user, AppTab.OGE_WORK_UPLOAD, "Загрузка работ ОГЭ");
        return ResponseEntity.ok(ogeService.importWorks(academicYearService.resolveRequestedOrDefault(academicYear), files, "INTERNAL"));
    }

    @PostMapping("/external-works/import")
    public ResponseEntity<List<OgeDtos.ImportFileResult>> importExternalWorks(@RequestParam("files") List<MultipartFile> files,
                                                                               @RequestParam(required = false) String academicYear,
                                                                               HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureCanEditTab(user, AppTab.OGE_WORK_UPLOAD, "Загрузка внешних работ ОГЭ");
        return ResponseEntity.ok(ogeService.importWorks(academicYearService.resolveRequestedOrDefault(academicYear), files, "EXTERNAL_TRYOUT"));
    }

    @GetMapping("/works/dataset")
    public ResponseEntity<OgeDtos.WorkDatasetResponse> workDataset(@RequestParam(required = false) String academicYear,
                                                                   @RequestParam(defaultValue = "INTERNAL") String source) {
        return ResponseEntity.ok(ogeService.workDataset(academicYearService.resolveRequestedOrDefault(academicYear), source));
    }

    @GetMapping("/works/teachers")
    public ResponseEntity<List<String>> teachers() {
        return ResponseEntity.ok(ogeService.teachers());
    }

    @GetMapping("/works/teacher-binding")
    public ResponseEntity<List<OgeDtos.TeacherBindingRow>> teacherBinding(@RequestParam(required = false) String academicYear,
                                                                          @RequestParam(defaultValue = "true") boolean onlyUnbound) {
        return ResponseEntity.ok(ogeService.teacherBindings(academicYearService.resolveRequestedOrDefault(academicYear), onlyUnbound));
    }

    @PutMapping("/works/teacher-binding")
    public ResponseEntity<Void> updateTeacherBinding(@RequestParam(required = false) String academicYear,
                                                     @RequestBody List<OgeDtos.TeacherBindingUpdate> updates,
                                                     HttpServletRequest request) {
        ensureCanEditTab(AuthSessionUtils.requiredUser(request), AppTab.OGE_WORK_UPLOAD, "Привязка педагога для результатов ОГЭ");
        ogeService.updateTeacherBindings(academicYearService.resolveRequestedOrDefault(academicYear), updates);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/works/teacher-binding/from-load")
    public ResponseEntity<String> bindTeachersFromLoad(@RequestParam(required = false) String academicYear,
                                                       HttpServletRequest request) {
        ensureCanEditTab(AuthSessionUtils.requiredUser(request), AppTab.OGE_WORK_UPLOAD, "Привязка педагога из нагрузки");
        int updated = ogeService.bindTeachersFromLoad(academicYearService.resolveRequestedOrDefault(academicYear));
        return ResponseEntity.ok("Привязано записей: " + updated);
    }

    @GetMapping("/works/export")
    public ResponseEntity<byte[]> exportWorks(@RequestParam(required = false) String academicYear,
                                              @RequestParam(defaultValue = "INTERNAL") String source) throws Exception {
        byte[] body = ogeService.exportWorksWorkbook(academicYearService.resolveRequestedOrDefault(academicYear), source);
        String fileName = ("EXTERNAL_TRYOUT".equalsIgnoreCase(source) ? "ОГЭ_внешние_работы_" : "ОГЭ_внутренние_работы_") + LocalDate.now() + ".xlsx";
        return excel(fileName, body);
    }

    private ResponseEntity<byte[]> excel(String fileName, byte[] body) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    private void ensureCanEditTab(SessionUser user, AppTab tab, String action) {
        if (user.isAdmin() || user.canEditTab(tab)) return;
        throw new AuthExceptions.ForbiddenException(action + " доступна только пользователям с соответствующим правом");
    }

    private void ensureCanViewTab(SessionUser user, AppTab tab, String action) {
        if (user.isAdmin() || user.canViewTab(tab)) return;
        throw new AuthExceptions.ForbiddenException(action + " доступна только пользователям с соответствующим правом просмотра");
    }
}
