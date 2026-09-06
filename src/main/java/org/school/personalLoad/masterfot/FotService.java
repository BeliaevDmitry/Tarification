package org.school.personalLoad.masterfot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.impl.MckoServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;
import java.util.regex.*;

@Service
@RequiredArgsConstructor
public class FotService {
    private final FotParser parser;
    private final FotBatchRepository batches;
    private final FotIssueRepository issues;
    private final FotMappingRepository mappings;
    private final CurriculumPlanEntryRepository curriculum;
    private final ManualLoadEntryRepository loads;
    private final StudyPeriodSettingRepository periods;
    private final MckoServiceImpl mcko;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public FotDtos.Overview overview(String year) {
        return new FotDtos.Overview(batches.findAllByAcademicYearOrderByIdDesc(year).stream().map(this::batchRow).toList(),
                issues.findAllByAcademicYear(year).stream().sorted(Comparator.comparing(FotIssue::isArchived)
                        .thenComparing(FotIssue::getId)).map(this::issueRow).toList());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FotDtos.BatchRow upload(String year, MultipartFile file, String user) {
        return upload(year, file, user, "");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FotDtos.BatchRow upload(String year, MultipartFile file, String user, String expectedSchoolCode) {
        FotDtos.Source source = parser.parse(file, year);
        verifySchool(source.organization(), expectedSchoolCode);
        List<FotBatch> previous = batches.findAllByAcademicYearOrderByIdDesc(year);
        if (!previous.isEmpty() && source.date().isBefore(previous.get(0).getSnapshotDate()))
            throw new IllegalArgumentException("Дата файла раньше последней выгрузки. Для следующей итерации нужен файл не старше " + previous.get(0).getSnapshotDate());
        FotDtos.Comparison comparison = comparison(year, source.date()).compare(source);
        FotBatch batch = new FotBatch();
        batch.setAcademicYear(year); batch.setFilename(Objects.toString(file.getOriginalFilename(), "Тарификация.xlsx"));
        batch.setSnapshotDate(source.date()); batch.setImportedAt(LocalDateTime.now()); batch.setImportedBy(user);
        batch.setRowCount(source.rows().size()); batch.setFindingCount(comparison.findings().size());
        batch.setComparisonComplete(comparison.complete()); batch.setSourceJson(write(source)); batch.setFindingsJson(write(comparison.findings()));
        batch = batches.saveAndFlush(batch);
        Map<String, FotIssue> old = new HashMap<>();
        issues.findAllByAcademicYear(year).forEach(row -> old.put(row.getId(), row));
        Set<String> present = new HashSet<>();
        for (FotDtos.Finding finding : comparison.findings()) {
            String id = FotComparison.hash(year + "|" + finding.key());
            present.add(id);
            FotIssue issue = old.getOrDefault(id, new FotIssue());
            refresh(issue, year, finding, batch.getId(), user);
            issue.setFindingJson(write(finding));
            issues.save(issue);
        }
        // Unmapped rows can hide real differences. Never archive on an incomplete comparison.
        if (comparison.complete()) {
            for (FotIssue issue : old.values()) {
                if (!present.contains(issue.getId()) && !issue.isArchived()) {
                    issue.setArchived(true); issue.setArchivedBatchId(batch.getId());
                    issue.setUpdatedAt(LocalDateTime.now()); issue.setUpdatedBy(user);
                    issues.save(issue);
                }
            }
        }
        return batchRow(batch);
    }

    static void verifySchool(String title, String expectedSchoolCode) {
        if (expectedSchoolCode == null || !expectedSchoolCode.matches("\\d+")) return;
        Matcher matcher = Pattern.compile("школа\\s*№?\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(Objects.toString(title, ""));
        if (matcher.find() && !matcher.group(1).equals(expectedSchoolCode)) {
            throw new IllegalArgumentException("Это выгрузка школы № " + matcher.group(1) + ", а открыт сервер школы № " + expectedSchoolCode);
        }
    }

    static void refresh(FotIssue issue, String year, FotDtos.Finding finding, Long batch, String user) {
        String fingerprint = FotComparison.hash(finding.type() + "|" + finding.expected() + "|" + finding.actual());
        boolean changed = !Objects.equals(issue.getFingerprint(), fingerprint);
        if (issue.getId() == null) {
            issue.setId(FotComparison.hash(year + "|" + finding.key())); issue.setAcademicYear(year); issue.setFirstBatchId(batch);
        }
        if (changed || issue.isArchived()) issue.setStatus("MCKO_VACANCY".equals(finding.type()) ? "EXPECTED" : "OPEN");
        // A claimed fix that is still present in the next export must return to the open list.
        if ("FIXED".equals(issue.getStatus())) issue.setStatus("OPEN");
        issue.setFingerprint(fingerprint); issue.setArchived(false); issue.setArchivedBatchId(null); issue.setLastBatchId(batch);
        issue.setUpdatedAt(LocalDateTime.now()); issue.setUpdatedBy(user);
    }

    @Transactional
    public FotDtos.IssueRow decision(String year, String id, FotDtos.DecisionRequest request, String user) {
        if (request == null || !Set.of("OPEN", "EXPECTED", "FIXED").contains(Objects.toString(request.status(), "")))
            throw new IllegalArgumentException("Выберите статус нестыковки");
        if (request.comment() != null && request.comment().length() > 4000) throw new IllegalArgumentException("Комментарий должен быть не длиннее 4000 символов");
        FotIssue issue = issues.findById(id).filter(row -> year.equals(row.getAcademicYear()))
                .orElseThrow(() -> new IllegalArgumentException("Нестыковка не найдена в выбранном учебном году"));
        if (issue.isArchived()) throw new IllegalArgumentException("Нестыковка уже в архиве");
        if (issue.getVersion() != request.version()) throw new IllegalArgumentException("Нестыковка уже изменена. Обновите список");
        issue.setStatus(request.status()); issue.setComment(Objects.toString(request.comment(), ""));
        issue.setUpdatedAt(LocalDateTime.now()); issue.setUpdatedBy(user);
        return issueRow(issues.saveAndFlush(issue));
    }

    @Transactional(readOnly = true)
    public List<FotDtos.Finding> history(String year, Long id) {
        FotBatch batch = batches.findById(id).filter(b -> year.equals(b.getAcademicYear()))
                .orElseThrow(() -> new IllegalArgumentException("Выгрузка не найдена"));
        try { return json.readValue(batch.getFindingsJson(), new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Не удалось прочитать историю сверки", ex); }
    }

    @Transactional(readOnly = true)
    public FotDtos.Options options(String year) {
        List<FotBatch> history = batches.findAllByAcademicYearOrderByIdDesc(year);
        LocalDate date = history.isEmpty() ? LocalDate.of(Integer.parseInt(year.substring(0, 4)), 9, 1) : history.get(0).getSnapshotDate();
        return comparison(year, date).options(mappings.findAllByAcademicYear(year));
    }

    @Transactional
    public void saveMapping(String year, FotDtos.MappingRequest request) {
        if (request == null || !Set.of("SUBJECT", "GROUP", "TEACHER").contains(Objects.toString(request.type(), ""))
                || request.source() == null || request.source().isBlank() || request.source().length() > 2000)
            throw new IllegalArgumentException("Не указано исходное название для сопоставления");
        String id = FotComparison.hash(year + "|" + FotComparison.mappingKey(request.type(), request.source()));
        if (request.target() == null || request.target().isBlank()) { mappings.deleteById(id); return; }
        FotDtos.Options options = options(year);
        List<FotDtos.Choice> choices = switch (request.type()) { case "GROUP" -> options.groups(); case "SUBJECT" -> options.subjects(); default -> options.teachers(); };
        if (choices.stream().noneMatch(c -> c.id().equals(request.target()))) throw new IllegalArgumentException("Выбранное соответствие отсутствует в системе");
        FotMapping mapping = new FotMapping();
        mapping.setId(id); mapping.setAcademicYear(year); mapping.setType(request.type()); mapping.setSource(request.source()); mapping.setTarget(request.target());
        mappings.save(mapping);
    }

    private FotComparison comparison(String year, LocalDate date) {
        List<CurriculumPlanEntry> plan = curriculum.findAllByAcademicYear(year);
        List<ManualLoadEntry> assignments = loads.findAllByAcademicYear(year);
        if (plan.isEmpty()) throw new IllegalArgumentException("В системе нет учебного плана за " + year + ". Сверка невозможна");
        return new FotComparison(plan, assignments, mcko.eligibilityForRows(assignments, date),
                mappings.findAllByAcademicYear(year), periods.findAllByAcademicYearOrderByIdAsc(year), date);
    }
    private FotDtos.BatchRow batchRow(FotBatch batch) {
        return new FotDtos.BatchRow(batch.getId(), batch.getFilename(), batch.getSnapshotDate(), batch.getImportedAt(), batch.getImportedBy(),
                batch.getRowCount(), batch.getFindingCount(), batch.isComparisonComplete());
    }
    private FotDtos.IssueRow issueRow(FotIssue issue) {
        try {
            return new FotDtos.IssueRow(issue.getId(), json.readValue(issue.getFindingJson(), FotDtos.Finding.class), issue.getStatus(), issue.getComment(),
                    issue.isArchived(), issue.getFirstBatchId(), issue.getLastBatchId(), issue.getArchivedBatchId(), issue.getUpdatedAt(), issue.getUpdatedBy(), issue.getVersion());
        } catch (Exception ex) { throw new IllegalStateException("Не удалось прочитать нестыковку", ex); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
