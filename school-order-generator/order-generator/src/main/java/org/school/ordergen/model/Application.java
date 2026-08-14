package org.school.ordergen.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Application {
    private String eventId;
    private String studentName;
    private String classDigit;
    private String classLetter;
}