package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.StudyPeriodSettingRequest;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings/study-periods")
@RequiredArgsConstructor
public class StudyPeriodSettingController {

    private final StudyPeriodSettingService studyPeriodSettingService;

    @GetMapping
    public ResponseEntity<List<StudyPeriodSetting>> findAll(@RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(studyPeriodSettingService.findAll(academicYear));
    }

    @GetMapping("/for-class")
    public ResponseEntity<List<StudyPeriodSetting>> findByClass(@RequestParam(required = false) String academicYear,
                                                                @RequestParam String className) {
        return ResponseEntity.ok(studyPeriodSettingService.findAvailableForClass(academicYear, className));
    }

    @PostMapping
    public ResponseEntity<StudyPeriodSetting> create(@RequestParam(required = false) String academicYear,
                                                     @RequestBody StudyPeriodSettingRequest request) {
        return ResponseEntity.ok(studyPeriodSettingService.create(academicYear, request));
    }

    @PutMapping
    public ResponseEntity<List<StudyPeriodSetting>> saveAll(@RequestParam(required = false) String academicYear,
                                                            @RequestBody List<StudyPeriodSettingRequest> requests) {
        return ResponseEntity.ok(studyPeriodSettingService.saveAll(academicYear, requests));
    }
}
