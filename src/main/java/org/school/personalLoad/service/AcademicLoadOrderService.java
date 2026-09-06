package org.school.personalLoad.service;

import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.AcademicLoadOrderDtos;
import org.school.personalLoad.model.AcademicLoadOrder;

import java.util.List;

public interface AcademicLoadOrderService {
    List<AcademicLoadOrderDtos.OrderView> list(String academicYear);

    AcademicLoadOrderDtos.ReadinessView readiness(String academicYear);

    AcademicLoadOrderDtos.ReferencesView references();

    AcademicLoadOrderDtos.OrderView create(AcademicLoadOrderDtos.CreateRequest request,
                                           String schoolCode,
                                           SessionUser user);

    AcademicLoadOrder document(Long id);
}
