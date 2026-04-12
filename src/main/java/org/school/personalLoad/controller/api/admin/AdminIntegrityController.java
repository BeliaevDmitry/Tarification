package org.school.personalLoad.controller.api.admin;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.auth.AuthExceptions.ForbiddenException;
import org.school.personalLoad.auth.AuthSessionUtils;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.ManualLoadEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/integrity")
@RequiredArgsConstructor
public class AdminIntegrityController {

    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final StudyPeriodSettingRepository studyPeriodSettingRepository;

    @GetMapping
    public ResponseEntity<IntegrityResponse> inspect(HttpServletRequest request) {
        ensureAdmin(request);

        List<IntegrityIssue> issues = new ArrayList<>();
        long blankCurriculumYear = curriculumPlanEntryRepository.findAll().stream()
                .filter(row -> isBlank(row.getAcademicYear()))
                .count();
        if (blankCurriculumYear > 0) {
            issues.add(new IntegrityIssue("CURRICULUM_BLANK_YEAR", "Записи УП без учебного года", blankCurriculumYear));
        }

        long blankManualYear = manualLoadEntryRepository.findAll().stream()
                .filter(row -> isBlank(row.getAcademicYear()))
                .count();
        if (blankManualYear > 0) {
            issues.add(new IntegrityIssue("MANUAL_LOAD_BLANK_YEAR", "Записи нагрузки без учебного года", blankManualYear));
        }

        long blankClassroomYear = classroomLeadershipRepository.findAll().stream()
                .filter(row -> isBlank(row.getAcademicYear()))
                .count();
        if (blankClassroomYear > 0) {
            issues.add(new IntegrityIssue("CLASSROOM_BLANK_YEAR", "Классное руководство без учебного года", blankClassroomYear));
        }

        long blankSettingsYear = studyPeriodSettingRepository.findAll().stream()
                .filter(row -> isBlank(row.getAcademicYear()))
                .count();
        if (blankSettingsYear > 0) {
            issues.add(new IntegrityIssue("STUDY_SETTINGS_BLANK_YEAR", "Периоды обучения без учебного года", blankSettingsYear));
        }

        long crossYearManualLinks = countCrossYearManualLinks();
        if (crossYearManualLinks > 0) {
            issues.add(new IntegrityIssue("MANUAL_LOAD_MISSING_CURRICULUM", "Нагрузка не находит правило УП в своём учебном году", crossYearManualLinks));
        }

        String status = issues.isEmpty() ? "OK" : "WARN";
        return ResponseEntity.ok(new IntegrityResponse(status, issues));
    }

    private long countCrossYearManualLinks() {
        long invalid = 0;
        for (ManualLoadEntry load : manualLoadEntryRepository.findAll()) {
            String year = trim(load.getAcademicYear());
            if (year.isEmpty()) {
                invalid++;
                continue;
            }
            boolean found = curriculumPlanEntryRepository
                    .findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                            year,
                            trim(load.getNumberSchoolBuilding()),
                            trim(load.getClassName()),
                            trim(load.getSubjectName()),
                            load.getEducationLevel(),
                            load.getStudyPeriod()
                    )
                    .isPresent();
            if (!found) invalid++;
        }
        return invalid;
    }

    private static boolean isBlank(String value) {
        return trim(value).isEmpty();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void ensureAdmin(HttpServletRequest request) {
        if (!AuthSessionUtils.requiredUser(request).isAdmin()) {
            throw new ForbiddenException("Операция доступна только администратору");
        }
    }

    public record IntegrityIssue(String code, String message, long count) {}
    public record IntegrityResponse(String status, List<IntegrityIssue> issues) {}
}
