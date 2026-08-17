package org.school.ordergen.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Event {
    private String id;
    private String name;
    private String date;           // dd.MM.yyyy
    private String timeRange;      // "15:00 - 16:30"
    private String organizer;
    private String partner;
    private String address;
}