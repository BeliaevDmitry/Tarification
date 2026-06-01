package org.school.educationalwork.model;

public record ValidationIssue(
        String code,
        String location,
        String message,
        String expected,
        String actual,
        Severity severity
) {
    public enum Severity { ERROR, WARNING }

    public static ValidationIssue error(String code, String location, String message, String expected, String actual) {
        return new ValidationIssue(code, location, message, expected, actual, Severity.ERROR);
    }

    public static ValidationIssue warning(String code, String location, String message, String expected, String actual) {
        return new ValidationIssue(code, location, message, expected, actual, Severity.WARNING);
    }
}
