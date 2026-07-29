package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.LoadInRateDtos.*;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoadInRateService {
    private final LoadInRateRuleRepository ruleRepository;
    private final LoadInRateRuleBandRepository bandRepository;
    private final EmploymentContractRepository contractRepository;
    private final ManualLoadEntryRepository loadRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final LoadSalaryCalculationService salaryCalculationService;
    private final HrDocumentService hrDocumentService;

    @Transactional(readOnly = true)
    public List<RuleView> rules() {
        return ruleRepository.findAllByOrderByNameAsc().stream().map(this::view).toList();
    }

    @Transactional
    public RuleView saveRule(Long id, RuleRequest request) {
        String name = required(request == null ? null : request.name(), "Название правила");
        String documentLabel = required(request.documentLabel(), "Пояснение для документов");
        List<RuleBandRequest> requestedBands = Optional.ofNullable(request.bands()).orElse(List.of());
        validateBands(requestedBands);
        if ((id == null && ruleRepository.existsByNameIgnoreCase(name))
                || (id != null && ruleRepository.existsByNameIgnoreCaseAndIdNot(name, id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Правило с таким названием уже существует");
        }
        LoadInRateRule rule = id == null ? new LoadInRateRule() : ruleRepository.findById(id).orElseThrow();
        rule.setName(name);
        rule.setDocumentLabel(documentLabel);
        rule.setActive(request.active() == null || request.active());
        rule.setUpdatedAt(LocalDateTime.now());
        rule = ruleRepository.save(rule);
        bandRepository.deleteAllByRuleId(rule.getId());
        for (RuleBandRequest bandRequest : requestedBands) {
            LoadInRateRuleBand band = new LoadInRateRuleBand();
            band.setRuleId(rule.getId());
            band.setMinTotalHours(nonNegative(bandRequest.minTotalHours()));
            band.setMaxTotalHours(nullableNonNegative(bandRequest.maxTotalHours()));
            band.setSuggestedIncludedHours(nonNegative(bandRequest.suggestedIncludedHours()));
            band.setRateFraction(nullableNonNegative(bandRequest.rateFraction()));
            if (band.getMaxTotalHours() != null
                    && band.getMaxTotalHours().compareTo(band.getMinTotalHours()) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Верхняя граница диапазона не может быть меньше нижней");
            }
            bandRepository.save(band);
        }
        return view(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        if (contractRepository.existsByLoadInRateRuleId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Правило используется в трудовом договоре. Сначала выберите для договора другое правило.");
        }
        bandRepository.deleteAllByRuleId(id);
        ruleRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Overview overview(String academicYear) {
        List<EmploymentContract> eligibleContracts = contractRepository
                .findAllByActiveTrueAndLoadHoursMayBeIncludedInRateTrueOrderByTeacherIdAsc();
        Map<Long, List<EmploymentContract>> contractsByTeacher = eligibleContracts.stream()
                .collect(Collectors.groupingBy(EmploymentContract::getTeacherId, LinkedHashMap::new, Collectors.toList()));
        contractsByTeacher.values().forEach(items -> items.sort(
                Comparator.comparing(EmploymentContract::isPrimaryContract).reversed()
                        .thenComparing(EmploymentContract::getContractDate, Comparator.nullsLast(Comparator.reverseOrder()))));
        Map<Long, EmploymentContract> contractsById = eligibleContracts.stream()
                .collect(Collectors.toMap(EmploymentContract::getId, Function.identity()));
        Map<Long, LoadInRateRule> rulesById = ruleRepository.findAll().stream()
                .collect(Collectors.toMap(LoadInRateRule::getId, Function.identity()));
        Map<Long, String> teacherNames = teacherRepository.findAll().stream()
                .filter(teacher -> teacher.getId() != null)
                .collect(Collectors.toMap(TeacherDirectoryEntry::getId, TeacherDirectoryEntry::getFioTeacher,
                        (first, second) -> first));
        List<ManualLoadEntry> loadRows = loadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row -> row.getTeacherId() != null)
                .filter(row -> contractsByTeacher.containsKey(row.getTeacherId()))
                .filter(row -> salaryCalculationService.totalHours(row).signum() > 0)
                .sorted(Comparator.comparing((ManualLoadEntry row) ->
                                teacherNames.getOrDefault(row.getTeacherId(), row.getFioTeacher()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ManualLoadEntry::getSubjectName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ManualLoadEntry::getClassName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        Map<Long, LoadSalaryCalculationService.SalaryLine> salary =
                salaryCalculationService.calculate(academicYear, loadRows);
        List<AllocationRow> rows = new ArrayList<>();
        for (ManualLoadEntry row : loadRows) {
            EmploymentContract contract = resolveContract(row, contractsByTeacher, contractsById);
            if (contract == null) continue;
            LoadInRateRule rule = contract.getLoadInRateRuleId() == null
                    ? null : rulesById.get(contract.getLoadInRateRuleId());
            LoadSalaryCalculationService.SalaryLine line = salary.get(row.getId());
            String label = firstPresent(row.getInRateReason(), contract.getLoadInRateDocumentLabel(),
                    rule == null ? null : rule.getDocumentLabel(), contract.getPositionName());
            rows.add(new AllocationRow(
                    row.getId(), row.getTeacherId(),
                    teacherNames.getOrDefault(row.getTeacherId(), row.getFioTeacher()),
                    contract.getId(), contract.getContractNumber(), contract.getPositionName(),
                    rule == null ? null : rule.getId(), rule == null ? "" : rule.getName(), label,
                    row.getAcademicYear(), row.getNumberSchoolBuilding(), row.getSubjectName(), row.getClassName(),
                    Objects.toString(row.getGroupNameEducationalPlan(), ""),
                    Objects.toString(row.getStudyPeriod(), StudyPeriod.YEAR.name()),
                    row.getLoadFromDate(), row.getLoadToDate(),
                    line.totalHours(), line.includedHours(), line.paidHours(),
                    row.isInRateAllocationConfirmed(), label, line.children(),
                    line.subjectCoefficient(), line.groupCoefficient(), line.amount()
            ));
        }
        List<TeacherSummary> teachers = summaries(rows, rulesById);
        return new Overview(rows, teachers, teachers.stream().anyMatch(item -> !item.complete()));
    }

    @Transactional
    public SaveResult save(String academicYear, AllocationBatchRequest request, String username) {
        List<AllocationUpdate> updates = Optional.ofNullable(request)
                .map(AllocationBatchRequest::rows).orElse(List.of());
        Set<Long> changedTeachers = new LinkedHashSet<>();
        int updated = 0;
        for (AllocationUpdate update : updates) {
            if (update == null || update.manualLoadEntryId() == null || update.contractId() == null) continue;
            ManualLoadEntry row = loadRepository.findById(update.manualLoadEntryId()).orElseThrow();
            if (!Objects.equals(row.getAcademicYear(), academicYear)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Строка нагрузки относится к другому учебному году");
            }
            EmploymentContract contract = contractRepository.findById(update.contractId()).orElseThrow();
            if (!contract.isActive() || !contract.isLoadHoursMayBeIncludedInRate()
                    || !Objects.equals(contract.getTeacherId(), row.getTeacherId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Трудовой договор не разрешает включение часов в ставку");
            }
            BigDecimal total = salaryCalculationService.totalHours(row);
            BigDecimal included = nonNegative(update.includedHours());
            if (included.compareTo(total) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Часов внутри ставки не может быть больше общей нагрузки");
            }
            String reason = firstPresent(update.reason(), contract.getLoadInRateDocumentLabel(),
                    contractRuleLabel(contract), contract.getPositionName());
            boolean changed = !same(row.getIncludedInRateHours(), included)
                    || !Objects.equals(row.getEmploymentContractId(), contract.getId())
                    || !Objects.equals(Objects.toString(row.getInRateReason(), ""), reason)
                    || !row.isInRateAllocationConfirmed();
            row.setEmploymentContractId(contract.getId());
            row.setIncludedInRateHours(included);
            row.setInRateAllocationConfirmed(true);
            row.setInRateReason(reason);
            row.setInRateUpdatedAt(LocalDateTime.now());
            row.setInRateUpdatedBy(username);
            loadRepository.save(row);
            if (changed) {
                changedTeachers.add(row.getTeacherId());
                updated++;
            }
        }
        if (!changedTeachers.isEmpty()) {
            hrDocumentService.markAnnualLoadAgreementsChanged(academicYear, changedTeachers);
        }
        int unresolved = overview(academicYear).teachers().stream().mapToInt(TeacherSummary::unresolvedRows).sum();
        return new SaveResult(updated, unresolved, !changedTeachers.isEmpty());
    }

    @Transactional(readOnly = true)
    public int unresolvedCount(String academicYear) {
        return overview(academicYear).teachers().stream().mapToInt(TeacherSummary::unresolvedRows).sum();
    }

    private List<TeacherSummary> summaries(List<AllocationRow> rows, Map<Long, LoadInRateRule> rulesById) {
        record Key(Long teacherId, Long contractId) {
        }
        Map<Key, List<AllocationRow>> grouped = rows.stream().collect(Collectors.groupingBy(
                row -> new Key(row.teacherId(), row.contractId()), LinkedHashMap::new, Collectors.toList()));
        List<TeacherSummary> result = new ArrayList<>();
        for (List<AllocationRow> group : grouped.values()) {
            AllocationRow first = group.get(0);
            BigDecimal[] total = halfTotals(group, AllocationRow::totalHours);
            BigDecimal[] included = halfTotals(group, AllocationRow::includedHours);
            BigDecimal[] paid = halfTotals(group, AllocationRow::paidHours);
            int unresolved = (int) group.stream().filter(row -> !row.allocationConfirmed()).count();
            LoadInRateRuleBand matchedBand = matchingBand(first.ruleId(), total[0].max(total[1])).orElse(null);
            BigDecimal suggestion = matchedBand == null ? BigDecimal.ZERO
                    : matchedBand.getSuggestedIncludedHours().min(total[0].max(total[1]));
            result.add(new TeacherSummary(
                    first.teacherId(), first.fio(), first.contractId(), first.contractNumber(), first.positionName(),
                    total[0], total[1], included[0], included[1], paid[0], paid[1],
                    suggestion, matchedBand == null ? null : matchedBand.getRateFraction(),
                    unresolved == 0, unresolved
            ));
        }
        return result.stream().sorted(Comparator.comparing(TeacherSummary::fio, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private BigDecimal[] halfTotals(List<AllocationRow> rows,
                                    Function<AllocationRow, BigDecimal> valueFunction) {
        BigDecimal h1 = BigDecimal.ZERO;
        BigDecimal h2 = BigDecimal.ZERO;
        for (AllocationRow row : rows) {
            BigDecimal value = Optional.ofNullable(valueFunction.apply(row)).orElse(BigDecimal.ZERO);
            if (StudyPeriod.H1.name().equals(row.studyPeriod())) h1 = h1.add(value);
            else if (StudyPeriod.H2.name().equals(row.studyPeriod())) h2 = h2.add(value);
            else {
                h1 = h1.add(value);
                h2 = h2.add(value);
            }
        }
        return new BigDecimal[]{h1, h2};
    }

    BigDecimal suggestedIncludedHours(Long ruleId, BigDecimal totalHours) {
        return matchingBand(ruleId, totalHours)
                .map(LoadInRateRuleBand::getSuggestedIncludedHours)
                .orElse(BigDecimal.ZERO).min(totalHours);
    }

    private Optional<LoadInRateRuleBand> matchingBand(Long ruleId, BigDecimal totalHours) {
        if (ruleId == null) return Optional.empty();
        return bandRepository.findAllByRuleIdOrderByMinTotalHoursAsc(ruleId).stream()
                .filter(band -> totalHours.compareTo(band.getMinTotalHours()) >= 0)
                .filter(band -> band.getMaxTotalHours() == null || totalHours.compareTo(band.getMaxTotalHours()) <= 0)
                .findFirst();
    }

    private void validateBands(List<RuleBandRequest> bands) {
        if (bands.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Добавьте хотя бы один диапазон нагрузки");
        }
        List<RuleBandRequest> sorted = bands.stream()
                .sorted(Comparator.comparing(band -> nonNegative(band.minTotalHours())))
                .toList();
        BigDecimal previousMax = null;
        boolean first = true;
        for (RuleBandRequest band : sorted) {
            BigDecimal min = nonNegative(band.minTotalHours());
            BigDecimal max = nullableNonNegative(band.maxTotalHours());
            BigDecimal included = nonNegative(band.suggestedIncludedHours());
            if (max != null && max.compareTo(min) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Верхняя граница диапазона не может быть меньше нижней");
            }
            if (max != null && included.compareTo(max) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Максимум часов внутри ставки не может превышать верхнюю границу диапазона");
            }
            if (!first && (previousMax == null || min.compareTo(previousMax) <= 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Диапазоны нагрузки не должны пересекаться");
            }
            previousMax = max;
            first = false;
        }
    }

    private EmploymentContract resolveContract(ManualLoadEntry row,
                                               Map<Long, List<EmploymentContract>> byTeacher,
                                               Map<Long, EmploymentContract> byId) {
        EmploymentContract assigned = row.getEmploymentContractId() == null
                ? null : byId.get(row.getEmploymentContractId());
        if (assigned != null && Objects.equals(assigned.getTeacherId(), row.getTeacherId())) return assigned;
        return byTeacher.getOrDefault(row.getTeacherId(), List.of()).stream().findFirst().orElse(null);
    }

    private String contractRuleLabel(EmploymentContract contract) {
        if (contract.getLoadInRateRuleId() == null) return null;
        return ruleRepository.findById(contract.getLoadInRateRuleId())
                .map(LoadInRateRule::getDocumentLabel).orElse(null);
    }

    private RuleView view(LoadInRateRule rule) {
        List<RuleBandView> bands = bandRepository.findAllByRuleIdOrderByMinTotalHoursAsc(rule.getId()).stream()
                .map(band -> new RuleBandView(band.getId(), band.getMinTotalHours(), band.getMaxTotalHours(),
                        band.getSuggestedIncludedHours(), band.getRateFraction()))
                .toList();
        return new RuleView(rule.getId(), rule.getName(), rule.getDocumentLabel(),
                rule.isActive(), bands, rule.getUpdatedAt());
    }

    private boolean same(BigDecimal first, BigDecimal second) {
        return Optional.ofNullable(first).orElse(BigDecimal.ZERO)
                .compareTo(Optional.ofNullable(second).orElse(BigDecimal.ZERO)) == 0;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        BigDecimal normalized = Optional.ofNullable(value).orElse(BigDecimal.ZERO);
        if (normalized.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Количество часов не может быть отрицательным");
        }
        return normalized;
    }

    private BigDecimal nullableNonNegative(BigDecimal value) {
        return value == null ? null : nonNegative(value);
    }

    private String required(String value, String label) {
        String normalized = Objects.toString(value, "").trim();
        if (normalized.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " обязательно");
        return normalized;
    }

    private String firstPresent(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).findFirst().orElse("");
    }
}
