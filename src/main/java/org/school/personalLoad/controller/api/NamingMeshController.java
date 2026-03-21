package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dao.NamingMeshDAO;
import org.school.personalLoad.dto.NamingMeshManualUpdateRequest;
import org.school.personalLoad.dto.NamingMeshMappingResponse;
import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.repository.SubjectCatalogRepository;
import org.school.personalLoad.service.DatabaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/naming-mesh")
@RequiredArgsConstructor
public class NamingMeshController {

    private final DatabaseService databaseService;
    private final AuditService auditService;
    private final SubjectCatalogRepository subjectCatalogRepository;
    private final NamingMeshDAO namingMeshDAO = new NamingMeshDAO();

    @GetMapping("/subjects")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getSubjects() {
        List<String> subjects = databaseService.getAllNamingMeshes().stream()
                .map(NamingMesh::getSubjectName)
                .collect(Collectors.toList());
        subjects.addAll(subjectCatalogRepository.findAll().stream()
                .map(entry -> entry.getSubjectName())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toList()));
        subjects = subjects.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/mappings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NamingMeshMappingResponse>> getMappings(@RequestParam String subjectName,
                                                                       @RequestParam(required = false) String className) {
        String normalizedSubject = normalize(subjectName);
        String normalizedClass = normalize(className);
        List<NamingMeshMappingResponse> rows = databaseService.getAllNamingMeshes().stream()
                .filter(mesh -> normalize(mesh.getSubjectName()).equalsIgnoreCase(normalizedSubject))
                .filter(mesh -> normalizedClass.isBlank() || normalize(mesh.getClassName()).equalsIgnoreCase(normalizedClass))
                .sorted(Comparator
                        .comparing((NamingMesh mesh) -> normalize(mesh.getClassName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(mesh -> normalize(mesh.getGroupNameEducationalPlan()), String.CASE_INSENSITIVE_ORDER))
                .map(mesh -> new NamingMeshMappingResponse(
                        mesh.getSubjectName(),
                        mesh.getClassName(),
                        mesh.getGroupNameEducationalPlan(),
                        mesh.getClassNameMesh(),
                        mesh.getGroupNameMesh()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(rows);
    }

    @PutMapping("/mappings")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','DEPUTY_DIRECTOR')")
    public ResponseEntity<NamingMeshMappingResponse> saveMapping(@RequestBody NamingMeshManualUpdateRequest request) {
        String subjectName = normalize(request.getSubjectName());
        String className = normalize(request.getClassName());
        String groupNameEducationalPlan = normalize(request.getGroupNameEducationalPlan());
        if (subjectName.isBlank() || className.isBlank()) {
            throw new IllegalArgumentException("subjectName and className are required");
        }

        String classNameMesh = normalize(request.getClassNameMesh());
        String groupNameMesh = normalize(request.getGroupNameMesh());
        if (classNameMesh.isBlank()) {
            classNameMesh = className;
        }
        if (groupNameMesh.isBlank()) {
            groupNameMesh = groupNameEducationalPlan;
        }

        NamingMesh oldValue = databaseService.findNamingMesh(subjectName, className, groupNameEducationalPlan).orElse(null);
        NamingMesh target = oldValue == null
                ? new NamingMesh(subjectName, className, groupNameEducationalPlan, groupNameMesh, classNameMesh)
                : oldValue;

        target.setSubjectName(subjectName);
        target.setClassName(className);
        target.setGroupNameEducationalPlan(groupNameEducationalPlan);
        target.setClassNameMesh(classNameMesh);
        target.setGroupNameMesh(groupNameMesh);

        if (oldValue == null) {
            namingMeshDAO.save(target);
        } else {
            namingMeshDAO.update(target);
        }
        databaseService.updateNamingMeshRelations();
        auditService.log(ActionType.UPDATE, "NamingMesh", null, oldValue, target, "Naming mesh mapping updated");

        return ResponseEntity.ok(new NamingMeshMappingResponse(
                target.getSubjectName(),
                target.getClassName(),
                target.getGroupNameEducationalPlan(),
                target.getClassNameMesh(),
                target.getGroupNameMesh()
        ));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
