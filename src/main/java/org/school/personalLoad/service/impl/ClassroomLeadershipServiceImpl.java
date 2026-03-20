package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.school.personalLoad.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ClassroomLeadershipServiceImpl implements ClassroomLeadershipService {

    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;
    private final SchoolBuildingRepository buildingRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Override
    public List<ClassroomLeadershipEntry> replaceAll(List<ClassroomLeadershipEntryRequest> requests) {
        List<ClassroomLeadershipEntryRequest> safeRequests = requests == null ? List.of() : requests;

        Map<String, ClassroomLeadershipEntryRequest> normalized = new LinkedHashMap<>();
        for (ClassroomLeadershipEntryRequest request : safeRequests) {
            if (request == null) continue;
            String building = normalize(request.getNumberSchoolBuilding());
            String className = ClassNameNormalizer.normalize(request.getClassName());
            String classDirection = normalize(request.getClassDirection());
            String fioTeacher = normalize(request.getFioTeacher());
            if (building.isBlank() || className.isBlank() || classDirection.isBlank() || fioTeacher.isBlank()) continue;

            if (teacherDirectoryRepository.findByFioTeacher(fioTeacher).isEmpty()) {
                throw new IllegalArgumentException("Teacher not found in directory: " + fioTeacher);
            }

            request.setClassName(className);
            normalized.put(building + "|" + className, request);
        }

        List<ClassroomLeadershipEntry> oldValue = classroomLeadershipRepository.findAll();
        classroomLeadershipRepository.deleteAll();
        List<ClassroomLeadershipEntry> toSave = new ArrayList<>();
        normalized.values().forEach((request) -> {
            ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
            entry.setNumberSchoolBuilding(normalize(request.getNumberSchoolBuilding()));
            entry.setClassName(ClassNameNormalizer.normalize(request.getClassName()));
            entry.setClassDirection(normalize(request.getClassDirection()));
            entry.setFioTeacher(normalize(request.getFioTeacher()));
            toSave.add(entry);
        });

        List<ClassroomLeadershipEntry> saved = classroomLeadershipRepository.saveAll(toSave);
        auditService.log(ActionType.UPDATE, "ClassroomLeadership", null, oldValue, saved, "Classroom leadership replaced");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomLeadershipEntry> findAll() {
        if (currentUserService.hasRole(RoleName.BUILDING_HEAD)) {
            Long userId = currentUserService.requireCurrentUser().getId();
            return buildingRepository.findByHeadUserId(userId)
                    .map(building -> classroomLeadershipRepository.findAllByNumberSchoolBuilding(building.getCode()))
                    .orElse(List.of());
        }
        return classroomLeadershipRepository.findAll();
    }

    @Override
    public void clearAll() {
        List<ClassroomLeadershipEntry> oldValue = classroomLeadershipRepository.findAll();
        classroomLeadershipRepository.deleteAll();
        auditService.log(ActionType.DELETE, "ClassroomLeadership", null, oldValue, null, "Classroom leadership cleared");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
