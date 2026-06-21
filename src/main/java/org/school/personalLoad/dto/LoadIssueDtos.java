package org.school.personalLoad.dto;

import java.util.List;

public class LoadIssueDtos {

    public record LoadIssueResponse(List<LoadIssueRow> rows, int unresolvedCount) {
    }

    public record LoadIssueRow(String key,
                               String building,
                               String type,
                               String description,
                               String comment,
                               boolean resolved,
                               String targetPage,
                               String targetClass,
                               String targetSubject) {
    }

    public record LoadIssueUpdateRequest(String key, String comment, Boolean resolved) {
    }
}
