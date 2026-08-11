package org.school.personalLoad.vsoko.mcko.repository;

import org.school.personalLoad.vsoko.mcko.model.MckoParticipantRosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MckoParticipantRosterRepository extends JpaRepository<MckoParticipantRosterEntry, Long> {
}
