package org.school.personalLoad.service;

import org.school.personalLoad.dto.contingent.StudentSupportDtos;

import java.time.LocalDate;

public interface StudentSupportService {

    StudentSupportDtos.SummaryResponse getSummary(String academicYear, LocalDate snapshotDate, LocalDate asOfDate);

    StudentSupportDtos.ReferenceDataResponse getReferenceData(String academicYear);

    StudentSupportDtos.RegisterRow saveStatus(String academicYear, StudentSupportDtos.StatusSaveRequest request);

    StudentSupportDtos.IupPlanView saveIup(String academicYear, StudentSupportDtos.IupSaveRequest request);

    StudentSupportDtos.IupPlanView getIup(String academicYear, Long iupPlanId);

    byte[] exportSummary(String academicYear, LocalDate snapshotDate, LocalDate asOfDate);
}
