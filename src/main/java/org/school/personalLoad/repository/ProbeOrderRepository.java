package org.school.personalLoad.repository;

import org.school.personalLoad.model.ProbeOrder;
import org.school.personalLoad.model.ProbeOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProbeOrderRepository extends JpaRepository<ProbeOrder, Long> {

    @EntityGraph(attributePaths = {"schoolBuilding", "primaryCompanion", "secondaryCompanion", "signer", "participants", "participants.student"})
    List<ProbeOrder> findAllByAcademicYearOrderByEventDateAscStartTimeAsc(String academicYear);

    @EntityGraph(attributePaths = {"schoolBuilding", "primaryCompanion", "secondaryCompanion", "signer", "participants", "participants.student"})
    Optional<ProbeOrder> findByAcademicYearAndExternalEventIdAndSchoolBuilding_Id(
            String academicYear, String externalEventId, Long schoolBuildingId);

    @EntityGraph(attributePaths = {"schoolBuilding", "primaryCompanion", "secondaryCompanion", "signer", "participants", "participants.student"})
    Optional<ProbeOrder> findOneById(Long id);

    @EntityGraph(attributePaths = {"schoolBuilding", "primaryCompanion", "secondaryCompanion", "participants"})
    List<ProbeOrder> findAllByStatusAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
            ProbeOrderStatus status, LocalDate from, LocalDate to);

    @Query("""
            select distinct o from ProbeOrder o
            join o.participants p
            where p.student.id = :studentId and o.status = org.school.personalLoad.model.ProbeOrderStatus.RELEASED
            order by o.eventDate desc, o.startTime desc
            """)
    List<ProbeOrder> findReleasedByStudentId(@Param("studentId") Long studentId);

    @Query("""
            select distinct o from ProbeOrder o
            where o.status = org.school.personalLoad.model.ProbeOrderStatus.RELEASED
              and (o.primaryCompanion.id = :teacherId or o.secondaryCompanion.id = :teacherId)
            order by o.eventDate desc, o.startTime desc
            """)
    List<ProbeOrder> findReleasedByCompanionId(@Param("teacherId") Long teacherId);
}
