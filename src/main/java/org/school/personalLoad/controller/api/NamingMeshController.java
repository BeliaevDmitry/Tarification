package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.dto.NamingMeshManualUpdateRequest;
import org.school.personalLoad.dto.NamingMeshMappingResponse;
import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.service.NamingMeshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/naming-mesh")
@RequiredArgsConstructor
public class NamingMeshController {

    private final NamingMeshService namingMeshService;

    @GetMapping("/subjects")
    public ResponseEntity<List<String>> getSubjects() {
        return ResponseEntity.ok(namingMeshService.getAllUniqueSubjects());
    }

    @GetMapping("/subjects/{subjectName}/classes")
    public ResponseEntity<List<String>> getClassesForSubject(@PathVariable String subjectName) {
        return ResponseEntity.ok(namingMeshService.getClassesForSubject(subjectName));
    }

    @GetMapping("/mappings")
    public ResponseEntity<List<NamingMeshMappingResponse>> getMappings(
            @RequestParam String subjectName,
            @RequestParam(required = false) String className
    ) {
        List<NamingMesh> mappings = (className == null || className.isBlank())
                ? namingMeshService.getMappingsForSubject(subjectName)
                : namingMeshService.getMappingsForSubjectAndClass(subjectName, className);

        List<NamingMeshMappingResponse> response = mappings.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/mappings")
    public ResponseEntity<NamingMeshMappingResponse> upsertManualMapping(
            @RequestBody NamingMeshManualUpdateRequest request
    ) {
        NamingMesh updated = namingMeshService.upsertManualMapping(
                request.getSubjectName(),
                request.getClassName(),
                request.getGroupNameEducationalPlan(),
                request.getClassNameMesh(),
                request.getGroupNameMesh()
        );

        return ResponseEntity.ok(toResponse(updated));
    }

    private NamingMeshMappingResponse toResponse(NamingMesh mesh) {
        return new NamingMeshMappingResponse(
                mesh.getSubjectName(),
                mesh.getClassName(),
                mesh.getGroupNameEducationalPlan(),
                mesh.getClassNameMesh(),
                mesh.getGroupNameMesh()
        );
    }
}
