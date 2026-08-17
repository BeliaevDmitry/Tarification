package org.school.ordergen.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Student {
    private String fullName;
    private String className;      // например "9-К"
    private String phone;
    private String parentName;
    private String parentPhone;
}