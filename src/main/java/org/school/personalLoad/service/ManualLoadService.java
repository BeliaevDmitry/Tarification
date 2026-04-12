package org.school.personalLoad.service;

import org.school.personalLoad.dto.ManualLoadEntryRequest;
import org.school.personalLoad.dto.ManualLoadProcessResult;
import org.school.personalLoad.model.ManualLoadEntry;

import java.util.List;

public interface ManualLoadService {
    ManualLoadEntry create(String academicYear, ManualLoadEntryRequest request);

    List<ManualLoadEntry> createBulk(String academicYear, List<ManualLoadEntryRequest> requests);

    List<ManualLoadEntry> findAll(String academicYear);

    void clearAll(String academicYear);

    ManualLoadProcessResult processCurrentManualLoad(String academicYear);

    default ManualLoadEntry create(ManualLoadEntryRequest request) { return create(null, request); }
    default List<ManualLoadEntry> createBulk(List<ManualLoadEntryRequest> requests) { return createBulk(null, requests); }
    default List<ManualLoadEntry> findAll() { return findAll(null); }
    default void clearAll() { clearAll(null); }
    default ManualLoadProcessResult processCurrentManualLoad() { return processCurrentManualLoad(null); }
}
