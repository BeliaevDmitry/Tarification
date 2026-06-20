package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "load_issue_state")
public class LoadIssueState {

    @Id
    @Column(name = "issue_key", length = 500)
    private String issueKey;

    @Column(name = "comment_text", length = 4000)
    private String comment;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
