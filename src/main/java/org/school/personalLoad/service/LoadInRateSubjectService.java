package org.school.personalLoad.service;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.LoadInRateRuleSubject;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.repository.LoadInRateRuleSubjectRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoadInRateSubjectService {

    private final LoadInRateRuleSubjectRepository repository;
    private final SubjectCatalogRepository subjectRepository;

    @Transactional(readOnly = true)
    public Map<Long, List<AllowedSubject>> allowedByRuleIds(Collection<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllByRuleIdIn(ruleIds).stream()
                .filter(row -> row.getRuleId() != null && row.getSubject() != null)
                .map(row -> new RuleSubject(row.getRuleId(),
                        new AllowedSubject(row.getSubjectId(), row.getSubject().getSubjectName())))
                .collect(Collectors.groupingBy(
                        RuleSubject::ruleId,
                        LinkedHashMap::new,
                        Collectors.mapping(RuleSubject::subject, Collectors.toList())
                ));
    }

    @Transactional(readOnly = true)
    public List<AllowedSubject> allowedForRule(Long ruleId) {
        if (ruleId == null) {
            return List.of();
        }
        return repository.findAllByRuleIdOrderBySubject_SubjectNameAsc(ruleId).stream()
                .filter(row -> row.getSubject() != null)
                .map(row -> new AllowedSubject(row.getSubjectId(), row.getSubject().getSubjectName()))
                .toList();
    }

    public boolean allows(Long ruleId,
                          Long subjectId,
                          String subjectName,
                          Map<Long, List<AllowedSubject>> allowedByRule) {
        if (ruleId == null || allowedByRule == null) {
            return false;
        }
        List<AllowedSubject> allowed = allowedByRule.getOrDefault(ruleId, List.of());
        if (subjectId != null && allowed.stream().anyMatch(subject -> Objects.equals(subject.id(), subjectId))) {
            return true;
        }
        String normalizedName = normalize(subjectName);
        return !normalizedName.isBlank() && allowed.stream()
                .anyMatch(subject -> normalize(subject.name()).equals(normalizedName));
    }

    @Transactional
    public List<AllowedSubject> replace(Long ruleId, Collection<Long> requestedSubjectIds) {
        LinkedHashSet<Long> subjectIds = Optional.ofNullable(requestedSubjectIds).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (subjectIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Выберите хотя бы один предмет, который может входить в ставку");
        }
        Map<Long, SubjectCatalogEntry> subjects = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(SubjectCatalogEntry::getId, subject -> subject));
        List<Long> missing = subjectIds.stream().filter(id -> !subjects.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Предметы не найдены в справочнике: " + missing);
        }
        repository.deleteAllByRuleId(ruleId);
        List<LoadInRateRuleSubject> saved = new ArrayList<>();
        for (Long subjectId : subjectIds) {
            LoadInRateRuleSubject link = new LoadInRateRuleSubject();
            link.setRuleId(ruleId);
            link.setSubject(subjects.get(subjectId));
            saved.add(repository.save(link));
        }
        return saved.stream()
                .map(row -> new AllowedSubject(row.getSubjectId(), row.getSubject().getSubjectName()))
                .sorted(Comparator.comparing(AllowedSubject::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public void deleteForRule(Long ruleId) {
        repository.deleteAllByRuleId(ruleId);
    }

    private String normalize(String value) {
        return Objects.toString(value, "")
                .trim()
                .replace('ё', 'е')
                .replace('Ё', 'Е')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public record AllowedSubject(Long id, String name) {
    }

    private record RuleSubject(Long ruleId, AllowedSubject subject) {
    }
}
