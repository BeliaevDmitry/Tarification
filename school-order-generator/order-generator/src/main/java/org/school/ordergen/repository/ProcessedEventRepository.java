package org.school.ordergen.repository;

import org.school.ordergen.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByEventIdAndBuildingAddress(String eventId, String buildingAddress);
}