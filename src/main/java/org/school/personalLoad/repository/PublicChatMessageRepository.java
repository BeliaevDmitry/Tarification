package org.school.personalLoad.repository;

import org.school.personalLoad.model.PublicChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicChatMessageRepository extends JpaRepository<PublicChatMessage, Long> {
    List<PublicChatMessage> findTop100ByOrderByIdDesc();
}
