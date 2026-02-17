package org.school.personalLoad.service;

import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;

import java.util.List;

public interface ClassroomLeadershipService {
    List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests);

    List<ClassroomLeadershipEntry> findAll();

    void clearAll();
}
