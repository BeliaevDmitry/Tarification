package org.school.personalLoad.service;

import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.model.ManualLoadEntry;

import java.util.List;

public interface ManualLoadService {
    ManualLoadEntry create(ManualLoadEntryRequest request);

    List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests);

    List<ManualLoadEntry> findAll();

    void clearAll();

    int processCurrentManualLoad();
}
