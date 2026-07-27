package org.school.personalLoad.repository;

import org.school.personalLoad.model.PedagogicalCouncilProtocol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PedagogicalCouncilProtocolRepository extends JpaRepository<PedagogicalCouncilProtocol, Long> {

    List<PedagogicalCouncilProtocol> findAllByAcademicYearOrderByMeetingDateDescCreatedAtDesc(String academicYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select protocol from PedagogicalCouncilProtocol protocol where protocol.id = :id")
    Optional<PedagogicalCouncilProtocol> findByIdForUpdate(@Param("id") Long id);
}
