package org.school.ordergen.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClassTeacher {
    private String className;      // без дефиса
    private String fullName;        // полное ФИО (именительный падеж)
    private String nominative;      // именительный
    private String accusative;      // винительный
    private String dative;          // дательный
    private String teacherPhone;
    private String buildingAddress;
}