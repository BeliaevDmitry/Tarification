package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.contingent.AdmissionDtos;
import org.school.personalLoad.model.AdmissionCandidate;
import org.school.personalLoad.model.AdmissionDecisionStatus;
import org.school.personalLoad.model.AdmissionDocumentStatus;
import org.school.personalLoad.repository.AdmissionCandidateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionCandidateRepository repository;

    @Transactional(readOnly = true)
    public AdmissionDtos.Overview overview(String academicYear) {
        return toOverview(academicYear, repository.findAllByAcademicYearOrderByProcessedAscUpdatedAtDescIdDesc(academicYear));
    }

    @Transactional
    public AdmissionDtos.Overview create(String academicYear, AdmissionDtos.SaveRequest request, String actor) {
        AdmissionCandidate candidate = new AdmissionCandidate();
        candidate.setAcademicYear(academicYear);
        candidate.setCreatedBy(displayActor(actor));
        apply(candidate, request, actor);
        repository.save(candidate);
        return overview(academicYear);
    }

    @Transactional
    public AdmissionDtos.Overview update(String academicYear, Long id, AdmissionDtos.SaveRequest request, String actor) {
        AdmissionCandidate candidate = find(academicYear, id);
        apply(candidate, request, actor);
        repository.save(candidate);
        return overview(academicYear);
    }

    @Transactional
    public AdmissionDtos.Overview action(String academicYear, Long id, AdmissionDtos.ActionRequest request, String actor) {
        AdmissionCandidate candidate = find(academicYear, id);
        String action = normalize(request == null ? null : request.getAction()).toUpperCase(Locale.ROOT);
        switch (action) {
            case "AGREE" -> candidate.setDecisionStatus(AdmissionDecisionStatus.AGREED);
            case "ENROLL" -> {
                if (normalize(candidate.getAssignedClass()).isBlank()) {
                    throw new IllegalArgumentException("Перед зачислением укажите класс зачисления");
                }
                candidate.setDecisionStatus(AdmissionDecisionStatus.ENROLLED);
                candidate.setDocumentStatus(AdmissionDocumentStatus.ENROLLMENT);
            }
            case "REFUSE" -> candidate.setDecisionStatus(AdmissionDecisionStatus.REFUSED);
            case "PROCESSED" -> candidate.setProcessed(true);
            case "REOPEN" -> candidate.setProcessed(false);
            default -> throw new IllegalArgumentException("Неизвестное действие по приёму ребёнка");
        }
        touch(candidate, actor);
        repository.save(candidate);
        return overview(academicYear);
    }

    private AdmissionCandidate find(String academicYear, Long id) {
        if (id == null) throw new IllegalArgumentException("Запись приёма не выбрана");
        return repository.findByIdAndAcademicYear(id, academicYear)
                .orElseThrow(() -> new IllegalArgumentException("Запись приёма не найдена"));
    }

    private void apply(AdmissionCandidate candidate, AdmissionDtos.SaveRequest request, String actor) {
        if (request == null) throw new IllegalArgumentException("Заполните данные ребёнка");
        String fullName = normalize(request.getFullName());
        if (fullName.isBlank()) throw new IllegalArgumentException("Укажите ФИО ребёнка");
        Integer requestedParallel = request.getRequestedParallel();
        if (requestedParallel == null || requestedParallel < 1 || requestedParallel > 11) {
            throw new IllegalArgumentException("Выберите параллель от 1 до 11");
        }
        if (request.getDocumentStatus() == null) {
            throw new IllegalArgumentException("Выберите статус документов");
        }
        candidate.setFullName(fullName);
        candidate.setRequestedParallel(requestedParallel);
        candidate.setDocumentStatus(request.getDocumentStatus());
        candidate.setProblems(normalizeMultiline(request.getProblems()));
        candidate.setComment(normalizeMultiline(request.getComment()));
        String assignedClass = normalize(request.getAssignedClass());
        if (candidate.getDecisionStatus() == AdmissionDecisionStatus.ENROLLED && assignedClass.isBlank()) {
            throw new IllegalArgumentException("У зачисленного ребёнка должен быть указан класс зачисления");
        }
        candidate.setAssignedClass(assignedClass);
        touch(candidate, actor);
    }

    private void touch(AdmissionCandidate candidate, String actor) {
        candidate.setUpdatedAt(LocalDateTime.now());
        candidate.setUpdatedBy(displayActor(actor));
    }

    private AdmissionDtos.Overview toOverview(String academicYear, List<AdmissionCandidate> candidates) {
        List<AdmissionCandidate> source = candidates == null ? List.of() : candidates;
        Map<Integer, MutableParallelStats> byParallel = new TreeMap<>();
        Map<AdmissionDecisionStatus, Integer> decisions = new EnumMap<>(AdmissionDecisionStatus.class);
        int processed = 0;
        int active = 0;
        for (AdmissionCandidate candidate : source) {
            MutableParallelStats stats = byParallel.computeIfAbsent(
                    candidate.getRequestedParallel(), ignored -> new MutableParallelStats()
            );
            stats.total++;
            if (candidate.isProcessed()) {
                processed++;
            } else {
                active++;
                stats.active++;
            }
            AdmissionDecisionStatus decision = candidate.getDecisionStatus() == null
                    ? AdmissionDecisionStatus.PENDING
                    : candidate.getDecisionStatus();
            decisions.merge(decision, 1, Integer::sum);
            switch (decision) {
                case AGREED -> stats.agreed++;
                case ENROLLED -> stats.enrolled++;
                case REFUSED -> stats.refused++;
                default -> { }
            }
        }

        List<AdmissionDtos.ParallelStats> parallelRows = new ArrayList<>();
        byParallel.forEach((parallel, sourceStats) -> {
            AdmissionDtos.ParallelStats stats = new AdmissionDtos.ParallelStats();
            stats.setRequestedParallel(parallel);
            stats.setTotal(sourceStats.total);
            stats.setActive(sourceStats.active);
            stats.setAgreed(sourceStats.agreed);
            stats.setEnrolled(sourceStats.enrolled);
            stats.setRefused(sourceStats.refused);
            parallelRows.add(stats);
        });

        AdmissionDtos.Overview overview = new AdmissionDtos.Overview();
        overview.setAcademicYear(academicYear);
        overview.setTotal(source.size());
        overview.setActive(active);
        overview.setAgreed(decisions.getOrDefault(AdmissionDecisionStatus.AGREED, 0));
        overview.setEnrolled(decisions.getOrDefault(AdmissionDecisionStatus.ENROLLED, 0));
        overview.setRefused(decisions.getOrDefault(AdmissionDecisionStatus.REFUSED, 0));
        overview.setProcessed(processed);
        overview.setParallels(parallelRows);
        overview.setCandidates(source.stream().map(this::toRow).toList());
        return overview;
    }

    private AdmissionDtos.CandidateRow toRow(AdmissionCandidate source) {
        AdmissionDtos.CandidateRow row = new AdmissionDtos.CandidateRow();
        row.setId(source.getId());
        row.setFullName(source.getFullName());
        row.setRequestedParallel(source.getRequestedParallel());
        row.setDocumentStatus(source.getDocumentStatus());
        row.setProblems(source.getProblems());
        row.setComment(source.getComment());
        row.setAssignedClass(source.getAssignedClass());
        row.setDecisionStatus(source.getDecisionStatus());
        row.setProcessed(source.isProcessed());
        row.setCreatedAt(source.getCreatedAt());
        row.setCreatedBy(source.getCreatedBy());
        row.setUpdatedAt(source.getUpdatedAt());
        row.setUpdatedBy(source.getUpdatedBy());
        return row;
    }

    private String displayActor(String value) {
        String actor = normalize(value);
        return actor.isBlank() ? "Система" : actor;
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", " ");
    }

    private String normalizeMultiline(String value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private static final class MutableParallelStats {
        private int total;
        private int active;
        private int agreed;
        private int enrolled;
        private int refused;
    }
}
