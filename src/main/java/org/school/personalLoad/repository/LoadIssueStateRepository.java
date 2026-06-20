package org.school.personalLoad.repository;

import org.school.personalLoad.model.LoadIssueState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoadIssueStateRepository extends JpaRepository<LoadIssueState, String> {
}
