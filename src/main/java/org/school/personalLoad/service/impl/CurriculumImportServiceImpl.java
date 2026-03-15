package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumImportResult;
import org.school.personalLoad.dto.CurriculumImportRow;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.service.CurriculumImportService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CurriculumImportServiceImpl implements CurriculumImportService {

    private final CurriculumExcelParser parser;
    private final CurriculumPlanEntryRepository curriculumRepository;
    private final ClassroomLeadershipRepository classroomRepository;
    private final ManualLoadEntryRepository manualLoadRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final SubjectCatalogRepository subjectCatalogRepository;

    @Override
    public CurriculumImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл обязателен");

        try {
            List<CurriculumImportRow> parsed = parser.parse(file.getInputStream());
            int created = 0, updated = 0, classesCreated = 0, subjectsImported = 0;
            Set<Long> importedIds = new HashSet<>();
            Map<String, SubjectCatalogEntry> existingSubjects = new HashMap<>();
            subjectCatalogRepository.findAll().forEach(s -> existingSubjects.put(subjectKey(s.getSubjectName(), s.getSubjectType()), s));

            String fallbackTeacher = teacherRepository.findAll().stream().findFirst().map(TeacherDirectoryEntry::getFioTeacher).orElse("Не назначен");

            for (CurriculumImportRow row : parsed) {
                CurriculumPlanEntry entry = curriculumRepository
                        .findFirstByAcademicYearAndStageAndClassNameAndSubjectNameAndStudyPeriod(
                                row.getAcademicYear(), row.getStage(), row.getClassName(), row.getSubjectName(), row.getStudyPeriod())
                        .orElseGet(CurriculumPlanEntry::new);

                boolean isNew = entry.getId() == null;
                entry.setAcademicYear(row.getAcademicYear());
                entry.setStage(row.getStage());
                entry.setClassName(row.getClassName());
                entry.setSubjectName(row.getSubjectName());
                entry.setStudyPeriod(row.getStudyPeriod());
                entry.setPlannedHours(row.getPlannedHours());
                entry.setCurriculumPart(row.getCurriculumPart() == null ? CurriculumPart.CORE : row.getCurriculumPart());
                entry.setDeprecated(false);
                if (isNew) {
                    entry.setNumberSchoolBuilding("СП0");
                    entry.setEducationLevel(EducationLevel.BASIC);
                    entry.setSubgroupRequired(false);
                    entry.setSubgroupCount(0);
                }

                if (entry.getEducationLevel() != EducationLevel.ADVANCED) {
                    entry.setEducationLevel(EducationLevel.BASIC);
                }
                if ("СП0".equalsIgnoreCase(entry.getNumberSchoolBuilding()) || entry.getNumberSchoolBuilding() == null || entry.getNumberSchoolBuilding().isBlank()) {
                    entry.setNumberSchoolBuilding("СП0");
                }

                CurriculumPlanEntry saved = curriculumRepository.save(entry);
                importedIds.add(saved.getId());
                if (isNew) created++; else updated++;

                if (!classroomRepository.existsByNumberSchoolBuildingAndClassName("СП0", row.getClassName())) {
                    ClassroomLeadershipEntry cls = new ClassroomLeadershipEntry();
                    cls.setNumberSchoolBuilding("СП0");
                    cls.setClassName(row.getClassName());
                    cls.setClassDirection(row.getClassDirection() == null || row.getClassDirection().isBlank() ? "Не указана" : row.getClassDirection());
                    cls.setFioTeacher(fallbackTeacher);
                    classroomRepository.save(cls);
                    classesCreated++;
                }

                SubjectType subjectType = resolveSubjectType(row);
                String normalizedSubject = normalizeSubject(row.getSubjectName());
                String subjectKey = subjectKey(normalizedSubject, subjectType);
                if (!normalizedSubject.isBlank() && !existingSubjects.containsKey(subjectKey)) {
                    SubjectCatalogEntry subjectCatalogEntry = new SubjectCatalogEntry();
                    subjectCatalogEntry.setSubjectName(normalizedSubject);
                    subjectCatalogEntry.setSubjectType(subjectType);
                    existingSubjects.put(subjectKey, subjectCatalogRepository.save(subjectCatalogEntry));
                    subjectsImported++;
                }
            }

            int deprecated = 0;
            List<CurriculumPlanEntry> all = curriculumRepository.findAll();
            for (CurriculumPlanEntry e : all) {
                boolean shouldDeprecate = !importedIds.contains(e.getId());
                if (shouldDeprecate && !e.isDeprecated()) {
                    e.setDeprecated(true);
                    curriculumRepository.save(e);
                    deprecated++;
                }
            }

            Set<String> activeKeys = new HashSet<>();
            curriculumRepository.findAll().stream().filter(e -> !e.isDeprecated()).forEach(e ->
                    activeKeys.add(keyWithoutBuilding(e.getClassName(), e.getSubjectName(), e.getEducationLevel())));

            int orphaned = 0;
            List<ManualLoadEntry> loads = manualLoadRepository.findAll();
            for (ManualLoadEntry l : loads) {
                boolean isOrphan = !activeKeys.contains(keyWithoutBuilding(ClassNameNormalizer.normalize(l.getClassName()), l.getSubjectName(), l.getEducationLevel()));
                l.setOrphaned(isOrphan);
                if (isOrphan) orphaned++;
            }
            manualLoadRepository.saveAll(loads);

            return new CurriculumImportResult(created, updated, deprecated, classesCreated, orphaned, subjectsImported);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось импортировать учебный план", e);
        }
    }

    private SubjectType resolveSubjectType(CurriculumImportRow row) {
        if (row.getCurriculumPart() == CurriculumPart.EXTRACURRICULAR) {
            return SubjectType.EXTRACURRICULAR;
        }
        String value = String.valueOf(row.getSubjectName() == null ? "" : row.getSubjectName()).trim().toLowerCase(Locale.ROOT);
        if (value.contains("внеур") || value.contains("разговоры о важном")) {
            return SubjectType.EXTRACURRICULAR;
        }
        return SubjectType.CORE_FORMABLE;
    }

    private String normalizeSubject(String value) {
        return String.valueOf(value == null ? "" : value).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String subjectKey(String name, SubjectType type) {
        return normalizeSubject(name).toLowerCase(Locale.ROOT) + "|" + type.name();
    }

    private String keyWithoutBuilding(String c, String s, EducationLevel l) {
        return String.join("|", String.valueOf(c).trim(), String.valueOf(s).trim(), String.valueOf(l));
    }
}
