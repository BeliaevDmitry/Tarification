package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.StudyPeriodSettingRequest;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.school.personalLoad.service.AcademicYearService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings/study-periods")
@RequiredArgsConstructor
public class StudyPeriodSettingController {

    private final StudyPeriodSettingService studyPeriodSettingService;
    private final AcademicYearService academicYearService;

    @GetMapping
    public ResponseEntity<List<StudyPeriodSetting>> findAll(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(studyPeriodSettingService.findAll(academicYearService.resolveRequestedOrDefault(academicYear)));
    }

    @GetMapping("/for-class")
    public ResponseEntity<List<StudyPeriodSetting>> findByClass(@RequestParam String className, @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(studyPeriodSettingService.findAvailableForClass(academicYearService.resolveRequestedOrDefault(academicYear), className));
    }

    @PostMapping
    public ResponseEntity<StudyPeriodSetting> create(@RequestBody StudyPeriodSettingRequest request, @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(studyPeriodSettingService.create(academicYearService.resolveRequestedOrDefault(academicYear), request));
    }

    @PutMapping
    public ResponseEntity<List<StudyPeriodSetting>> saveAll(@RequestBody List<StudyPeriodSettingRequest> requests, @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(studyPeriodSettingService.saveAll(academicYearService.resolveRequestedOrDefault(academicYear), requests));
    }
}
