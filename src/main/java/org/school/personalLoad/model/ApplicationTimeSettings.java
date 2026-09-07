package org.school.personalLoad.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "application_time_settings")
@Getter
@Setter
public class ApplicationTimeSettings {
    @Id
    private Long id = 1L;

    @Column(name = "offset_millis", nullable = false)
    private long offsetMillis;

    @Column(name = "zone_id", nullable = false, length = 80)
    private String zoneId = "Europe/Moscow";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
