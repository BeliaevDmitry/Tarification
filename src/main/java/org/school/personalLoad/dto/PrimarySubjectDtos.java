package org.school.personalLoad.dto;

import org.school.personalLoad.model.PrimarySubjectAssignmentMode;
import org.school.personalLoad.model.PrimarySubjectRuleType;

import java.util.List;

public final class PrimarySubjectDtos {

    private PrimarySubjectDtos() {
    }

    public record TeacherPrimarySubjectRow(Long teacherId,
                                           String teacherFio,
                                           String primarySubject,
                                           PrimarySubjectAssignmentMode mode,
                                           List<String> loadSubjects) {
    }

    public record AssignmentRequest(String primarySubject) {
    }

    public record RuleRequest(String primarySubject,
                              PrimarySubjectRuleType ruleType,
                              String ruleValue,
                              Integer priority) {
    }

    public record DetermineResult(int processed, int assigned, int preservedManual, int unresolved) {
    }
}
