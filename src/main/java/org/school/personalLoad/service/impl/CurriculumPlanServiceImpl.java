package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.CurriculumPlanEntryRequest;
import org.school.personalLoad.model.CurriculumPart;
import org.school.personalLoad.model.CurriculumModule;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.model.EducationLevel;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.model.StudyPeriod;
import org.school.personalLoad.model.StudyPeriodSetting;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.StudyPeriodSettingRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.service.CurriculumPlanService;
import org.school.personalLoad.service.StudyPeriodSettingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CurriculumPlanServiceImpl implements CurriculumPlanService {

    private final CurriculumPlanEntryRepository repository;
    private final StudyPeriodSettingRepository studyPeriodSettingRepository;
    private final StudyPeriodSettingService studyPeriodSettingService;
    private final SubjectCatalogRepository subjectCatalogRepository;

    @Override
    public CurriculumPlanEntry upsert(CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPart curriculumPart = request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart();
        StudyPeriodSetting rule = resolveRule(request);
        rule = normalizeYearRuleIfNeeded(request, curriculumPart, rule);
        String normalizedClassName = ClassNameNormalizer.normalize(request.getClassName());

        CurriculumPlanEntry entity = repository
                .findByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriodAndStudyPeriodSettingId(
                        request.getAcademicYear(),
                        request.getNumberSchoolBuilding().trim(),
                        normalizedClassName,
                        request.getSubjectName().trim(),
                        request.getEducationLevel(),
                        curriculumPart,
                        rule.getStudyPeriod(),
                        rule.getId()
                )
                .orElseGet(CurriculumPlanEntry::new);

        applyValues(entity, request, curriculumPart, rule);
        return repository.save(entity);
    }

    @Override
    public List<CurriculumPlanEntry> upsertBulk(List<CurriculumPlanEntryRequest> requests) {
        List<CurriculumPlanEntry> result = new ArrayList<>();
        for (CurriculumPlanEntryRequest request : requests) {
            result.add(upsert(request));
        }
        return result;
    }

    @Override
    public List<CurriculumPlanEntry> findAll(String academicYear) {
        return repository.findAllByAcademicYear(academicYear);
    }

    @Override
    public List<CurriculumPlanEntry> findAll(String academicYear, String numberSchoolBuilding) {
        if (numberSchoolBuilding == null || numberSchoolBuilding.isBlank()) {
            return findAll(academicYear);
        }
        return repository.findAllByAcademicYearAndNumberSchoolBuildingIgnoreCase(academicYear, numberSchoolBuilding.trim());
    }

    @Override
    public void clearAll(String academicYear) {
        repository.deleteAllByAcademicYear(academicYear);
    }

    @Override
    public CurriculumPlanEntry updateById(Long id, CurriculumPlanEntryRequest request) {
        validate(request);
        CurriculumPlanEntry entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum entry not found: " + id));
        CurriculumPart curriculumPart = request.getCurriculumPart() == null ? CurriculumPart.CORE : request.getCurriculumPart();
        StudyPeriodSetting rule = resolveRule(request);
        rule = normalizeYearRuleIfNeeded(request, curriculumPart, rule);
        applyValues(entity, request, curriculumPart, rule);
        return repository.save(entity);
    }

    @Override
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Curriculum entry not found: " + id);
        }
        repository.deleteById(id);
    }

    @Override
    public Optional<CurriculumPlanEntry> findRule(String academicYear,
                                                  String numberSchoolBuilding,
                                                  String className,
                                                  String subjectName,
                                                  EducationLevel educationLevel,
                                                  StudyPeriod studyPeriod) {
        String normalizedClass = ClassNameNormalizer.normalize(className);
        String normalizedSubject = subjectName == null ? "" : subjectName.trim();
        StudyPeriod effectiveStudyPeriod = studyPeriod == null ? StudyPeriod.YEAR : studyPeriod;

        Optional<CurriculumPlanEntry> exactRule = repository.findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                academicYear,
                numberSchoolBuilding,
                normalizedClass,
                normalizedSubject,
                educationLevel,
                effectiveStudyPeriod
        );

        if (exactRule.isPresent()) {
            return exactRule;
        }

        return repository.findFirstByAcademicYearAndClassNameAndSubjectNameAndEducationLevelAndStudyPeriodAndDeprecatedFalse(
                academicYear,
                normalizedClass,
                normalizedSubject,
                educationLevel,
                effectiveStudyPeriod
        );
    }

    private StudyPeriodSetting resolveRule(CurriculumPlanEntryRequest request) {
        if (request.getStudyPeriodSettingId() != null) {
            StudyPeriodSetting byId = studyPeriodSettingRepository.findById(request.getStudyPeriodSettingId())
                    .orElseThrow(() -> new IllegalArgumentException("Период обучения не найден: " + request.getStudyPeriodSettingId()));
            Integer parallel = ClassNameNormalizer.extractParallel(request.getClassName());
            if (parallel != null && (parallel < byId.getParallelFrom() || parallel > byId.getParallelTo())) {
                throw new IllegalArgumentException("Период не подходит для выбранного класса");
            }
            return byId;
        }
        return studyPeriodSettingService.resolveRuleForClassAndPeriod(request.getAcademicYear(), request.getClassName(), request.getStudyPeriod());
    }

    private StudyPeriodSetting normalizeYearRuleIfNeeded(CurriculumPlanEntryRequest request,
                                                         CurriculumPart curriculumPart,
                                                         StudyPeriodSetting resolvedRule) {
        if (resolvedRule.getStudyPeriod() == StudyPeriod.YEAR) {
            return resolvedRule;
        }
        StudyPeriod oppositePeriod = resolvedRule.getStudyPeriod() == StudyPeriod.H1 ? StudyPeriod.H2 : StudyPeriod.H1;
        Optional<CurriculumPlanEntry> opposite = repository.findFirstByAcademicYearAndNumberSchoolBuildingAndClassNameAndSubjectNameAndEducationLevelAndCurriculumPartAndStudyPeriod(
                request.getAcademicYear(),
                request.getNumberSchoolBuilding().trim(),
                ClassNameNormalizer.normalize(request.getClassName()),
                request.getSubjectName().trim(),
                request.getEducationLevel(),
                curriculumPart,
                oppositePeriod
        );
        if (opposite.isEmpty()) {
            return resolvedRule;
        }
        if (request.getPlannedHours() == null || opposite.get().getPlannedHours() == null) {
            return resolvedRule;
        }
        if (request.getPlannedHours().compareTo(opposite.get().getPlannedHours()) != 0) {
            return resolvedRule;
        }
        repository.delete(opposite.get());
        try {
            return studyPeriodSettingService.resolveRuleForClassAndPeriod(request.getAcademicYear(), request.getClassName(), StudyPeriod.YEAR);
        } catch (Exception ignored) {
            return resolvedRule;
        }
    }

    private void applyValues(CurriculumPlanEntry entity,
                             CurriculumPlanEntryRequest request,
                             CurriculumPart curriculumPart,
                             StudyPeriodSetting rule) {
        entity.setAcademicYear(request.getAcademicYear());
        entity.setNumberSchoolBuilding(request.getNumberSchoolBuilding().trim());
        entity.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
        SubjectCatalogEntry subject = subjectCatalogRepository.findAll().stream()
                .filter(s -> s.getSubjectName().equalsIgnoreCase(request.getSubjectName().trim()))
                .findFirst()
                .orElse(null);
        entity.setSubject(subject);
        entity.setSubjectName(subject == null ? request.getSubjectName().trim() : subject.getSubjectName());
        entity.setPlannedHours(request.getPlannedHours());
        entity.setModularSystem(request.isModularSystem());
        boolean parentSubgroups = !request.isModularSystem() && request.isSubgroupRequired();
        entity.setSubgroupRequired(parentSubgroups);
        entity.setSubgroupCount(parentSubgroups ? 2 : 0);
        entity.setEducationLevel(request.getEducationLevel());
        entity.setSubgroup1Hours(parentSubgroups ? request.getSubgroup1Hours() : null);
        entity.setSubgroup1EducationLevel(parentSubgroups ? request.getSubgroup1EducationLevel() : null);
        entity.setSubgroup2Hours(parentSubgroups ? request.getSubgroup2Hours() : null);
        entity.setSubgroup2EducationLevel(parentSubgroups ? request.getSubgroup2EducationLevel() : null);
        applyModules(entity, request);
        entity.setCurriculumPart(curriculumPart);
        entity.setStudyPeriod(rule.getStudyPeriod());
        entity.setStudyPeriodSettingId(rule.getId());

        boolean explicitMetaGroupRow = entity.getClassName() != null
                && entity.getClassName().trim().toUpperCase(Locale.ROOT).startsWith("МГ:");
        if (explicitMetaGroupRow) {
            if (request.isExcludedFromManualLoad()) {
                throw new IllegalArgumentException("Строка нагрузки метагруппы должна переноситься в нагрузку");
            }
            entity.setExcludedFromManualLoad(false);
            entity.setMetaGroup(true);
        } else {
            entity.setExcludedFromManualLoad(request.isExcludedFromManualLoad());
            entity.setMetaGroup(request.isExcludedFromManualLoad());
        }
    }

    private void validate(CurriculumPlanEntryRequest request) {
        if (request.getAcademicYear() == null || request.getAcademicYear().isBlank()) {
            throw new IllegalArgumentException("academicYear is required");
        }
        if (request.getNumberSchoolBuilding() == null || request.getNumberSchoolBuilding().isBlank()) {
            throw new IllegalArgumentException("numberSchoolBuilding is required");
        }
        if (request.getClassName() == null || request.getClassName().isBlank()) {
            throw new IllegalArgumentException("className is required");
        }
        if (request.getSubjectName() == null || request.getSubjectName().isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        if (request.getPlannedHours() == null || request.getPlannedHours().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("plannedHours must be > 0");
        }
        if (request.getEducationLevel() == null) {
            throw new IllegalArgumentException("educationLevel is required");
        }
        if (request.isModularSystem()) {
            validateModules(request);
        } else if (request.isSubgroupRequired()) {
            if (request.getSubgroup1Hours() == null || request.getSubgroup1Hours() < 0) {
                throw new IllegalArgumentException("subgroup1Hours must be >= 0 when subgroupRequired=true");
            }
            if (request.getSubgroup2Hours() == null || request.getSubgroup2Hours() < 0) {
                throw new IllegalArgumentException("subgroup2Hours must be >= 0 when subgroupRequired=true");
            }
            if (request.getSubgroup1Hours() == 0 && request.getSubgroup2Hours() == 0) {
                throw new IllegalArgumentException("at least one subgroup must have hours > 0 when subgroupRequired=true");
            }
            if (request.getSubgroup1EducationLevel() == null || request.getSubgroup2EducationLevel() == null) {
                throw new IllegalArgumentException("subgroup levels are required when subgroupRequired=true");
            }
        }
    }

    private void validateModules(CurriculumPlanEntryRequest request) {
        List<CurriculumPlanEntryRequest.ModuleRequest> modules = Optional.ofNullable(request.getModules()).orElseGet(List::of);
        if (modules.size() < 2) {
            throw new IllegalArgumentException("Модульная система должна содержать не менее двух модулей");
        }
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (CurriculumPlanEntryRequest.ModuleRequest module : modules) {
            if (module.getModuleName() == null || module.getModuleName().isBlank()) {
                throw new IllegalArgumentException("Название модуля обязательно");
            }
            if (module.getPlannedHours() == null || module.getPlannedHours().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Часы модуля должны быть больше нуля");
            }
            if (module.getEducationLevel() == null) {
                throw new IllegalArgumentException("Уровень обучения модуля обязателен");
            }
            if (module.isSubgroupRequired()) {
                validateModuleSubgroups(module);
            }
            total = total.add(module.getPlannedHours());
        }
        if (total.compareTo(request.getPlannedHours()) != 0) {
            throw new IllegalArgumentException("Сумма часов модулей должна быть равна часам основного предмета");
        }
    }

    private void validateModuleSubgroups(CurriculumPlanEntryRequest.ModuleRequest module) {
        if (module.getSubgroup1Hours() == null || module.getSubgroup1Hours() < 0
                || module.getSubgroup2Hours() == null || module.getSubgroup2Hours() < 0) {
            throw new IllegalArgumentException("Часы обеих подгрупп модуля должны быть указаны");
        }
        if (module.getSubgroup1Hours() == 0 && module.getSubgroup2Hours() == 0) {
            throw new IllegalArgumentException("Хотя бы одна подгруппа модуля должна иметь часы");
        }
        if (module.getSubgroup1EducationLevel() == null || module.getSubgroup2EducationLevel() == null) {
            throw new IllegalArgumentException("Уровни обучения подгрупп модуля обязательны");
        }
        int effectiveHours = Math.max(module.getSubgroup1Hours(), module.getSubgroup2Hours());
        if (module.getPlannedHours().compareTo(java.math.BigDecimal.valueOf(effectiveHours)) != 0) {
            throw new IllegalArgumentException("Часы модуля должны совпадать с максимальным количеством часов его подгруппы");
        }
    }

    private void applyModules(CurriculumPlanEntry entity, CurriculumPlanEntryRequest request) {
        if (!request.isModularSystem()) {
            entity.getModules().clear();
            return;
        }
        Map<Long, CurriculumModule> existingById = entity.getModules().stream()
                .filter(module -> module.getId() != null)
                .collect(Collectors.toMap(CurriculumModule::getId, Function.identity()));
        List<CurriculumModule> updated = new ArrayList<>();
        List<CurriculumPlanEntryRequest.ModuleRequest> requested = request.getModules();
        for (int index = 0; index < requested.size(); index++) {
            CurriculumPlanEntryRequest.ModuleRequest source = requested.get(index);
            CurriculumModule module = source.getId() == null ? new CurriculumModule() : existingById.get(source.getId());
            if (module == null) {
                throw new IllegalArgumentException("Модуль не принадлежит редактируемому предмету: " + source.getId());
            }
            module.setCurriculumEntry(entity);
            module.setModuleOrder(index + 1);
            module.setModuleName(source.getModuleName().trim());
            module.setPlannedHours(source.getPlannedHours());
            module.setSubgroupRequired(source.isSubgroupRequired());
            module.setSubgroupCount(source.isSubgroupRequired() ? 2 : 0);
            module.setEducationLevel(source.getEducationLevel());
            module.setSubgroup1Hours(source.isSubgroupRequired() ? source.getSubgroup1Hours() : null);
            module.setSubgroup1EducationLevel(source.isSubgroupRequired() ? source.getSubgroup1EducationLevel() : null);
            module.setSubgroup2Hours(source.isSubgroupRequired() ? source.getSubgroup2Hours() : null);
            module.setSubgroup2EducationLevel(source.isSubgroupRequired() ? source.getSubgroup2EducationLevel() : null);
            updated.add(module);
        }
        entity.getModules().clear();
        entity.getModules().addAll(updated);
    }
}
