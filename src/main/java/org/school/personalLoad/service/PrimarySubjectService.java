package org.school.personalLoad.service;

import org.school.personalLoad.dto.PrimarySubjectDtos;
import org.school.personalLoad.model.PrimarySubjectRule;

import java.util.List;
import java.util.Map;

public interface PrimarySubjectService {
    List<PrimarySubjectDtos.TeacherPrimarySubjectRow> getTeacherAssignments(String academicYear);
    PrimarySubjectDtos.DetermineResult determine(String academicYear);
    PrimarySubjectDtos.TeacherPrimarySubjectRow setManual(String academicYear, Long teacherId, String primarySubject);
    void clearAssignment(String academicYear, Long teacherId);
    List<PrimarySubjectRule> getRules();
    PrimarySubjectRule saveRule(Long id, PrimarySubjectDtos.RuleRequest request);
    void deleteRule(Long id);
    Map<Long, String> resolveForExport(String academicYear);
}
