package org.school.MckoReport.MckoCompleks.service.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;
import org.school.MckoReport.MckoCompleks.repository.ListStudentDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultFGDataRepository;
import org.school.MckoReport.MckoCompleks.util.DiagnosticCodeUtil;
import org.school.MckoReport.MckoCompleks.util.SubjectNormalizerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataCombinationService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ListStudentDataRepository listStudentDataRepository;
    private final StudentResultDataRepository studentResultDataRepository;
    private final StudentResultFGDataRepository studentResultFGDataRepository;

    /**
     * Получить объединенные данные по ключевым параметрам
     */
    public List<CombinedResultData> combineDataByKey(
            String school,
            String subject,
            String date,
            String className) {

        log.info("Объединение данных для школы: {}, предмет: {}, дата: {}, класс: {}",
                school, subject, date, className);

        // Получаем базовые данные студентов
        List<ListStudentData> studentList = getStudentList(school, subject, date, className);

        // Сначала ищем по точной дате. По месяцу сопоставляем только тогда,
        // когда у ключа есть единственная исходная дата.
        List<StudentResultData> resultData = getResultData(school, subject, className);
        Map<String, List<StudentResultData>> resultDataByCode = getResultDataMap(resultData);
        Map<String, List<StudentResultData>> resultDataByCodeMonth = getResultDataByMonthYearMap(resultData);
        Map<String, List<StudentResultData>> resultDataByStudentNumber = getResultDataByStudentNumberMap(resultData);
        Map<String, List<StudentResultData>> resultDataByStudentNumberMonth =
                getResultDataByStudentNumberMonthYearMap(resultData);

        // Собираем ФГ результаты в Map для быстрого поиска
        List<StudentResultFGData> fgData = getFGData(school, subject, className);
        Map<String, List<StudentResultFGData>> fgDataMap = getFGDataMap(fgData, school);
        Map<String, List<StudentResultFGData>> fgDataByMonthYearMap = getFGDataByMonthYearMap(fgData, school);

        // Объединяем данные
        List<CombinedResultData> combinedResults = new ArrayList<>();

        for (ListStudentData student : studentList) {
            combinedResults.addAll(createCombinedData(
                    student,
                    resultDataByCode,
                    resultDataByCodeMonth,
                    resultDataByStudentNumber,
                    resultDataByStudentNumberMonth,
                    fgDataMap,
                    fgDataByMonthYearMap
            ));
        }

        log.info("Объединено {} записей", combinedResults.size());
        return combinedResults;
    }

    private List<ListStudentData> getStudentList(String school, String subject, String date, String className) {
        if (className != null && !className.isEmpty()) {
            return listStudentDataRepository.findBySchoolAndClassNameAndSubjectAndDate(
                    school, className, subject, date);
        } else {
            return listStudentDataRepository.findBySchoolAndSubjectAndDate(school, subject, date);
        }
    }

    private List<StudentResultData> getResultData(String school, String subject, String className) {
        List<StudentResultData> resultData = hasText(className)
                ? studentResultDataRepository.findBySchoolAndClassName(school, className)
                : studentResultDataRepository.findBySchool(school);
        return resultData.stream()
                .filter(data -> subjectsMatch(data.getSubject(), subject))
                .collect(Collectors.toList());
    }

    private Map<String, List<StudentResultData>> getResultDataMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> DiagnosticCodeUtil.isUsable(data.getCode()))
                .collect(Collectors.groupingBy(
                        data -> generateCodeKey(
                                data.getCode(),
                                data.getSubject(),
                                data.getDate(),
                                data.getSchool(),
                                data.getClassName()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultData>> getResultDataByMonthYearMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> DiagnosticCodeUtil.isUsable(data.getCode()))
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.groupingBy(
                        data -> generateCodeMonthYearKey(
                                data.getCode(),
                                data.getSubject(),
                                data.getDate(),
                                data.getSchool(),
                                data.getClassName()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultData>> getResultDataByStudentNumberMap(
            List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> data.getStudentNumber() != null)
                .filter(data -> !DiagnosticCodeUtil.isUsable(data.getCode()))
                .collect(Collectors.groupingBy(
                        data -> generateStudentNumberKey(
                                data.getStudentNumber(),
                                data.getSubject(),
                                data.getDate(),
                                data.getSchool(),
                                data.getClassName()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultData>> getResultDataByStudentNumberMonthYearMap(
            List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> data.getStudentNumber() != null)
                .filter(data -> !DiagnosticCodeUtil.isUsable(data.getCode()))
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.groupingBy(
                        data -> generateStudentNumberMonthYearKey(
                                data.getStudentNumber(),
                                data.getSubject(),
                                data.getDate(),
                                data.getSchool(),
                                data.getClassName()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<StudentResultFGData> getFGData(String school, String subject, String className) {
        List<StudentResultFGData> allFGData = studentResultFGDataRepository.findBySchool(school);
        return allFGData.stream()
                .filter(data -> matchesCriteria(data, school, subject, className))
                .collect(Collectors.toList());
    }

    private Map<String, List<StudentResultFGData>> getFGDataMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> DiagnosticCodeUtil.isUsable(data.getCode()))
                .collect(Collectors.groupingBy(
                        data -> generateCodeKey(
                                data.getCode(),
                                data.getSubject(),
                                data.getDate(),
                                school,
                                data.getClassName()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultFGData>> getFGDataByMonthYearMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> DiagnosticCodeUtil.isUsable(data.getCode()))
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.groupingBy(
                        data -> generateCodeMonthYearKey(
                                data.getCode(),
                                data.getSubject(),
                                data.getDate(),
                                school,
                                data.getClassName()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private boolean matchesCriteria(StudentResultFGData data, String school, String subject, String className) {
        boolean matches = Objects.equals(data.getSchool(), school) && subjectsMatch(data.getSubject(), subject);

        if (className != null && !className.isEmpty()) {
            matches = matches && Objects.equals(data.getClassName(), className);
        }

        return matches;
    }

    private String generateCodeKey(String code, String subject, String date, String school, String className) {
        return String.format("%s_%s_%s_%s_%s",
                DiagnosticCodeUtil.normalize(code),
                normalizeSubjectForMatching(subject),
                date,
                school,
                className
        );
    }

    private String generateCodeMonthYearKey(
            String code,
            String subject,
            String date,
            String school,
            String className) {
        return String.format("%s_%s_%s_%s_%s",
                DiagnosticCodeUtil.normalize(code),
                normalizeSubjectForMatching(subject),
                extractMonthYear(date),
                school,
                className
        );
    }

    private String generateStudentNumberKey(
            Integer studentNumber,
            String subject,
            String date,
            String school,
            String className) {
        return String.format("%s_%s_%s_%s_%s",
                studentNumber,
                normalizeSubjectForMatching(subject),
                date,
                school,
                className
        );
    }

    private String generateStudentNumberMonthYearKey(
            Integer studentNumber,
            String subject,
            String date,
            String school,
            String className) {
        return String.format("%s_%s_%s_%s_%s",
                studentNumber,
                normalizeSubjectForMatching(subject),
                extractMonthYear(date),
                school,
                className
        );
    }

    private boolean subjectsMatch(String resultSubject, String requestedSubject) {
        return Objects.equals(normalizeSubjectForMatching(resultSubject), normalizeSubjectForMatching(requestedSubject));
    }

    private String normalizeSubjectForMatching(String subject) {
        return SubjectNormalizerUtil.normalizeForMatching(subject);
    }

    private String extractMonthYear(String date) {
        if (!hasText(date)) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
            return String.format("%02d.%d", localDate.getMonthValue(), localDate.getYear());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private List<CombinedResultData> createCombinedData(
            ListStudentData student,
            Map<String, List<StudentResultData>> resultDataByCode,
            Map<String, List<StudentResultData>> resultDataByCodeMonth,
            Map<String, List<StudentResultData>> resultDataByStudentNumber,
            Map<String, List<StudentResultData>> resultDataByStudentNumberMonth,
            Map<String, List<StudentResultFGData>> fgDataMap,
            Map<String, List<StudentResultFGData>> fgDataByMonthYearMap) {

        List<StudentResultData> resultData = findResultData(
                student,
                resultDataByCode,
                resultDataByCodeMonth,
                resultDataByStudentNumber,
                resultDataByStudentNumberMonth
        );
        List<StudentResultFGData> fgData = findFGData(student, fgDataMap, fgDataByMonthYearMap);

        if (resultData.isEmpty() && fgData.isEmpty()) {
            return Collections.singletonList(buildCombinedData(student, null, null));
        }

        List<CombinedResultData> combined = new ArrayList<>();
        if (!resultData.isEmpty()) {
            for (StudentResultData result : resultData) {
                combined.add(buildCombinedData(student, result, findMatchingFG(result, fgData)));
            }
            return combined;
        }

        for (StudentResultFGData fg : fgData) {
            combined.add(buildCombinedData(student, null, fg));
        }
        return combined;
    }

    private List<StudentResultData> findResultData(
            ListStudentData student,
            Map<String, List<StudentResultData>> resultDataByCode,
            Map<String, List<StudentResultData>> resultDataByCodeMonth,
            Map<String, List<StudentResultData>> resultDataByStudentNumber,
            Map<String, List<StudentResultData>> resultDataByStudentNumberMonth) {

        if (DiagnosticCodeUtil.isUsable(student.getCode())) {
            List<StudentResultData> exactCodeMatch = resultDataByCode.get(
                    generateCodeKey(
                            student.getCode(),
                            student.getSubject(),
                            student.getDate(),
                            student.getSchool(),
                            student.getClassName()
                    )
            );
            if (hasItems(exactCodeMatch)) {
                return requireSingleResult(exactCodeMatch, "точная дата, код", student);
            }
        }

        if (student.getStudentNumber() != null) {
            List<StudentResultData> exactNumberMatch = resultDataByStudentNumber.get(
                    generateStudentNumberKey(
                            student.getStudentNumber(),
                            student.getSubject(),
                            student.getDate(),
                            student.getSchool(),
                            student.getClassName()
                    )
            );
            exactNumberMatch = requireSingleResult(
                    exactNumberMatch,
                    "точная дата, № ученика",
                    student
            );
            if (hasItems(exactNumberMatch)) {
                return exactNumberMatch;
            }
        }

        if (DiagnosticCodeUtil.isUsable(student.getCode())) {
            List<StudentResultData> monthCodeMatch = resultDataByCodeMonth.get(
                    generateCodeMonthYearKey(
                            student.getCode(),
                            student.getSubject(),
                            student.getDate(),
                            student.getSchool(),
                            student.getClassName()
                    )
            );
            monthCodeMatch = requireSingleSourceDate(monthCodeMatch, "месяц, код", student);
            monthCodeMatch = requireSingleResult(monthCodeMatch, "месяц, код", student);
            if (hasItems(monthCodeMatch)) {
                return monthCodeMatch;
            }
        }

        if (student.getStudentNumber() != null) {
            List<StudentResultData> monthNumberMatch = resultDataByStudentNumberMonth.get(
                    generateStudentNumberMonthYearKey(
                            student.getStudentNumber(),
                            student.getSubject(),
                            student.getDate(),
                            student.getSchool(),
                            student.getClassName()
                    )
            );
            monthNumberMatch = requireSingleSourceDate(monthNumberMatch, "месяц, № ученика", student);
            monthNumberMatch = requireSingleResult(monthNumberMatch, "месяц, № ученика", student);
            if (hasItems(monthNumberMatch)) {
                return monthNumberMatch;
            }
        }

        return Collections.emptyList();
    }

    private List<StudentResultFGData> findFGData(
            ListStudentData student,
            Map<String, List<StudentResultFGData>> fgDataMap,
            Map<String, List<StudentResultFGData>> fgDataByMonthYearMap) {
        if (!DiagnosticCodeUtil.isUsable(student.getCode())) {
            return Collections.emptyList();
        }

        List<StudentResultFGData> fgData = fgDataMap.get(
                generateCodeKey(
                        student.getCode(),
                        student.getSubject(),
                        student.getDate(),
                        student.getSchool(),
                        student.getClassName()
                )
        );
        if (hasItems(fgData)) {
            return requireSingleFGResult(fgData, "точная дата, код ФГ", student);
        }

        fgData = fgDataByMonthYearMap.get(
                generateCodeMonthYearKey(
                        student.getCode(),
                        student.getSubject(),
                        student.getDate(),
                        student.getSchool(),
                        student.getClassName()
                )
        );
        fgData = requireSingleFGSourceDate(fgData, "месяц, код ФГ", student);
        fgData = requireSingleFGResult(fgData, "месяц, код ФГ", student);
        if (hasItems(fgData)) {
            return fgData;
        }

        return Collections.emptyList();
    }

    private <T> boolean hasItems(List<T> values) {
        return values != null && !values.isEmpty();
    }

    private List<StudentResultData> requireSingleSourceDate(
            List<StudentResultData> candidates,
            String matchType,
            ListStudentData student) {
        if (!hasItems(candidates)) {
            return Collections.emptyList();
        }

        Set<String> dates = candidates.stream()
                .map(StudentResultData::getDate)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (dates.size() == 1) {
            return candidates;
        }

        log.warn("Пропущено неоднозначное сопоставление ({}) для code={}, studentNumber={}, subject={}, class={}, listDate={}: resultDates={}",
                matchType,
                student.getCode(),
                student.getStudentNumber(),
                student.getSubject(),
                student.getClassName(),
                student.getDate(),
                dates);
        return Collections.emptyList();
    }

    private List<StudentResultFGData> requireSingleFGSourceDate(
            List<StudentResultFGData> candidates,
            String matchType,
            ListStudentData student) {
        if (!hasItems(candidates)) {
            return Collections.emptyList();
        }

        Set<String> dates = candidates.stream()
                .map(StudentResultFGData::getDate)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (dates.size() == 1) {
            return candidates;
        }

        log.warn("Пропущено неоднозначное сопоставление ({}) для code={}, subject={}, class={}, listDate={}: resultDates={}",
                matchType,
                student.getCode(),
                student.getSubject(),
                student.getClassName(),
                student.getDate(),
                dates);
        return Collections.emptyList();
    }

    private List<StudentResultFGData> requireSingleFGResult(
            List<StudentResultFGData> candidates,
            String matchType,
            ListStudentData student) {
        if (!hasItems(candidates)) {
            return Collections.emptyList();
        }
        if (candidates.size() == 1) {
            return candidates;
        }

        List<StudentResultFGData> uniqueCandidates = candidates.stream()
                .collect(Collectors.toMap(
                        this::buildFGPayloadKey,
                        result -> result,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
        if (uniqueCandidates.size() == 1) {
            return uniqueCandidates;
        }

        log.warn("Пропущено неоднозначное сопоставление ({}) для code={}, subject={}, class={}, listDate={}: candidates={}",
                matchType,
                student.getCode(),
                student.getSubject(),
                student.getClassName(),
                student.getDate(),
                uniqueCandidates.size());
        return Collections.emptyList();
    }

    private List<StudentResultData> requireSingleResult(
            List<StudentResultData> candidates,
            String matchType,
            ListStudentData student) {
        if (!hasItems(candidates)) {
            return Collections.emptyList();
        }
        if (candidates.size() == 1) {
            return candidates;
        }

        List<StudentResultData> uniqueCandidates = candidates.stream()
                .collect(Collectors.toMap(
                        this::buildResultPayloadKey,
                        result -> result,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
        if (uniqueCandidates.size() == 1) {
            return uniqueCandidates;
        }

        log.warn("Пропущено неоднозначное сопоставление ({}) для code={}, studentNumber={}, subject={}, class={}, listDate={}: candidates={}",
                matchType,
                student.getCode(),
                student.getStudentNumber(),
                student.getSubject(),
                student.getClassName(),
                student.getDate(),
                uniqueCandidates.size());
        return Collections.emptyList();
    }

    private String buildResultPayloadKey(StudentResultData result) {
        return String.join("|",
                String.valueOf(result.getVariant()),
                String.valueOf(result.getBall()),
                String.valueOf(result.getPercentCompleted()),
                String.valueOf(result.getMark()),
                String.valueOf(result.getTaskScores())
        );
    }

    private String buildFGPayloadKey(StudentResultFGData result) {
        return String.join("|",
                String.valueOf(result.getOverallPercent()),
                String.valueOf(result.getMasteryLevel()),
                String.valueOf(result.getSection1Percent()),
                String.valueOf(result.getSection2Percent()),
                String.valueOf(result.getSection3Percent()),
                String.valueOf(result.getClassPercent()),
                String.valueOf(result.getCityPercent())
        );
    }

    private StudentResultFGData findMatchingFG(StudentResultData resultData, List<StudentResultFGData> fgData) {
        if (resultData == null || fgData.isEmpty()) {
            return null;
        }

        for (StudentResultFGData fg : fgData) {
            if (Objects.equals(resultData.getSubject(), fg.getSubject())) {
                return fg;
            }
        }
        return fgData.get(0);
    }

    private CombinedResultData buildCombinedData(
            ListStudentData student,
            StudentResultData resultData,
            StudentResultFGData fgData) {

        String schoolYear = firstNonBlank(
                resultData != null ? resultData.getSchoolYear() : null,
                fgData != null ? fgData.getSchoolYear() : null,
                student.getSchoolYear()
        );
        String subject = firstNonBlank(
                resultData != null ? resultData.getSubject() : null,
                fgData != null ? fgData.getSubject() : null,
                student.getSubject()
        );
        String date = firstNonBlank(
                resultData != null ? resultData.getDate() : null,
                fgData != null ? fgData.getDate() : null,
                student.getDate()
        );

        return CombinedResultData.builder()
                .nameFIO(student.getNameFIO())
                .code(student.getCode())
                .className(student.getClassName())
                .subject(subject)
                .date(date)
                .school(student.getSchool())
                .schoolYear(schoolYear)

                // Данные из StudentResultData
                .parallel(resultData != null ? resultData.getParallel() : null)
                .letter(resultData != null ? resultData.getLetter() : null)
                .variant(resultData != null ? resultData.getVariant() : null)
                .taskScores(resultData != null ? resultData.getTaskScores() : null)
                .ball(resultData != null ? resultData.getBall() : null)
                .percentCompleted(resultData != null ? resultData.getPercentCompleted() : null)
                .mark(resultData != null ? resultData.getMark() : null)
                .studentNumber(resultData != null ? resultData.getStudentNumber() : student.getStudentNumber())

                // Данные из StudentResultFGData
                .overallPercent(fgData != null ? fgData.getOverallPercent() : null)
                .masteryLevel(fgData != null ? fgData.getMasteryLevel() : null)
                .section1Percent(fgData != null ? fgData.getSection1Percent() : null)
                .section2Percent(fgData != null ? fgData.getSection2Percent() : null)
                .section3Percent(fgData != null ? fgData.getSection3Percent() : null)
                .classLevel(fgData != null ? formatPercent(fgData.getClassPercent()) : null)
                .cityLevel(fgData != null ? formatPercent(fgData.getCityPercent()) : null)

                // Флаги
                .hasResultData(resultData != null)
                .hasFGData(fgData != null)
                .build();
    }

    private String formatPercent(Integer percent) {
        return percent != null ? percent + "%" : null;
    }

    /**
     * Получить уникальные комбинации для фильтрации
     */
    public Map<String, List<String>> getFilterOptions() {
        Map<String, List<String>> options = new HashMap<>();

        // Школы
        List<String> schools = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getSchool)
                .distinct()
                .collect(Collectors.toList());
        options.put("schools", schools);

        // Предметы
        List<String> subjects = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getSubject)
                .distinct()
                .collect(Collectors.toList());
        options.put("subjects", subjects);

        // Даты
        List<String> dates = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getDate)
                .distinct()
                .collect(Collectors.toList());
        options.put("dates", dates);

        List<String> schoolYears = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getSchoolYear)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        options.put("schoolYears", schoolYears);

        // Классы
        List<String> classes = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getClassName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        options.put("classes", classes);

        return options;
    }
}
