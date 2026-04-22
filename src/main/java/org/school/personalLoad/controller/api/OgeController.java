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
        ensureCanEditTab(user, AppTab.OGE_GIA_UPLOAD, "Загрузка выгрузок ГИА");
        return ResponseEntity.ok(ogeService.importGia(academicYearService.resolveRequestedOrDefault(academicYear), files));
    }

    @GetMapping("/gia/versions")
    public ResponseEntity<List<OgeDtos.GiaVersionView>> versions() {
        return ResponseEntity.ok(ogeService.versions());
    }

    @GetMapping("/gia/participants")
    public ResponseEntity<List<OgeDtos.GiaParticipantView>> participants() {
        return ResponseEntity.ok(ogeService.latestParticipants());
    }

    @GetMapping("/gia/changes")
    public ResponseEntity<OgeDtos.GiaChangesResponse> changes() {
        return ResponseEntity.ok(ogeService.changesBetweenLastTwo());
    }

    @GetMapping("/gia/stats")
    public ResponseEntity<OgeDtos.GiaStatsResponse> giaStats() {
        return ResponseEntity.ok(ogeService.giaStats());
    }

    @GetMapping("/mismatches")
    public ResponseEntity<OgeDtos.GiaMismatchResponse> mismatches(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(ogeService.giaMismatches(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/gia/export")
    public ResponseEntity<byte[]> exportGia() throws Exception {
        byte[] body = ogeService.exportGiaWorkbook();
        String fileName = "ОГЭ_выгрузка_" + LocalDate.now() + ".xlsx";
        return excel(fileName, body);
    }

    @GetMapping("/scores")
    public ResponseEntity<List<OgeDtos.ScoreScaleRow>> scores() {
        return ResponseEntity.ok(ogeService.scoreScale());
    }

    @PutMapping("/scores")
    public ResponseEntity<Void> updateScores(@RequestBody List<OgeDtos.ScoreScaleRow> rows,
                                             HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureCanEditTab(user, AppTab.VSOKO_EDIT, "Редактирование шкалы баллов ОГЭ");
        ogeService.upsertScoreScale(rows);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/works/import")
    public ResponseEntity<List<OgeDtos.ImportFileResult>> importWorks(@RequestParam("files") List<MultipartFile> files,
                                                                      @RequestParam(required = false) String academicYear,
                                                                      HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        ensureCanEditTab(user, AppTab.OGE_WORK_UPLOAD, "Загрузка работ ОГЭ");
        return ResponseEntity.ok(ogeService.importWorks(academicYearService.resolveRequestedOrDefault(academicYear), files));
    }

    @GetMapping("/works/dataset")
    public ResponseEntity<OgeDtos.WorkDatasetResponse> workDataset() {
        return ResponseEntity.ok(ogeService.workDataset());
    }

    @GetMapping("/works/export")
    public ResponseEntity<byte[]> exportWorks(@RequestParam(required = false) String academicYear) throws Exception {
        byte[] body = ogeService.exportWorksWorkbook(academicYearService.resolveRequestedOrDefault(academicYear));
        String fileName = "ОГЭ_работы_" + LocalDate.now() + ".xlsx";
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
}
