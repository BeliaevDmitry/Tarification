package org.school.personalLoad.service;

import org.school.personalLoad.dto.contingent.StudentDataExchangeDtos;
import org.school.personalLoad.model.ManualLoadEntry;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;

public interface StudentDataExchangeService {

    byte[] exportPackage(String academicYear);

    StudentDataExchangeDtos.ImportResult importPackage(String academicYear, MultipartFile file);

    StudentDataExchangeDtos.ReadinessResponse readiness(String academicYear);

    StudentCountResolution resolveStudentCounts(String academicYear, Collection<ManualLoadEntry> rows);

    record StudentCountResolution(
            boolean contingentMode,
            String mode,
            Map<Long, Integer> childrenByLoadEntry
    ) {
        public Integer childrenFor(ManualLoadEntry row) {
            return row == null || row.getId() == null ? null : childrenByLoadEntry.get(row.getId());
        }
    }
}
