package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.dto.AcademicYearDtos;
import org.school.personalLoad.model.AcademicYear;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    @GetMapping
    public ResponseEntity<AcademicYearDtos.AcademicYearListResponse> list() {
        List<AcademicYearDtos.AcademicYearResponse> years = academicYearService.findAll().stream()
                .map(this::toResponse)
                .toList();
        AcademicYear current = academicYearService.resolveCurrent();
        return ResponseEntity.ok(AcademicYearDtos.AcademicYearListResponse.builder()
                .currentAcademicYear(current.getName())
                .years(years)
                .build());
    }

    @PostMapping
    public ResponseEntity<AcademicYearDtos.AcademicYearResponse> create(@RequestBody AcademicYearDtos.CreateAcademicYearRequest request,
                                                                        HttpServletRequest httpServletRequest) {
        ensureAdmin(httpServletRequest);
        AcademicYear created = academicYearService.create(request.getStartYear());
        return ResponseEntity.ok(toResponse(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        ensureAdmin(httpServletRequest);
        academicYearService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AcademicYearDtos.AcademicYearResponse toResponse(AcademicYear year) {
        return AcademicYearDtos.AcademicYearResponse.builder()
                .id(year.getId())
                .name(year.getName())
                .startDate(year.getStartDate())
                .endDate(year.getEndDate())
                .startYear(year.getStartYear())
                .build();
    }

    private void ensureAdmin(HttpServletRequest request) {
        if (!AuthSessionUtils.requiredUser(request).isAdmin()) {
            throw new ForbiddenException("Операция доступна только администратору");
        }
    }
}

