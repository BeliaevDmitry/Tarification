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
    public ResponseEntity<List<StudyPeriodSetting>> findAll() {
        return ResponseEntity.ok(studyPeriodSettingService.findAll());
    }

    @PutMapping
    public ResponseEntity<List<StudyPeriodSetting>> saveAll(@RequestBody List<StudyPeriodSettingRequest> requests) {
        return ResponseEntity.ok(studyPeriodSettingService.saveAll(requests));
    }
}
