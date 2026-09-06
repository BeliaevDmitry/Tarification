package org.school.personalLoad.masterfot;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.*;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.service.AcademicYearService;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/master-fot")
@RequiredArgsConstructor
public class FotController {
    private final FotService service;
    private final AcademicYearService years;
    @GetMapping public FotDtos.Overview overview(@RequestParam(required = false) String academicYear) {
        return service.overview(years.resolveRequestedOrDefault(academicYear));
    }
    @PostMapping("/import") public FotDtos.BatchRow upload(@RequestParam(required = false) String academicYear,
                                                           @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        SessionUser user = AuthSessionUtils.requiredUser(request);
        if (!user.canImportTab(AppTab.LOAD_MASTER_FOT)) throw new ForbiddenException("Нет права импорта Мастер ФОТ");
        String host = String.join(",", header(request, "X-Forwarded-Host"), header(request, "X-Original-Host"),
                header(request, "Host"), request.getServerName());
        return service.upload(years.resolveRequestedOrDefault(academicYear), file, user.getFullName(), SchoolCodeResolver.resolve(host));
    }
    @PatchMapping("/issues/{id}") public FotDtos.IssueRow decision(@RequestParam(required = false) String academicYear,
                                                                  @PathVariable String id, @RequestBody FotDtos.DecisionRequest body, HttpServletRequest request) {
        return service.decision(years.resolveRequestedOrDefault(academicYear), id, body, AuthSessionUtils.requiredUser(request).getFullName());
    }
    @GetMapping("/batches/{id}") public List<FotDtos.Finding> history(@RequestParam(required = false) String academicYear, @PathVariable Long id) {
        return service.history(years.resolveRequestedOrDefault(academicYear), id);
    }
    @GetMapping("/options") public FotDtos.Options options(@RequestParam(required = false) String academicYear) {
        return service.options(years.resolveRequestedOrDefault(academicYear));
    }
    @PutMapping("/mappings") public void mapping(@RequestParam(required = false) String academicYear, @RequestBody FotDtos.MappingRequest body) {
        service.saveMapping(years.resolveRequestedOrDefault(academicYear), body);
    }
    private String header(HttpServletRequest request, String name) {
        return Objects.toString(request.getHeader(name), "");
    }
}
