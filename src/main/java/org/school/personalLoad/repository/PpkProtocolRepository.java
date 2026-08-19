package org.school.personalLoad.repository;

import org.school.personalLoad.model.PpkProtocol;
import org.school.personalLoad.model.PpkProtocolType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PpkProtocolRepository extends JpaRepository<PpkProtocol, Long> {
    List<PpkProtocol> findAllByAcademicYearOrderByMeetingDateDescSequenceNumberDesc(String academicYear);
    List<PpkProtocol> findAllByStudent_IdAndAcademicYearOrderByMeetingDateDesc(Long studentId, String academicYear);
    boolean existsByStudent_IdAndAcademicYearAndProtocolType(Long studentId, String academicYear, PpkProtocolType protocolType);

    @Query("select coalesce(max(protocol.sequenceNumber), 0) from PpkProtocol protocol")
    int maxSequenceNumber();
}
