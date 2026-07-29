package org.school.personalLoad.service;

import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.model.ContingentStudent;

import java.util.List;

public interface StudentIdentityService {

    LinkResult linkStudents(ContingentSnapshot snapshot, List<ContingentStudent> students);

    LinkResult reconcileSnapshot(Long snapshotId);

    record LinkResult(int linked, int created, int ambiguous) {
    }
}
