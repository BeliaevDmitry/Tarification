package org.school.ordergen.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventId;
    private String buildingAddress;
    private LocalDateTime processedAt;
    private String fileName;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String buildingAddress, String fileName) {
        this.eventId = eventId;
        this.buildingAddress = buildingAddress;
        this.fileName = fileName;
        this.processedAt = LocalDateTime.now();
    }

    // геттеры и сеттеры
    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getBuildingAddress() { return buildingAddress; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public String getFileName() { return fileName; }
    public void setId(Long id) { this.id = id; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setBuildingAddress(String buildingAddress) { this.buildingAddress = buildingAddress; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}