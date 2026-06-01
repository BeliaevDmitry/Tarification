package org.school.educationalwork.dto;

import org.school.educationalwork.model.ClassTeacherReport;
import org.school.educationalwork.model.ValidationIssue;

import java.util.List;

public final class EducationalWorkDtos {
    private EducationalWorkDtos() {}

    public record UploadResponse(boolean accepted, ClassTeacherReport report, List<ValidationIssue> issues) {}
    public record ExpectedClass(String numberSchoolBuilding, String schoolClass, String classTeacherFullName) {}
    public record SubmissionRow(int number, String schoolClass, String classTeacherFullName,
                                boolean submitted, String downloadUrl) {}
    public record MatrixCell(String schoolClass, Status status) {}
    public enum Status { SUBMITTED, NOT_SUBMITTED, CLASS_NOT_EXISTS }
    public record ParallelRow(int parallel, List<MatrixCell> letters) {}
    public record BuildingSummary(String numberSchoolBuilding, List<ParallelRow> matrix) {}
    public record SchoolSummary(List<ParallelRow> matrix, List<BuildingSummary> buildingSummaries,
                                List<SubmissionRow> submissions, SchoolAggregate aggregate,
                                ReportTables tables) {}
    public record SchoolAggregate(int reportsSubmitted, int expectedReports, int studentCount,
                                  int gto, int movementFirst, int volunteers, int studentCouncil,
                                  int studentAchievements, int specialProjectRows) {}

    public record ReportTables(List<PerformanceRow> performance,
                               List<AdditionalEducationRow> additionalEducation,
                               List<ActivityRow> activity,
                               List<AcademicDebtRow> academicDebts,
                               List<StudentAchievementRow> studentAchievements,
                               List<SpecialProjectRow> specialProjects,
                               List<TeacherPortfolioRow> teacherPortfolio,
                               List<StaffRecognitionRow> staffRecognitions,
                               List<DiagnosticRow> diagnostics) {}

    public record PerformanceRow(String schoolClass, String classTeacherFullName, int studentCount,
                                 int grade5Count, int grade4And5Count, int oneGrade3Count,
                                 int grade3And4Count, int failingCount, String grade5,
                                 String grade4And5, String oneGrade3, String grade3And4, String failing) {}
    public record AdditionalEducationRow(String schoolClass, Integer insideCount, Integer insidePercent,
                                         Integer outsideCount, Integer outsidePercent, Integer noAdditionalEducationCount) {}
    public record ActivityRow(String schoolClass, Integer gto, Integer movementFirst, Integer volunteers,
                              Integer studentCouncil) {}
    public record AcademicDebtRow(String schoolClass, String studentName, String trimester1,
                                  String trimester2, String trimester3, String finalResult) {}
    public record StudentAchievementRow(String schoolClass, String level, String project, String nomination,
                                        String responsibleTeacher, String participants, String prizeWinners,
                                        String winners) {}
    public record SpecialProjectRow(String schoolClass, String project, String classTeacher,
                                    String nominationOrFormat, String students, String result) {}
    public record TeacherPortfolioRow(String schoolClass, String professionalCompetitions,
                                      String experienceSharing, String publications,
                                      String professionalDevelopment) {}
    public record StaffRecognitionRow(String schoolClass, String fullName, String category, String awards) {}
    public record DiagnosticRow(String schoolClass, String name, String result, String date, Boolean published) {}
}
