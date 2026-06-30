package org.school.personalLoad.service;

import org.school.personalLoad.model.ClassSizeSource;

import java.util.List;
import java.util.Map;

public interface ClassSizeService {
    Map<String, Integer> aisClassSizes(String academicYear);

    Map<String, Integer> effectiveClassSizes(String academicYear);

    ClassSizeSource source(String academicYear);

    ClassSizeSource setSource(String academicYear, ClassSizeSource source);

    List<ClassSizeRow> manualRows(String academicYear);

    void saveManualRows(String academicYear, List<ManualClassSizeUpdate> rows);

    record ClassSizeRow(String className, Integer aisStudents, Integer manualStudents, boolean matches) {}

    record ManualClassSizeUpdate(String className, Integer manualStudents) {}
}
