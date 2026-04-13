package org.school.personalLoad.repository;

import org.school.personalLoad.model.ServiceMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ServiceMemoRepository extends JpaRepository<ServiceMemo, Long> {
    List<ServiceMemo> findAllByStatusOrderByCreatedAtDesc(ServiceMemo.Status status);
    List<ServiceMemo> findAllByAcademicYearAndStatusOrderByCreatedAtDesc(String academicYear, ServiceMemo.Status status);

    List<ServiceMemo> findAllByStatusInOrderByCreatedAtDesc(Collection<ServiceMemo.Status> statuses);
    List<ServiceMemo> findAllByAcademicYearAndStatusInOrderByCreatedAtDesc(String academicYear, Collection<ServiceMemo.Status> statuses);

    List<ServiceMemo> findAllByFioTeacherInAndStatusIn(Collection<String> fioTeachers, Collection<ServiceMemo.Status> statuses);
}
