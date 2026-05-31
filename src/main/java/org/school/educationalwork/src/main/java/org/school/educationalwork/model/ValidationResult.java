package org.school.educationalwork.src.main.java.org.school.educationalwork.model;

import java.util.List;

public record ValidationResult<T>(T data, List<ValidationIssue> issues) {
    public ValidationResult {
        issues = List.copyOf(issues);
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
    }
}
