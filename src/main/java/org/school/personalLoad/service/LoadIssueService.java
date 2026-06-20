package org.school.personalLoad.service;

import org.school.personalLoad.dto.LoadIssueDtos;

public interface LoadIssueService {
    LoadIssueDtos.LoadIssueResponse findIssues(String academicYear, String building);

    LoadIssueDtos.LoadIssueRow updateState(LoadIssueDtos.LoadIssueUpdateRequest request);
}
