package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.ClassroomLeadershipEntryRequest;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.TeacherDirectoryRepository;
import org.school.personalLoad.service.ClassroomLeadershipService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClassroomLeadershipServiceImpl implements ClassroomLeadershipService {

    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final TeacherDirectoryRepository teacherDirectoryRepository;

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

        return classroomLeadershipRepository.saveAll(toSave);
    }

    @Override
    public List<ClassroomLeadershipEntry> findAll() {
        return classroomLeadershipRepository.findAll();
    }

    @Override
    public void clearAll() {
        classroomLeadershipRepository.deleteAll();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
