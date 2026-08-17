package org.school.personalLoad.service;

import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.ProbeOrderDtos;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface ProbeOrderService {
    ProbeOrderDtos.ImportResponse importRegistration(String academicYear, MultipartFile file, SessionUser user) throws IOException;

    List<ProbeOrderDtos.OrderView> list(String academicYear, SessionUser user);

    ProbeOrderDtos.ReferenceData references(String academicYear, SessionUser user);

    ProbeOrderDtos.OrderView update(Long id, ProbeOrderDtos.EditRequest request, SessionUser user);

    ProbeOrderDtos.OrderView assignCompanions(Long id, ProbeOrderDtos.CompanionRequest request, SessionUser user);

    ProbeOrderDtos.OrderView acknowledge(Long id, SessionUser user);

    ProbeOrderDtos.OrderView generate(Long id, ProbeOrderDtos.GenerateRequest request, SessionUser user);

    ProbeOrderDtos.FilePayload generatedDocument(Long id, SessionUser user);

    ProbeOrderDtos.OrderView release(Long id, SessionUser user);

    ProbeOrderDtos.OrderView uploadScan(Long id, MultipartFile file, SessionUser user) throws IOException;

    ProbeOrderDtos.FilePayload signedScan(Long id, SessionUser user);

    List<ProbeOrderDtos.CalendarEvent> calendar(LocalDate from, LocalDate to);

    List<ProbeOrderDtos.HistoryEvent> studentHistory(Long studentId);

    List<ProbeOrderDtos.HistoryEvent> teacherHistory(Long teacherId);
}
