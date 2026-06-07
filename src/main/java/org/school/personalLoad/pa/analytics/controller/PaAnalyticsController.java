package org.school.personalLoad.pa.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.pa.analytics.dto.PaAnalyticsDtos;
import org.school.personalLoad.pa.analytics.model.PaReportAnalysisSummary;
import org.school.personalLoad.pa.analytics.repository.PaReportAnalysisSummaryRepository;
import org.school.personalLoad.pa.analytics.service.PaReportAnalysisJobRunner;
import org.school.personalLoad.pa.analytics.service.PaReportAnalysisService;
import org.school.personalLoad.pa.analytics.service.PaTeacherAnalyticsService;
import org.school.personalLoad.pa.analytics.service.PaTeacherDossierService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pa/analytics")
@RequiredArgsConstructor
public class PaAnalyticsController {

    private final PaReportAnalysisService analysisService;
    private final PaReportAnalysisJobRunner analysisJobRunner;
    private final PaTeacherAnalyticsService teacherAnalyticsService;
    private final PaTeacherDossierService teacherDossierService;
    private final PaReportAnalysisSummaryRepository summaryRepository;
    private final AcademicYearService academicYearService;

    @GetMapping("/reports")
    public ResponseEntity<List<PaAnalyticsDtos.ReportAnalysisListItem>> reports(@RequestParam(required = false) String academicYear,
                                                                                 @RequestParam(required = false) String subjectName,
                                                                                 @RequestParam(required = false) String teacherFio,
                                                                                 @RequestParam(required = false) String className,
                                                                                 @RequestParam(required = false) String workType,
                                                                                 @RequestParam(required = false) Boolean onlyProblems,
                                                                                 @RequestParam(required = false) Boolean onlyNeedsReview) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(analysisService.getReports(year, subjectName, teacherFio, className, workType, onlyProblems, onlyNeedsReview));
    }


    @GetMapping("/teachers")
    public ResponseEntity<List<PaAnalyticsDtos.TeacherSummaryRow>> teachers(@RequestParam(required = false) String academicYear,
                                                                             @RequestParam(required = false) String subjectName,
                                                                             @RequestParam(required = false) Boolean onlyNeedsReview) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(teacherAnalyticsService.getTeacherSummaries(year, subjectName, onlyNeedsReview));
    }

    @GetMapping("/teacher-details")
    public ResponseEntity<PaAnalyticsDtos.TeacherDetailsResponse> teacherDetails(@RequestParam(required = false) String academicYear,
                                                                                 @RequestParam String teacherFio) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        return ResponseEntity.ok(teacherAnalyticsService.getTeacherDetails(year, teacherFio));
    }


    @GetMapping("/teacher-dossier.docx")
    public ResponseEntity<byte[]> teacherDossier(@RequestParam(required = false) String academicYear,
                                                 @RequestParam String teacherFio) {
        String year = academicYearService.resolveRequestedOrDefault(academicYear);
        byte[] body;
        try {
            body = teacherDossierService.generateTeacherDossier(year, teacherFio);
        } catch (IllegalArgumentException exception) {
            return textResponse(404, exception.getMessage());
        } catch (IOException exception) {
            return textResponse(500, "Не удалось сформировать Word-досье: " + exception.getMessage());
        }
        String fileName = "Досье_ПА_" + safeFilePart(teacherFio) + "_" + safeFilePart(year) + ".docx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(body);
    }

    @GetMapping("/reports/{reportVersionId}")
    public ResponseEntity<PaAnalyticsDtos.ReportAnalysisDetails> details(@PathVariable Long reportVersionId) {
        return ResponseEntity.ok(analysisService.getDetails(reportVersionId));
    }

    @PostMapping("/reports/{reportVersionId}/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildReport(@PathVariable Long reportVersionId) {
        try {
            analysisJobRunner.analyzeOneInNewTransaction(reportVersionId);
            return ResponseEntity.ok(Map.of("reportVersionId", reportVersionId, "status", "REBUILD_FINISHED"));
        } catch (Exception exception) {
            try {
                analysisJobRunner.saveAnalysisErrorInNewTransaction(reportVersionId, exception);
            } catch (Exception ignored) {
                // Ошибка записи аналитического лога не должна ломать rebuild endpoint.
            }
            return ResponseEntity.ok(Map.of("reportVersionId", reportVersionId, "status", "ERROR", "message", exception.getMessage()));
        }
    }

    @PostMapping("/rebuild")
    public ResponseEntity<PaAnalyticsDtos.RebuildAllResult> rebuildAll(@RequestParam String academicYear) {
        return ResponseEntity.ok(analysisService.rebuildAll(academicYear));
    }

    @GetMapping("/reports/{reportVersionId}/log/download")
    public ResponseEntity<byte[]> downloadLog(@PathVariable Long reportVersionId) throws Exception {
        PaReportAnalysisSummary summary = summaryRepository.findByReportVersionId(reportVersionId).orElse(null);
        String fileName = "analysis_report_" + reportVersionId + ".txt";
        String text;
        if (summary != null
                && summary.getAnalysisErrorLogPath() != null
                && !summary.getAnalysisErrorLogPath().isBlank()
                && Files.isRegularFile(Path.of(summary.getAnalysisErrorLogPath()))) {
            Path path = Path.of(summary.getAnalysisErrorLogPath());
            byte[] body = Files.readAllBytes(path);
            fileName = summary.getAnalysisErrorLogFileName() == null || summary.getAnalysisErrorLogFileName().isBlank()
                    ? path.getFileName().toString()
                    : summary.getAnalysisErrorLogFileName();
            return textFile(fileName, body);
        } else if (summary != null && summary.getAnalysisMessage() != null && !summary.getAnalysisMessage().isBlank()) {
            text = "reportVersionId: " + reportVersionId + System.lineSeparator()
                    + "analysisStatus: " + summary.getAnalysisStatus() + System.lineSeparator()
                    + "analysisMessage: " + summary.getAnalysisMessage() + System.lineSeparator();
        } else {
            text = "Анализ ещё не выполнялся";
        }
        return textFile(fileName, text.getBytes(StandardCharsets.UTF_8));
    }

    private String safeFilePart(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("[^\\p{L}\\p{N}._-]+", "_");
    }

    private ResponseEntity<byte[]> textResponse(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.valueOf(message == null ? "" : message).getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> textFile(String fileName, byte[] body) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }
}
