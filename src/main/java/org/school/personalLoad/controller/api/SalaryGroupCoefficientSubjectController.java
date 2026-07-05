package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.SalaryGroupCoefficientSubjectRequest;
import org.school.personalLoad.model.SalaryGroupCoefficientSubject;
import org.school.personalLoad.model.SubjectCatalogEntry;
import org.school.personalLoad.repository.SalaryGroupCoefficientSubjectRepository;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/salary-group-coefficient-subjects")
@RequiredArgsConstructor
public class SalaryGroupCoefficientSubjectController {

    private final SalaryGroupCoefficientSubjectRepository repository;
    private final SubjectCatalogRepository subjectCatalogRepository;

    @GetMapping
    public ResponseEntity<List<SalaryGroupCoefficientSubject>> findAll() {
        List<SalaryGroupCoefficientSubject> rows = repository.findAll();
        rows.sort(Comparator.comparing(SalaryGroupCoefficientSubject::getSubjectName, String.CASE_INSENSITIVE_ORDER));
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    public ResponseEntity<SalaryGroupCoefficientSubject> save(@RequestBody SalaryGroupCoefficientSubjectRequest request) {
        Long subjectId = request == null ? null : request.getSubjectId();
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId is required");
        }
        SubjectCatalogEntry subject = subjectCatalogRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("subject not found: " + subjectId));
        SalaryGroupCoefficientSubject row = repository.findBySubjectId(subject.getId())
                .orElseGet(SalaryGroupCoefficientSubject::new);
        row.setSubjectId(subject.getId());
        row.setSubjectName(normalizeSubjectName(subject.getSubjectName()));
        return ResponseEntity.ok(repository.save(row));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String normalizeSubjectName(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        return normalized;
    }
}
