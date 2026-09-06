package org.school.personalLoad.repository;

import org.school.personalLoad.model.ExitOrder;
import org.school.personalLoad.model.ProbeOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExitOrderRepository extends JpaRepository<ExitOrder, Long> {

    @EntityGraph(attributePaths = {"schoolBuilding", "primaryCompanion", "secondaryCompanion",
            "additionalCompanions", "signer", "participants", "participants.student"})
    List<ExitOrder> findAllByAcademicYearOrderByEventDateAscStartTimeAsc(String academicYear);

    @EntityGraph(attributePaths = {"schoolBuilding", "primaryCompanion", "secondaryCompanion",
            "additionalCompanions", "signer", "participants", "participants.student"})
    Optional<ExitOrder> findOneById(Long id);

    @EntityGraph(attributePaths = {"schoolBuilding", "primaryCompanion", "secondaryCompanion",
            "additionalCompanions", "participants", "participants.student"})
    List<ExitOrder> findAllByStatusInAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
            Collection<ProbeOrderStatus> statuses, LocalDate from, LocalDate to);

    @Query("""
            select distinct o from ExitOrder o
            join o.participants p
            where p.student.id = :studentId
              and p.absent = false
              and o.status = org.school.personalLoad.model.ProbeOrderStatus.RELEASED
              and o.eventDate <= :today
            order by o.eventDate desc, o.startTime desc
            """)
    List<ExitOrder> findAttendedByStudentId(@Param("studentId") Long studentId,
                                             @Param("today") LocalDate today);

    @Query("""
            select distinct o from ExitOrder o
            left join o.primaryCompanion primaryCompanion
            left join o.secondaryCompanion secondaryCompanion
            left join o.additionalCompanions additionalCompanion
            where o.status = org.school.personalLoad.model.ProbeOrderStatus.RELEASED
              and o.eventDate <= :today
              and (primaryCompanion.id = :teacherId or secondaryCompanion.id = :teacherId
                   or additionalCompanion.id = :teacherId)
            order by o.eventDate desc, o.startTime desc
            """)
    List<ExitOrder> findReleasedByCompanionId(@Param("teacherId") Long teacherId,
                                               @Param("today") LocalDate today);
}
