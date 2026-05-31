package org.school.educationalwork.src.main.java.org.school.educationalwork.dto;

import org.school.educationalwork.src.main.java.org.school.educationalwork.model.ClassTeacherReport;
import org.school.educationalwork.src.main.java.org.school.educationalwork.model.ValidationIssue;

import java.util.List;

public final class EducationalWorkDtos {
    private EducationalWorkDtos() {}

    public record UploadResponse(boolean accepted, ClassTeacherReport report, List<ValidationIssue> issues) {}
    public record ExpectedClass(String schoolClass, String classTeacherFullName) {}
    public record SubmissionRow(int number, String schoolClass, String classTeacherFullName,
                                boolean submitted, String downloadUrl) {}
    public record MatrixCell(String schoolClass, Status status) {}
    public enum Status { SUBMITTED, NOT_SUBMITTED, CLASS_NOT_EXISTS }
    public record ParallelRow(int parallel, List<MatrixCell> letters) {}
    public record SchoolSummary(List<ParallelRow> matrix, List<SubmissionRow> submissions,
                                SchoolAggregate aggregate) {}
    public record SchoolAggregate(int reportsSubmitted, int expectedReports, int studentCount,
                                  int gto, int movementFirst, int volunteers, int studentCouncil,
                                  int studentAchievements, int specialProjectRows) {}
}
