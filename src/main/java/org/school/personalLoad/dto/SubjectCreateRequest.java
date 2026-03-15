package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.SubjectType;

@Data
public class SubjectCreateRequest {
    private String subjectName;
    private SubjectType subjectType;
}
