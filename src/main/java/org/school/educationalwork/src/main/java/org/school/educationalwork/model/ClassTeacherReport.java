package org.school.educationalwork.src.main.java.org.school.educationalwork.model;

import java.util.List;

public record ClassTeacherReport(
        String academicYear,
        String schoolClass,
        String teacherFullName,
        AcademicPerformance performance,
        List<AcademicDebt> academicDebts,
        AdditionalEducation additionalEducation,
        ActivityCounters activityCounters,
        List<StudentAchievement> studentAchievements,
        List<SpecialProjectParticipation> specialProjects,
        TeacherPortfolio teacherPortfolio,
        List<StaffRecognition> staffRecognitions,
        List<Diagnostic> diagnostics
) {
    public record AcademicPerformance(
            int studentCount,
            List<String> grade5,
            List<String> grade4And5,
            List<String> oneGrade3,
            List<String> grade3And4,
            List<String> failing
    ) {}

    public record AcademicDebt(String studentName, String trimester1, String trimester2,
                               String trimester3, String finalResult) {}

    public record AdditionalEducation(Integer insideCount, Integer insidePercent,
                                      Integer outsideCount, Integer outsidePercent,
                                      Integer noAdditionalEducationCount) {}

    public record ActivityCounters(Integer gto, Integer movementFirst, Integer volunteers,
                                   Integer studentCouncil) {}

    public record StudentAchievement(String level, String project, String nomination,
                                     String responsibleTeacher, String participants,
                                     String prizeWinners, String winners) {}

    public record SpecialProjectParticipation(String project, String schoolClass,
                                              String classTeacher, String nominationOrFormat,
                                              String students, String result) {}

    public record TeacherPortfolio(String professionalCompetitions, String experienceSharing,
                                   String publications, String professionalDevelopment) {}

    public record StaffRecognition(String fullName, String category, String awards) {}

    public record Diagnostic(String name, String result, String date, Boolean published) {}
}
