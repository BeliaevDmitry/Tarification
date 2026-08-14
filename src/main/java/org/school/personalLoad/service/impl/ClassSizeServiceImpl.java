package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.model.ClassSizeSource;
import org.school.personalLoad.model.ContingentClassSizeOverride;
import org.school.personalLoad.model.ContingentClassSizeSourceSetting;
import org.school.personalLoad.model.ContingentStudent;
import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentClassSizeOverrideRepository;
import org.school.personalLoad.repository.ContingentClassSizeSourceSettingRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.service.ClassSizeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClassSizeServiceImpl implements ClassSizeService {

    private final ContingentSnapshotRepository snapshotRepository;
    private final ContingentStudentRepository studentRepository;
    private final ContingentClassSizeOverrideRepository overrideRepository;
    private final ContingentClassSizeSourceSettingRepository sourceSettingRepository;
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;

    @Override
    public Map<String, Integer> aisClassSizes(String academicYear) {
        try {
            return snapshotRepository.findFirstByAcademicYearOrderBySnapshotDateDescImportedAtDesc(academicYear)
                    .map(snapshot -> studentRepository.findAllBySnapshotId(snapshot.getId()).stream()
                            .filter(student -> isSchoolClass(student.getClassName()))
                            .collect(Collectors.groupingBy(
                                    student -> classSizeKey(student.getClassName()),
                                    LinkedHashMap::new,
                                    Collectors.summingInt(student -> 1)
                            )))
                    .orElseGet(LinkedHashMap::new);
        } catch (RuntimeException exception) {
            // Contingent is an optional correction layer. A damaged or not yet
            // migrated import must never disable the established salary logic
            // and workload exports, which use their legacy default class size.
            log.warn("Не удалось получить численность из контингента за {}. Основной расчёт продолжен без корректировки контингентом.",
                    academicYear, exception);
            return new LinkedHashMap<>();
        }
    }

    @Override
    public Map<String, Integer> effectiveClassSizes(String academicYear) {
        Map<String, Integer> ais = aisClassSizes(academicYear);
        try {
            if (source(academicYear) != ClassSizeSource.MANUAL) {
                return ais;
            }
            Map<String, Integer> effective = new LinkedHashMap<>(ais);
            overrideRepository.findAllByAcademicYear(academicYear).forEach(row -> {
                if (row.getManualStudents() != null) {
                    effective.put(classSizeKey(row.getClassName()), row.getManualStudents());
                }
            });
            return effective;
        } catch (RuntimeException exception) {
            log.warn("Не удалось применить настройки численности за {}. Основной расчёт продолжен с доступной численностью.",
                    academicYear, exception);
            return ais;
        }
    }

    @Override
    public ClassSizeSource source(String academicYear) {
        return sourceSettingRepository.findByAcademicYear(academicYear)
                .map(ContingentClassSizeSourceSetting::getSource)
                .orElse(ClassSizeSource.AIS);
    }

    @Override
    @Transactional
    public ClassSizeSource setSource(String academicYear, ClassSizeSource source) {
        ContingentClassSizeSourceSetting setting = sourceSettingRepository.findByAcademicYear(academicYear)
                .orElseGet(() -> {
                    ContingentClassSizeSourceSetting created = new ContingentClassSizeSourceSetting();
                    created.setAcademicYear(academicYear);
                    return created;
                });
        setting.setSource(source == null ? ClassSizeSource.AIS : source);
        setting.setUpdatedAt(LocalDateTime.now());
        return sourceSettingRepository.save(setting).getSource();
    }

    @Override
    public List<ClassSizeRow> manualRows(String academicYear) {
        Map<String, Integer> ais = aisClassSizes(academicYear);
        Map<String, Integer> manual = new LinkedHashMap<>();
        Map<String, String> displayByKey = new LinkedHashMap<>();
        overrideRepository.findAllByAcademicYear(academicYear).forEach(row ->
        {
            String key = classSizeKey(row.getClassName());
            manual.putIfAbsent(key, row.getManualStudents());
            displayByKey.putIfAbsent(key, normalizeClass(row.getClassName()));
        });
        ais.keySet().forEach(key -> displayByKey.putIfAbsent(key, normalizeClass(key)));
        classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .map(entry -> normalizeClass(entry.getClassName()))
                .filter(value -> !value.isBlank())
                .forEach(value -> displayByKey.putIfAbsent(classSizeKey(value), value));
        curriculumPlanEntryRepository.findAllByAcademicYear(academicYear).stream()
                .filter(entry -> !entry.isDeprecated())
                .map(CurriculumPlanEntry::getClassName)
                .map(this::normalizeClass)
                .filter(value -> !value.isBlank() && !value.startsWith("МГ:"))
                .forEach(value -> displayByKey.putIfAbsent(classSizeKey(value), value));

        TreeSet<String> classes = new TreeSet<>((left, right) -> compareClassNames(displayByKey.get(left), displayByKey.get(right)));
        classes.addAll(displayByKey.keySet());

        List<ClassSizeRow> result = new ArrayList<>();
        for (String key : classes) {
            String className = displayByKey.getOrDefault(key, normalizeClass(key));
            Integer aisStudents = ais.get(key);
            Integer manualStudents = manual.get(key);
            result.add(new ClassSizeRow(className, aisStudents, manualStudents, Objects.equals(aisStudents, manualStudents)));
        }
        return result;
    }

    @Override
    @Transactional
    public void saveManualRows(String academicYear, List<ManualClassSizeUpdate> rows) {
        if (rows == null) {
            return;
        }
        for (ManualClassSizeUpdate row : rows) {
            String className = normalizeClass(row.className());
            if (className.isBlank()) {
                continue;
            }
            String key = classSizeKey(className);
            ContingentClassSizeOverride entity = overrideRepository.findAllByAcademicYear(academicYear).stream()
                    .filter(existing -> classSizeKey(existing.getClassName()).equals(key))
                    .findFirst()
                    .or(() -> overrideRepository.findByAcademicYearAndClassName(academicYear, className))
                    .orElseGet(() -> {
                        ContingentClassSizeOverride created = new ContingentClassSizeOverride();
                        created.setAcademicYear(academicYear);
                        created.setClassName(className);
                        return created;
                    });
            Integer manualStudents = row.manualStudents();
            entity.setManualStudents(manualStudents == null || manualStudents < 0 ? null : manualStudents);
            entity.setUpdatedAt(LocalDateTime.now());
            overrideRepository.save(entity);
        }
    }

    private String normalizeClass(String className) {
        return ClassNameNormalizer.normalize(className);
    }

    private boolean isSchoolClass(String className) {
        Integer parallel = ClassNameNormalizer.extractParallel(className);
        return parallel != null && parallel >= 1 && parallel <= 11;
    }

    private String classSizeKey(String className) {
        return normalizeClass(className)
                .toLowerCase(java.util.Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", "")
                .replace('–', '-')
                .replace('—', '-');
    }

    private int compareClassNames(String first, String second) {
        Integer firstParallel = ClassNameNormalizer.extractParallel(first);
        Integer secondParallel = ClassNameNormalizer.extractParallel(second);
        if (firstParallel != null && secondParallel != null && !Objects.equals(firstParallel, secondParallel)) {
            return Integer.compare(firstParallel, secondParallel);
        }
        if (firstParallel != null) return -1;
        if (secondParallel != null) return 1;
        return String.valueOf(first).compareToIgnoreCase(String.valueOf(second));
    }
}
