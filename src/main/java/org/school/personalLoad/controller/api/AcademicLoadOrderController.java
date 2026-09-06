package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.school.personalLoad.dto.AcademicLoadOrderDtos;
import org.school.personalLoad.model.AcademicLoadOrder;
import org.school.personalLoad.service.AcademicLoadOrderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/load-orders")
@RequiredArgsConstructor
public class AcademicLoadOrderController {

    private final AcademicLoadOrderService service;

    @GetMapping
    public List<AcademicLoadOrderDtos.OrderView> list(@RequestParam(required = false) String academicYear) {
        return service.list(academicYear);
    }

    @GetMapping("/readiness")
    public AcademicLoadOrderDtos.ReadinessView readiness(@RequestParam(required = false) String academicYear) {
        return service.readiness(academicYear);
    }

    @GetMapping("/references")
    public AcademicLoadOrderDtos.ReferencesView references() {
        return service.references();
    }

    @PostMapping
    public AcademicLoadOrderDtos.OrderView create(@RequestBody AcademicLoadOrderDtos.CreateRequest request,
                                                   HttpServletRequest httpRequest) {
        return service.create(request, resolveSchoolCode(httpRequest), AuthSessionUtils.requiredUser(httpRequest));
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Long id) {
        AcademicLoadOrder order = service.document(id);
        String encoded = URLEncoder.encode(order.getDocumentFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(order.getDocumentContent());
    }

    private String resolveSchoolCode(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Host");
        return SchoolCodeResolver.resolve(forwarded == null || forwarded.isBlank() ? request.getHeader("Host") : forwarded);
    }
}
